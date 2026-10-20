package com.asd.player.data.settings

import android.content.Context
import android.net.Uri
import com.asd.player.domain.model.SortField
import com.asd.player.domain.model.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DriveFolder(val id: String, val name: String)

data class DriveSettings(
    val accounts: List<String> = emptyList(),
    val defaultAccount: String? = null,
    /** Cartella scelta per l'account predefinito (ogni account ricorda la sua). */
    val folder: DriveFolder? = null,
    val lastScanAt: Long = 0,
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

    private val _drive = MutableStateFlow(loadDrive())
    val drive: StateFlow<DriveSettings> = _drive.asStateFlow()

    fun addDriveAccount(email: String) {
        val accounts = prefs.getStringSet(KEY_DRIVE_ACCOUNTS, emptySet()).orEmpty() + email
        prefs.edit().putStringSet(KEY_DRIVE_ACCOUNTS, accounts).apply()
        _drive.value = loadDrive()
    }

    fun removeDriveAccount(email: String) {
        val accounts = prefs.getStringSet(KEY_DRIVE_ACCOUNTS, emptySet()).orEmpty() - email
        val editor = prefs.edit()
            .putStringSet(KEY_DRIVE_ACCOUNTS, accounts)
            .remove(folderIdKey(email))
            .remove(folderNameKey(email))
        if (prefs.getString(KEY_DRIVE_DEFAULT, null) == email) {
            editor.putString(KEY_DRIVE_DEFAULT, accounts.sorted().firstOrNull())
        }
        editor.apply()
        _drive.value = loadDrive()
    }

    fun setDefaultDriveAccount(email: String?) {
        prefs.edit().putString(KEY_DRIVE_DEFAULT, email).apply()
        _drive.value = loadDrive()
    }

    fun setDriveFolder(email: String, folder: DriveFolder) {
        prefs.edit().putString(folderIdKey(email), folder.id).putString(folderNameKey(email), folder.name).apply()
        _drive.value = loadDrive()
    }

    fun setDriveLastScanAt(timestamp: Long) {
        prefs.edit().putLong(KEY_DRIVE_LAST_SCAN, timestamp).apply()
        _drive.value = loadDrive()
    }

    private fun loadDrive(): DriveSettings {
        val default = prefs.getString(KEY_DRIVE_DEFAULT, null)
        return DriveSettings(
            accounts = prefs.getStringSet(KEY_DRIVE_ACCOUNTS, emptySet()).orEmpty().sorted(),
            defaultAccount = default,
            folder = default?.let { email ->
                val id = prefs.getString(folderIdKey(email), null) ?: return@let null
                DriveFolder(id, prefs.getString(folderNameKey(email), null) ?: id)
            },
            lastScanAt = prefs.getLong(KEY_DRIVE_LAST_SCAN, 0L),
        )
    }

    private fun folderIdKey(email: String) = "drive_folder_id_$email"
    private fun folderNameKey(email: String) = "drive_folder_name_$email"

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
        const val KEY_DRIVE_ACCOUNTS = "drive_accounts"
        const val KEY_DRIVE_DEFAULT = "drive_default_account"
        const val KEY_DRIVE_LAST_SCAN = "drive_last_scan_at"
    }
}
