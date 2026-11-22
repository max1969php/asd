package com.asd.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import android.os.Bundle
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.SettableFuture
import com.asd.player.MainActivity
import com.asd.player.MusicApplication
import com.asd.player.data.loudness.VolumeLeveling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Tiene vivo ExoPlayer in background ed espone la sessione al sistema: notifica, lock screen,
 * cuffie Bluetooth, tasti multimediali e Android Auto, che sfoglia la libreria ([CarLibrary]) e
 * gli passa le richieste vocali ("metti … con ASD").
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private val car by lazy { CarLibrary((application as MusicApplication).container) }
    private val scope = MainScope()

    /** Loudness misurata per URI, e la mediana per i brani non ancora misurati. */
    private var levels: Map<String, Double> = emptyMap()
    private var fallbackLufs: Double? = null
    private var normalize = true

    /** Sfumatura del timer di spegnimento, moltiplicata al volume uniforme. */
    private var fade = 1f
    private var applyVolume: () -> Unit = {}

    /** Margine per l'equalizzatore (dB, 0 o negativo): con le bande alzate il player abbassa un po'. */
    private var headroomDb = 0.0

    override fun onCreate() {
        super.onCreate()
        // Un'unica pipeline per entrambe le fonti: i file locali passano dritti,
        // quelli di Drive vengono riscritti in HTTPS autenticato dal resolver.
        val resolver = (application as MusicApplication).container.streamResolver
        val dataSourceFactory = ResolvingDataSource.Factory(DefaultDataSource.Factory(this), resolver)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        // La sessione su cui stanno equalizzatore e bassi.
        player.audioSessionId = (application as MusicApplication).container.audioEffects.sessionId

        levelVolume(player)
        rememberPlayback(player)
        runSleepTimer(player)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        player?.let(::savePosition)
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.player?.let(::savePosition)
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private val memory by lazy { PlaybackMemory(this) }

    /** Esegue il timer di spegnimento impostato dalla UI. */
    private fun runSleepTimer(player: ExoPlayer) {
        val timer = (application as MusicApplication).container.sleepTimer
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // "A fine pezzo": ExoPlayer si è fermato da solo alla fine del brano.
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                    timer.state.value == SleepTimerState.EndOfSong
                ) timer.cancel()
            }
        })
        scope.launch {
            timer.state.collectLatest { state ->
                player.pauseAtEndOfMediaItems = state == SleepTimerState.EndOfSong
                if (state !is SleepTimerState.At) {
                    fade = 1f
                    applyVolume()
                    return@collectLatest
                }
                while (true) {
                    val left = state.endsAt - SystemClock.elapsedRealtime()
                    if (left <= 0) {
                        player.pause()
                        fade = 1f
                        applyVolume()
                        timer.cancel()
                        break
                    }
                    val next = (left.toFloat() / SleepTimer.FADE_MS).coerceAtMost(1f)
                    if (next != fade) {
                        fade = next
                        applyVolume()
                    }
                    delay(if (left > SleepTimer.FADE_MS) minOf(left - SleepTimer.FADE_MS, 1_000L) else 200L)
                }
            }
        }
    }

    /**
     * Ultimo ascolto: la coda si salva quando cambia, pezzo e punto a ogni pausa, cambio pezzo e
     * ogni tanto mentre suona. Alla partenza, se nessuno ha già chiesto altro, il player si rimette
     * lì, in pausa e senza preparare (niente notifica finché non si preme play).
     */
    private fun rememberPlayback(player: ExoPlayer) {
        val container = (application as MusicApplication).container
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED || player.mediaItemCount == 0) return
                val uris = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId }
                scope.launch(Dispatchers.IO) { memory.saveQueue(uris) }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                    )
                ) savePosition(player)
            }
        })
        scope.launch {
            while (true) {
                delay(15_000)
                if (player.isPlaying) savePosition(player)
            }
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) { memory.load() } ?: return@launch
            val byUri = container.libraryRepository.songs.first().associateBy { it.uri }
            if (player.mediaItemCount > 0) return@launch
            // I pezzi spariti dalla cartella si saltano; se manca quello corrente si parte dal suo posto.
            val current = saved.uris.getOrNull(saved.index)
            val songs = saved.uris.mapNotNull { byUri[it] }
            if (songs.isEmpty()) return@launch
            val index = songs.indexOfFirst { it.uri == current }
            player.shuffleModeEnabled = saved.shuffle
            player.repeatMode = saved.repeatMode
            val items = songs.map { it.toMediaItem(container.settings.skipSilence.value) }
            if (index >= 0) {
                player.setMediaItems(items, index, saved.positionMs)
            } else {
                player.setMediaItems(items, saved.index.coerceIn(songs.indices), C.TIME_UNSET)
            }
        }
    }

    private fun savePosition(player: Player) {
        if (player.mediaItemCount == 0) return
        memory.savePosition(player.currentMediaItemIndex, player.currentPosition, player.shuffleModeEnabled, player.repeatMode)
    }

    /**
     * Volume uniforme: a ogni cambio di brano (e quando arriva una misura nuova) si applica il
     * guadagno del brano corrente, tolto il margine dell'equalizzatore. Sotto zero lo fa il volume
     * del player, sopra zero il LoudnessEnhancer (il volume del player non va oltre 1). Tutti e due
     * agiscono su ciò che esce ora dalle casse, quindi cambiano proprio al cambio di brano.
     */
    private fun levelVolume(player: ExoPlayer) {
        val container = (application as MusicApplication).container
        fun apply() {
            val uri = player.currentMediaItem?.mediaId
            val gain = if (normalize) VolumeLeveling.gainDb(levels[uri], fallbackLufs) else 0.0
            val total = gain + headroomDb
            player.volume = fade * VolumeLeveling.linear(total.coerceAtMost(0.0))
            container.audioEffects.setBoost(total)
        }
        applyVolume = ::apply
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                apply()
                // Se non è ancora misurato (volume o silenzio) passa davanti agli altri.
                if (mediaItem != null) container.loudnessQueue.prioritize(mediaItem.mediaId)
            }
        })
        val measured = container.libraryRepository.songs
            .map { songs -> songs.mapNotNull { s -> s.loudnessLufs?.let { s.uri to it } }.toMap() }
            .flowOn(Dispatchers.Default)
        scope.launch {
            container.settings.eq.collect { eq ->
                container.audioEffects.apply(eq)
                headroomDb = container.audioEffects.headroomDb(eq)
                apply()
            }
        }
        scope.launch {
            combine(measured, container.settings.normalizeVolume, ::Pair).collect { (map, enabled) ->
                levels = map
                fallbackLufs = VolumeLeveling.median(map.values)
                normalize = enabled
                apply()
            }
        }
    }

    /** Un risultato calcolato in una coroutine sul main thread, come lo vuole Media3. */
    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        scope.launch {
            try {
                result.set(block())
            } catch (e: Exception) {
                result.setException(e)
            }
        }
        return result
    }

    /** Dove si era rimasti: la coda che c'è, altrimenti l'ultimo ascolto salvato. */
    private suspend fun lastListening(): MediaSession.MediaItemsWithStartPosition {
        val player = session?.player
        if (player != null && player.mediaItemCount > 0) {
            return MediaSession.MediaItemsWithStartPosition(
                List(player.mediaItemCount) { player.getMediaItemAt(it) }, player.currentMediaItemIndex, player.currentPosition,
            )
        }
        val container = (application as MusicApplication).container
        val saved = withContext(Dispatchers.IO) { memory.load() } ?: throw IllegalStateException("niente da riprendere")
        val byUri = container.libraryRepository.songs.first().associateBy { it.uri }
        val songs = saved.uris.mapNotNull { byUri[it] }
        if (songs.isEmpty()) throw IllegalStateException("niente da riprendere")
        val index = songs.indexOfFirst { it.uri == saved.uris.getOrNull(saved.index) }
        val skip = container.settings.skipSilence.value
        return MediaSession.MediaItemsWithStartPosition(
            songs.map { it.toMediaItem(skip) },
            if (index >= 0) index else saved.index.coerceIn(songs.indices),
            if (index >= 0) saved.positionMs else C.TIME_UNSET,
        )
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        /** Le schede di Android Auto in elenco, con la ricerca. */
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val extras = Bundle().apply {
                putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
            }
            return Futures.immediateFuture(LibraryResult.ofItem(car.root(), LibraryParams.Builder().setExtras(extras).build()))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val all = car.children(parentId) ?: return@future LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            LibraryResult.ofItemList(page(all, page, pageSize), params)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = future {
            session.notifySearchResultChanged(browser, query, car.search(query).size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            LibraryResult.ofItemList(page(car.search(query), page, pageSize), params)
        }

        /**
         * Da Android Auto e dall'assistente arrivano solo id o testo cercato: qui diventano la coda
         * vera. Testo vuoto ("metti qualcosa con ASD") = riprende l'ultimo ascolto. Dall'app
         * arrivano item completi, che passano come prima.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            val query = mediaItems.singleOrNull()?.takeIf { it.mediaId.isEmpty() }?.requestMetadata?.searchQuery
            if (query != null && query.isBlank()) return@future lastListening()
            val resolved = car.resolve(mediaItems)
                ?: return@future MediaSession.MediaItemsWithStartPosition(withUris(mediaItems), startIndex, startPositionMs)
            val (songs, index) = resolved
            if (songs.isEmpty()) throw IllegalArgumentException("niente trovato: $query")
            val skip = (application as MusicApplication).container.settings.skipSilence.value
            MediaSession.MediaItemsWithStartPosition(songs.map { it.toMediaItem(skip) }, index, C.TIME_UNSET)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = future {
            val skip = (application as MusicApplication).container.settings.skipSilence.value
            car.resolve(mediaItems)?.first?.map { it.toMediaItem(skip) }?.toMutableList() ?: withUris(mediaItems)
        }

        /** Play da Android Auto o dalla notifica di ripresa, con ASD chiuso: l'ultimo ascolto. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future { lastListening() }

        /**
         * Gli item che attraversano il confine controller → sessione perdono l'URI locale:
         * lo ricostruiamo dal mediaId, che contiene l'URI del documento.
         */
        private fun withUris(mediaItems: List<MediaItem>): MutableList<MediaItem> =
            mediaItems.map { item ->
                item.buildUpon().setUri(item.requestMetadata.mediaUri ?: Uri.parse(item.mediaId)).build()
            }.toMutableList()

        private fun <T> page(all: List<T>, page: Int, pageSize: Int): ImmutableList<T> {
            if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return ImmutableList.copyOf(all)
            val from = (page.toLong() * pageSize).coerceAtMost(all.size.toLong()).toInt()
            return ImmutableList.copyOf(all.subList(from, minOf(all.size, from + pageSize)))
        }
    }
}
