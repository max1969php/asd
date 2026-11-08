package com.asd.player.data.settings

import android.content.Context
import android.net.Uri
import com.asd.player.BuildConfig
import com.asd.player.domain.model.SortField
import com.asd.player.domain.model.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CloudFolder(val id: String, val name: String)

/** Stato di una fonte cloud (Google Drive): account collegati, predefinito e la sua cartella. */
data class CloudSettings(
    val accounts: List<String> = emptyList(),
    val defaultAccount: String? = null,
    /** Cartella scelta per l'account predefinito (ogni account ricorda la sua). */
    val folder: CloudFolder? = null,
    val lastScanAt: Long = 0,
)

/** Dove il TUBO salva l'audio scaricato: telefono, Drive o tutti e due (il predefinito). */
enum class TubeDestination { LOCAL, DRIVE, BOTH }

/**
 * Equalizzatore: acceso/spento, preset del telefono scelto (null = bande a mano), livello di ogni
 * banda in millibel e forza dei bassi (0..1000).
 */
data class EqSettings(
    val enabled: Boolean = false,
    val preset: Int? = null,
    val levels: List<Int> = emptyList(),
    val bass: Int = 0,
)

/** Poche preferenze, niente DataStore: SharedPreferences + StateFlow bastano. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("asd_settings", Context.MODE_PRIVATE)

    private val _directoryUri = MutableStateFlow(prefs.getString(KEY_DIRECTORY, null)?.let(Uri::parse))
    val directoryUri: StateFlow<Uri?> = _directoryUri.asStateFlow()

    private val _lastScanAt = MutableStateFlow(prefs.getLong(KEY_LAST_SCAN, 0L))
    val lastScanAt: StateFlow<Long> = _lastScanAt.asStateFlow()

    private val _sortOrder = MutableStateFlow(
        SortOrder(
            field = prefs.getString(KEY_SORT_FIELD, null)
                ?.let { runCatching { SortField.valueOf(it) }.getOrNull() } ?: SortField.TITLE,
            ascending = prefs.getBoolean(KEY_SORT_ASC, true),
        )
    )
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    /** Le chiavi di Drive restano quelle di prima ("drive_…"): niente da migrare. */
    val drive = CloudPrefs("drive")

    // Senza Drive (versione tubo) il TUBO salva solo nel telefono, qualunque cosa ci sia salvato.
    private val _tubeDestination = MutableStateFlow(
        if (!BuildConfig.DRIVE) TubeDestination.LOCAL
        else prefs.getString(KEY_TUBE_DESTINATION, null)
            ?.let { runCatching { TubeDestination.valueOf(it) }.getOrNull() } ?: TubeDestination.BOTH
    )
    val tubeDestination: StateFlow<TubeDestination> = _tubeDestination.asStateFlow()

    private val _normalizeVolume = MutableStateFlow(prefs.getBoolean(KEY_NORMALIZE_VOLUME, true))
    /** Volume uniforme (EBU R128 / ReplayGain 2.0): acceso di default. */
    val normalizeVolume: StateFlow<Boolean> = _normalizeVolume.asStateFlow()

    private val _skipSilence = MutableStateFlow(prefs.getBoolean(KEY_SKIP_SILENCE, true))
    /** Salta il silenzio a inizio e fine pezzo: acceso di default. */
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    fun setSkipSilence(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_SILENCE, enabled).apply()
        _skipSilence.value = enabled
    }

    private val _eq = MutableStateFlow(
        EqSettings(
            enabled = prefs.getBoolean(KEY_EQ_ENABLED, false),
            preset = prefs.getInt(KEY_EQ_PRESET, -1).takeIf { it >= 0 },
            levels = prefs.getString(KEY_EQ_LEVELS, null)?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty(),
            bass = prefs.getInt(KEY_EQ_BASS, 0),
        )
    )
    val eq: StateFlow<EqSettings> = _eq.asStateFlow()

    fun setEq(eq: EqSettings) {
        prefs.edit()
            .putBoolean(KEY_EQ_ENABLED, eq.enabled)
            .putInt(KEY_EQ_PRESET, eq.preset ?: -1)
            .putString(KEY_EQ_LEVELS, eq.levels.joinToString(","))
            .putInt(KEY_EQ_BASS, eq.bass)
            .apply()
        _eq.value = eq
    }

    fun setNormalizeVolume(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NORMALIZE_VOLUME, enabled).apply()
        _normalizeVolume.value = enabled
    }

    fun setTubeDestination(destination: TubeDestination) {
        prefs.edit().putString(KEY_TUBE_DESTINATION, destination.name).apply()
        _tubeDestination.value = destination
    }

    inner class CloudPrefs(private val prefix: String) {
        private val _state = MutableStateFlow(load())
        val state: StateFlow<CloudSettings> = _state.asStateFlow()

        fun addAccount(email: String) {
            val accounts = prefs.getStringSet(accountsKey, emptySet()).orEmpty() + email
            prefs.edit().putStringSet(accountsKey, accounts).apply()
            _state.value = load()
        }

        fun removeAccount(email: String) {
            val accounts = prefs.getStringSet(accountsKey, emptySet()).orEmpty() - email
            val editor = prefs.edit()
                .putStringSet(accountsKey, accounts)
                .remove(folderIdKey(email))
                .remove(folderNameKey(email))
            if (prefs.getString(defaultKey, null) == email) {
                editor.putString(defaultKey, accounts.sorted().firstOrNull())
            }
            editor.apply()
            _state.value = load()
        }

        fun setDefault(email: String?) {
            prefs.edit().putString(defaultKey, email).apply()
            _state.value = load()
        }

        fun setFolder(email: String, folder: CloudFolder) {
            prefs.edit().putString(folderIdKey(email), folder.id).putString(folderNameKey(email), folder.name).apply()
            _state.value = load()
        }

        fun setLastScanAt(timestamp: Long) {
            prefs.edit().putLong(lastScanKey, timestamp).apply()
            _state.value = load()
        }

        private fun load(): CloudSettings {
            val default = prefs.getString(defaultKey, null)
            return CloudSettings(
                accounts = prefs.getStringSet(accountsKey, emptySet()).orEmpty().sorted(),
                defaultAccount = default,
                folder = default?.let { email ->
                    val id = prefs.getString(folderIdKey(email), null) ?: return@let null
                    CloudFolder(id, prefs.getString(folderNameKey(email), null) ?: id)
                },
                lastScanAt = prefs.getLong(lastScanKey, 0L),
            )
        }

        private val accountsKey get() = "${prefix}_accounts"
        private val defaultKey get() = "${prefix}_default_account"
        private val lastScanKey get() = "${prefix}_last_scan_at"
        private fun folderIdKey(email: String) = "${prefix}_folder_id_$email"
        private fun folderNameKey(email: String) = "${prefix}_folder_name_$email"
    }

    fun setDirectory(uri: Uri?) {
        prefs.edit().putString(KEY_DIRECTORY, uri?.toString()).apply()
        _directoryUri.value = uri
    }

    fun setLastScanAt(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SCAN, timestamp).apply()
        _lastScanAt.value = timestamp
    }

    /** Cartella aperta in ROBA e posizione nella lista, per ritrovarle alla riapertura. */
    data class LibraryPlace(
        val source: String? = null,
        val dirs: List<String> = emptyList(),
        val index: Int = 0,
        val offset: Int = 0,
        /** CARTELLE / ARTISTI / ALBUM. */
        val view: String? = null,
    )

    val libraryPlace: LibraryPlace
        get() {
            val parts = prefs.getString(KEY_LIBRARY_PATH, null)?.split(SEPARATOR) ?: return LibraryPlace()
            return LibraryPlace(
                source = parts.first().ifEmpty { null },
                dirs = parts.drop(1),
                index = prefs.getInt(KEY_LIBRARY_INDEX, 0),
                offset = prefs.getInt(KEY_LIBRARY_OFFSET, 0),
                view = prefs.getString(KEY_LIBRARY_VIEW, null),
            )
        }

    fun setLibraryPlace(place: LibraryPlace) {
        prefs.edit()
            .putString(KEY_LIBRARY_PATH, (listOf(place.source.orEmpty()) + place.dirs).joinToString(SEPARATOR))
            .putInt(KEY_LIBRARY_INDEX, place.index)
            .putInt(KEY_LIBRARY_OFFSET, place.offset)
            .putString(KEY_LIBRARY_VIEW, place.view)
            .apply()
    }

    fun setSortOrder(order: SortOrder) {
        prefs.edit().putString(KEY_SORT_FIELD, order.field.name).putBoolean(KEY_SORT_ASC, order.ascending).apply()
        _sortOrder.value = order
    }

    private companion object {
        const val KEY_DIRECTORY = "directory_uri"
        const val KEY_LAST_SCAN = "last_scan_at"
        const val KEY_SORT_FIELD = "sort_field"
        const val KEY_SORT_ASC = "sort_asc"
        const val KEY_TUBE_DESTINATION = "tube_destination"
        const val KEY_NORMALIZE_VOLUME = "normalize_volume"
        const val KEY_SKIP_SILENCE = "skip_silence"
        const val KEY_EQ_ENABLED = "eq_enabled"
        const val KEY_EQ_PRESET = "eq_preset"
        const val KEY_EQ_LEVELS = "eq_levels"
        const val KEY_EQ_BASS = "eq_bass"
        const val KEY_LIBRARY_PATH = "library_path"
        const val KEY_LIBRARY_INDEX = "library_index"
        const val KEY_LIBRARY_OFFSET = "library_offset"
        const val KEY_LIBRARY_VIEW = "library_view"
        /** Nei nomi di cartella (anche di Drive) non c'è. */
        const val SEPARATOR = "\u001F"
    }
}
