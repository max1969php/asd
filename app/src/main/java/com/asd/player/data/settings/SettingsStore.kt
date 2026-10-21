package com.asd.player.data.settings

import android.content.Context
import android.net.Uri
import com.asd.player.domain.model.SortField
import com.asd.player.domain.model.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CloudFolder(val id: String, val name: String)

/** Stato di una fonte cloud (Drive o Dropbox): account collegati, predefinito e la sua cartella. */
data class CloudSettings(
    val accounts: List<String> = emptyList(),
    val defaultAccount: String? = null,
    /** Cartella scelta per l'account predefinito (ogni account ricorda la sua). */
    val folder: CloudFolder? = null,
    val lastScanAt: Long = 0,
)

/** Dove il TUBO salva l'audio scaricato. */
enum class TubeDestination { LOCAL, DRIVE, DROPBOX }

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
    val dropbox = CloudPrefs("dropbox")

    private val _tubeDestination = MutableStateFlow(
        prefs.getString(KEY_TUBE_DESTINATION, null)
            ?.let { runCatching { TubeDestination.valueOf(it) }.getOrNull() } ?: TubeDestination.DRIVE
    )
    val tubeDestination: StateFlow<TubeDestination> = _tubeDestination.asStateFlow()

    private val _normalizeVolume = MutableStateFlow(prefs.getBoolean(KEY_NORMALIZE_VOLUME, true))
    /** Volume uniforme (EBU R128 / ReplayGain 2.0): acceso di default. */
    val normalizeVolume: StateFlow<Boolean> = _normalizeVolume.asStateFlow()

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
    }
}
