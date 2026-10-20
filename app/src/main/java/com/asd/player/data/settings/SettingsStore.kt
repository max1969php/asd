package com.asd.player.data.settings

import android.content.Context
import android.net.Uri
import com.asd.player.domain.model.SortField
import com.asd.player.domain.model.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    }
}
