package com.asd.player.ui.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asd.player.AppContainer
import com.asd.player.data.repository.ScanState
import com.asd.player.data.scanner.LocalSourceScanner
import com.asd.player.domain.model.LibraryQuery
import com.asd.player.domain.model.Playlist
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SongSource
import com.asd.player.domain.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val directoryUri: Uri? = null,
    val totalCount: Int = 0,
    val driveCount: Int = 0,
    val songs: List<Song> = emptyList(),
    val formats: List<String> = emptyList(),
    val sources: List<SongSource> = emptyList(),
    val artists: List<String> = emptyList(),
    val query: LibraryQuery = LibraryQuery(),
    val scanState: ScanState = ScanState.Idle,
    val lastScanAt: Long = 0,
    val loaded: Boolean = false,
)

class LibraryViewModel(
    private val app: Application,
    private val container: AppContainer,
) : AndroidViewModel(app) {

    private val settings = container.settings
    private val repository = container.libraryRepository
    private val filters = MutableStateFlow(LibraryQuery())

    private val query = combine(filters, settings.sortOrder) { f, sort -> f.copy(sort = sort) }

    private val catalog = combine(repository.songs, query) { all, q ->
        Triple(all, q, container.searchSongs(all, q))
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<LibraryUiState> = combine(
        catalog,
        settings.directoryUri,
        repository.scanState,
        settings.lastScanAt,
    ) { (all, q, filtered), dir, scan, lastScan ->
        LibraryUiState(
            directoryUri = dir,
            totalCount = all.size,
            driveCount = all.count { it.source == SongSource.DRIVE },
            songs = filtered,
            formats = all.mapTo(sortedSetOf()) { it.format }.toList(),
            sources = all.mapTo(sortedSetOf()) { it.source }.toList(),
            artists = all.mapTo(sortedSetOf(String.CASE_INSENSITIVE_ORDER)) { it.displayArtist }.toList(),
            query = q,
            scanState = scan,
            lastScanAt = lastScan,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    val playlists: StateFlow<List<Playlist>> = container.playlistRepository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Primo avvio dopo l'installazione o cartella appena scelta ma mai scansionata.
        settings.directoryUri.value?.let { dir ->
            if (settings.lastScanAt.value == 0L) rescan(dir)
        }
    }

    /**
     * Testo del campo CERCA_, aggiornato in modo sincrono a ogni tasto. Il campo non deve leggere
     * il testo da uiState: quello arriva in ritardo (filtraggio su un altro thread) e, scrivendo
     * veloce, riporterebbe indietro testo e cursore.
     */
    var searchText by mutableStateOf("")
        private set

    fun setText(text: String) {
        searchText = text
        filters.update { it.copy(text = text) }
    }

    fun toggleFormat(format: String) = filters.value.let {
        filters.value = it.copy(formats = if (format in it.formats) it.formats - format else it.formats + format)
    }

    fun toggleSource(source: SongSource) = filters.value.let {
        filters.value = it.copy(sources = if (source in it.sources) it.sources - source else it.sources + source)
    }

    fun setArtist(artist: String?) = filters.value.let { filters.value = it.copy(artist = artist) }

    fun clearFilters() {
        searchText = ""
        filters.value = LibraryQuery()
    }

    fun setSort(order: SortOrder) = settings.setSortOrder(order)

    fun onDirectoryChosen(uri: Uri) {
        val resolver = app.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        settings.directoryUri.value?.takeIf { it != uri }?.let { old ->
            runCatching { resolver.releasePersistableUriPermission(old, flags) }
        }
        resolver.takePersistableUriPermission(uri, flags)
        settings.setDirectory(uri)
        rescan(uri)
    }

    fun rescan(uri: Uri? = settings.directoryUri.value) {
        uri ?: return
        viewModelScope.launch {
            val scanner = LocalSourceScanner(uri, container.audioScanner, container.metadataReader)
            if (repository.rescan(scanner)) settings.setLastScanAt(System.currentTimeMillis())
        }
    }

    fun play(songs: List<Song>, index: Int) = container.playbackController.playQueue(songs, index)

    fun shuffle(songs: List<Song>) =
        container.playbackController.playQueue(songs, songs.indices.random(), shuffle = true)

    fun addToPlaylist(playlistId: Long, song: Song) = viewModelScope.launch {
        container.playlistRepository.addSongs(playlistId, listOf(song.id))
    }

    fun createPlaylistWith(name: String, song: Song) = viewModelScope.launch {
        val id = container.playlistRepository.create(name)
        container.playlistRepository.addSongs(id, listOf(song.id))
    }
}
