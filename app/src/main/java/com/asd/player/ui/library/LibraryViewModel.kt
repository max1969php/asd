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
import com.asd.player.L10n
import com.asd.player.R
import com.asd.player.data.loudness.LoudnessProgress
import com.asd.player.data.repository.ScanState
import com.asd.player.data.scanner.LocalSourceScanner
import com.asd.player.data.settings.SettingsStore
import com.asd.player.domain.model.LibraryQuery
import com.asd.player.domain.model.Playlist
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SongSource
import com.asd.player.domain.model.SortOrder
import com.asd.player.domain.usecase.FolderEntry
import com.asd.player.domain.usecase.FolderPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val directoryUri: Uri? = null,
    val totalCount: Int = 0,
    val countBySource: Map<SongSource, Int> = emptyMap(),
    /** I file della cartella corrente (o i risultati di ricerca e filtri). */
    val songs: List<Song> = emptyList(),
    val folders: List<FolderEntry> = emptyList(),
    val path: FolderPath = FolderPath(),
    /** Tutto quello che c'è da qui in giù: lo suonano "suona tutto" e "shuffle". */
    val below: List<Song> = emptyList(),
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

    private val savedPlace = settings.libraryPlace

    /** La cartella aperta in ROBA, ritrovata alla riapertura. Cambiarla non tocca i mixtape. */
    private val path = MutableStateFlow(
        FolderPath(savedPlace.source?.let { runCatching { SongSource.valueOf(it) }.getOrNull() }, savedPlace.dirs)
    )

    /** Posizione nella lista per ogni cartella visitata: risalendo si ritrova il punto. */
    private val scrolls = mutableMapOf(path.value to (savedPlace.index to savedPlace.offset))

    fun scrollFor(folder: FolderPath): Pair<Int, Int> = scrolls[folder] ?: (0 to 0)

    fun saveScroll(folder: FolderPath, index: Int, offset: Int) {
        scrolls[folder] = index to offset
        settings.setLibraryPlace(SettingsStore.LibraryPlace(folder.source?.name, folder.dirs, index, offset))
    }

    private val query = combine(filters, settings.sortOrder) { f, sort -> f.copy(sort = sort) }

    private val catalog = combine(repository.songs, query, path) { all, q, p ->
        Triple(all, q, container.browseFolder(all, p, q))
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<LibraryUiState> = combine(
        catalog,
        settings.directoryUri,
        repository.scanState,
        settings.lastScanAt,
    ) { (all, q, view), dir, scan, lastScan ->
        LibraryUiState(
            directoryUri = dir,
            totalCount = all.size,
            countBySource = all.groupingBy { it.source }.eachCount(),
            songs = view.songs,
            folders = view.folders,
            path = view.path,
            below = view.below,
            // Anche i valori selezionati che non esistono più restano visibili come chip:
            // altrimenti il filtro resterebbe attivo senza modo di toglierlo.
            formats = (all.mapTo(sortedSetOf()) { it.format } + q.formats).sorted(),
            sources = (all.mapTo(sortedSetOf()) { it.source } + q.sources).sorted(),
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
            if (settings.lastScanAt.value == 0L) {
                rescan(dir)
            } else {
                // Brani indicizzati da una versione che non ricordava le cartelle: una riscansione le trova.
                viewModelScope.launch { if (repository.needsFolders(SongSource.LOCAL)) rescan(dir) }
            }
        }
    }

    fun openFolder(folder: FolderEntry) = path.update {
        val shown = uiState.value.path
        if (folder.source != null) FolderPath(folder.source) else shown.copy(dirs = shown.dirs + folder.name)
    }

    fun goUp() = path.update { uiState.value.path.up() }

    /** Dal percorso in alto: [depth] cartelle sotto la radice della fonte (0 = la radice). */
    fun goTo(depth: Int) = path.update { uiState.value.path.let { it.copy(dirs = it.dirs.take(depth)) } }

    fun goToRoot() = path.update { FolderPath() }

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

    fun toggleFormat(format: String) = filters.update {
        it.copy(formats = if (format in it.formats) it.formats - format else it.formats + format)
    }

    fun toggleSource(source: SongSource) = filters.update {
        it.copy(sources = if (source in it.sources) it.sources - source else it.sources + source)
    }

    fun setArtist(artist: String?) = filters.update { it.copy(artist = artist) }

    fun clearFilters() {
        searchText = ""
        filters.value = LibraryQuery()
    }

    fun setSort(order: SortOrder) = settings.setSortOrder(order)

    val normalizeVolume: StateFlow<Boolean> = settings.normalizeVolume
    val loudnessProgress: StateFlow<LoudnessProgress> = container.loudnessQueue.progress

    fun setNormalizeVolume(enabled: Boolean) = settings.setNormalizeVolume(enabled)

    fun onDirectoryChosen(uri: Uri) {
        val resolver = app.contentResolver
        // Anche scrittura: il TUBO può salvare l'audio scaricato in questa cartella.
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val changed = settings.directoryUri.value.let { it != null && it != uri }
        settings.directoryUri.value?.takeIf { it != uri }?.let { old ->
            // Le cartelle scelte con le versioni precedenti hanno solo la lettura: si rilasciano separatamente.
            runCatching { resolver.releasePersistableUriPermission(old, read or write) }
                .onFailure { runCatching { resolver.releasePersistableUriPermission(old, read) } }
        }
        resolver.takePersistableUriPermission(uri, read or write)
        settings.setDirectory(uri)
        path.value = FolderPath()
        scrolls.clear()
        viewModelScope.launch {
            // Cartella nuova: i brani della vecchia escono dai mixtape, e i mixtape rimasti vuoti spariscono.
            if (scan(uri) && changed) container.playlistRepository.deleteEmpty()
        }
    }

    fun rescan(uri: Uri? = settings.directoryUri.value) {
        uri ?: return
        viewModelScope.launch { scan(uri) }
    }

    private suspend fun scan(uri: Uri): Boolean {
        val scanner = LocalSourceScanner(uri, container.audioScanner, container.metadataReader)
        return repository.rescan(scanner).also { if (it) settings.setLastScanAt(System.currentTimeMillis()) }
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Copia sul telefono un brano di Drive (lo scarica la coda del TUBO). */
    fun downloadToPhone(song: Song) {
        _messages.tryEmit(
            if (container.tubeImporter.downloadToPhone(song)) L10n.str(R.string.queued_find_in_loot, song.title)
            else L10n.str(R.string.need_writable_folder)
        )
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
