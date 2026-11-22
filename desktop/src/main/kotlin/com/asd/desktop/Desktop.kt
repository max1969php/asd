package com.asd.desktop

import com.asd.player.domain.model.Song
import com.asd.player.domain.usecase.BrowseFolder
import com.asd.player.domain.usecase.SearchSongs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tutto ASD Desktop collegato a mano, come AppContainer dell'app Android: impostazioni, catalogo,
 * player e ultimo ascolto. All'avvio: catalogo salvato subito, poi riscansione e ripresa dell'ascolto.
 */
class Desktop {
    val settings = SettingsStore()
    val library = Library()
    val player = Player()
    val browse = BrowseFolder(SearchSongs())
    private val memory = PlaybackMemory()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        Song.unknownArtist = Texts.UNKNOWN_ARTIST
        scope.launch {
            val folder = settings.state.value.folder
            if (folder != null) {
                library.load(folder)
                restorePlayback()
                library.rescan(folder)
            }
        }
        // Ultimo ascolto: a ogni cambio di pezzo, pausa o modo, e ogni 15 secondi mentre suona.
        scope.launch {
            player.state.map { listOf(it.index, it.playing, it.shuffle, it.repeat, it.queue.size) }
                .collect { savePlayback() }
        }
        scope.launch {
            while (true) {
                delay(15_000)
                if (player.state.value.playing) savePlayback()
            }
        }
    }

    fun chooseFolder() {
        scope.launch {
            val current = settings.state.value.folder
            val chosen = withContext(Dispatchers.IO) { FolderChooser.choose(Texts.CHOOSE_FOLDER, current) }
            if (FolderChooser.isCancelled(chosen) || chosen == current) return@launch
            settings.update { it.copy(folder = chosen, path = emptyList(), scroll = 0) }
            library.rescan(chosen!!)
        }
    }

    fun rescan() {
        val folder = settings.state.value.folder ?: return
        scope.launch { library.rescan(folder) }
    }

    private suspend fun restorePlayback() {
        val saved = memory.load() ?: return
        val byUri = library.songs.first().associateBy { it.uri }
        // I pezzi spariti si saltano; se manca quello corrente si parte dal suo posto.
        val current = saved.uris.getOrNull(saved.index)
        val songs = saved.uris.mapNotNull { byUri[it] }
        if (songs.isEmpty() || player.state.value.queue.isNotEmpty()) return
        val index = songs.indexOfFirst { it.uri == current }
        player.restore(songs, if (index >= 0) index else saved.index, if (index >= 0) saved.positionMs else 0, saved.shuffle, saved.repeat)
    }

    private fun savePlayback() {
        val s = player.state.value
        if (s.queue.isEmpty()) return
        memory.save(SavedPlayback(s.queue.map { it.uri }, s.index, player.positionMs, s.shuffle, s.repeat))
    }

    /** All'uscita: salva dove si era e chiude GStreamer. */
    fun close() {
        savePlayback()
        player.shutdown()
    }
}
