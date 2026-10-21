package com.asd.player.data.repository

import com.asd.player.data.scanner.AudioMetadata
import com.asd.player.data.scanner.ScannedFile
import com.asd.player.data.scanner.SourceScanner
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SongSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val source: SongSource, val processed: Int, val total: Int) : ScanState
    data class Done(val source: SongSource, val added: Int, val updated: Int, val removed: Int) : ScanState
    data class Failed(val source: SongSource, val reason: String) : ScanState
}

interface MusicLibraryRepository {
    val songs: Flow<List<Song>>
    val scanState: StateFlow<ScanState>

    /** Sincronizza i brani di una fonte con il suo contenuto attuale. Le altre fonti non vengono toccate. */
    suspend fun rescan(scanner: SourceScanner): Boolean

    /** Aggiunge un singolo brano appena comparso in una fonte (es. caricato su Drive dal Tubo). */
    suspend fun add(file: ScannedFile, metadata: AudioMetadata, source: SongSource)

    /** Brani di cui non si conosce ancora il volume percepito. */
    suspend fun unmeasured(): List<Song>

    /** [lufs] null = file illeggibile, da non riprovare. */
    suspend fun setLoudness(songId: Long, lufs: Double?, peakDb: Double?)

    /** Toglie dal catalogo tutti i brani di una fonte (es. account Drive scollegato). */
    suspend fun clearSource(source: SongSource)
}
