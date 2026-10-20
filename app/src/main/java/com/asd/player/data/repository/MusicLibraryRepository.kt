package com.asd.player.data.repository

import android.net.Uri
import com.asd.player.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val processed: Int, val total: Int) : ScanState
    data class Done(val added: Int, val updated: Int, val removed: Int) : ScanState
    data class Failed(val reason: String) : ScanState
}

interface MusicLibraryRepository {
    val songs: Flow<List<Song>>
    val scanState: StateFlow<ScanState>

    /** Sincronizza il catalogo con il contenuto attuale della cartella. */
    suspend fun rescan(treeUri: Uri)
}
