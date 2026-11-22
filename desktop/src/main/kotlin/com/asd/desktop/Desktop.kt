package com.asd.desktop

import com.asd.player.data.loudness.VolumeLeveling
import com.asd.player.domain.model.Song
import com.asd.player.domain.usecase.BrowseFolder
import com.asd.player.domain.usecase.SearchSongs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tutto ASD Desktop collegato a mano, come AppContainer dell'app Android: impostazioni, catalogo,
 * analisi audio, player, ultimo ascolto e MPRIS. All'avvio: catalogo salvato subito, poi ripresa
 * dell'ascolto e riscansione.
 */
class Desktop {
    val settings = SettingsStore().also { I18n.use(it.state.value.language) }
    val library = Library()
    val player = Player()
    val mixtapes = Mixtapes()
    val analysis = AnalysisQueue(library, settings)
    val browse = BrowseFolder(SearchSongs())
    private val memory = PlaybackMemory()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val sleep = SleepTimer(player, scope)
    val tube = Tube(settings, onSaved = ::rescan)

    /** Richieste di portare la finestra davanti (dai controlli multimediali di GNOME). */
    private val _raise = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val raise: SharedFlow<Unit> = _raise.asSharedFlow()

    /** Chiusura chiesta da fuori (MPRIS "Quit"): la imposta la finestra. */
    @Volatile var onQuit: () -> Unit = {}

    val mpris = Mpris(player, onRaise = { _raise.tryEmit(Unit) }, onQuit = { onQuit() })

    /** Misure correnti per URI e mediana della libreria (per i pezzi non ancora misurati). */
    @Volatile private var byUri: Map<String, Song> = emptyMap()
    @Volatile private var medianLufs: Double? = null

    init {
        Song.unknownArtist = Texts.UNKNOWN_ARTIST
        player.adjust = ::adjustFor
        analysis.start()
        mpris.start()

        scope.launch {
            val folder = settings.state.value.folder
            if (folder != null) {
                library.load(folder)
                restorePlayback()
                library.rescan(folder)
            }
        }
        // Misure nuove o interruttori cambiati: il pezzo che suona si regola subito.
        scope.launch {
            combine(library.songs, settings.state.map { it.normalizeVolume to it.skipSilence }.distinctUntilChanged()) { songs, _ -> songs }
                .collect { songs ->
                    byUri = songs.associateBy { it.uri }
                    medianLufs = VolumeLeveling.median(songs.mapNotNull { it.loudnessLufs })
                    player.refreshAdjust()
                }
        }
        // Il pezzo che suona si analizza per primo; MPRIS sa sempre cosa suona.
        scope.launch {
            player.state.collect { s ->
                analysis.prioritize(s.current?.uri)
                mpris.publish()
            }
        }
        // Ultimo ascolto: a ogni cambio di pezzo, pausa o modo, e ogni 15 secondi mentre suona.
        scope.launch {
            player.state.map { listOf(it.index, it.playing, it.shuffle, it.repeat, it.queue.size) }
                .distinctUntilChanged()
                .collect { savePlayback() }
        }
        scope.launch {
            while (true) {
                delay(15_000)
                if (player.state.value.playing) savePlayback()
            }
        }
    }

    /** Guadagno e silenzio di un pezzo con le misure più recenti (la coda tiene copie vecchie dei pezzi). */
    private fun adjustFor(song: Song): Adjust {
        val s = settings.state.value
        val fresh = byUri[song.uri] ?: song
        return Adjust(
            gainDb = if (s.normalizeVolume) VolumeLeveling.gainDb(fresh.loudnessLufs, medianLufs) else 0.0,
            startMs = fresh.soundStartMs.takeIf { s.skipSilence },
            endMs = fresh.soundEndMs.takeIf { s.skipSilence },
        )
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

    fun setNormalizeVolume(on: Boolean) = settings.update { it.copy(normalizeVolume = on) }
    fun setSkipSilence(on: Boolean) = settings.update { it.copy(skipSilence = on) }
    fun setYtBrowser(browser: String?) = settings.update { it.copy(ytBrowser = browser) }

    /** [tag] null = come il sistema. La finestra si ridisegna da sola (I18n.language). */
    fun setLanguage(tag: String?) {
        settings.update { it.copy(language = tag) }
        I18n.use(tag)
        Song.unknownArtist = Texts.UNKNOWN_ARTIST
    }

    /** I pezzi di un mixtape che ci sono ancora, nell'ordine del nastro (con l'indice nel nastro). */
    fun songsOf(mixtape: Mixtape, songs: List<Song>): List<Pair<Int, Song>> {
        val byUri = songs.associateBy { it.uri }
        return mixtape.uris.mapIndexedNotNull { i, uri -> byUri[uri]?.let { i to it } }
    }

    /** INFO e TESTO: tag, copertina dai tag (come nell'app: niente cover.jpg, scartato), testo. */
    suspend fun details(song: Song): SongDetails = withContext(Dispatchers.IO) {
        val file = runCatching { java.io.File(java.net.URI(song.uri)) }.getOrNull() ?: return@withContext SongDetails()
        TagReader.details(file)
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

    /** All'uscita: salva dove si era e le misure, chiude MPRIS e GStreamer. */
    fun close() {
        sleep.cancel()
        savePlayback()
        library.flush()
        mpris.close()
        player.shutdown()
    }
}
