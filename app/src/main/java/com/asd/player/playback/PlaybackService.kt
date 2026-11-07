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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
 * Tiene vivo ExoPlayer in background ed espone la MediaSession al sistema:
 * notifica, lock screen, cuffie Bluetooth e tasti multimediali.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = MainScope()

    /** Loudness misurata per URI, e la mediana per i brani non ancora misurati. */
    private var levels: Map<String, Double> = emptyMap()
    private var fallbackLufs: Double? = null
    private var normalize = true

    /** Sfumatura del timer di spegnimento, moltiplicata al volume uniforme. */
    private var fade = 1f
    private var applyVolume: () -> Unit = {}

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

        levelVolume(player)
        rememberPlayback(player)
        runSleepTimer(player)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(LocalFilesCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

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
            if (index >= 0) {
                player.setMediaItems(songs.map { it.toMediaItem() }, index, saved.positionMs)
            } else {
                player.setMediaItems(songs.map { it.toMediaItem() }, saved.index.coerceIn(songs.indices), 0L)
            }
        }
    }

    private fun savePosition(player: Player) {
        if (player.mediaItemCount == 0) return
        memory.savePosition(player.currentMediaItemIndex, player.currentPosition, player.shuffleModeEnabled, player.repeatMode)
    }

    /**
     * Volume uniforme: a ogni cambio di brano (e quando arriva una misura nuova) il volume del
     * player diventa il guadagno ReplayGain del brano corrente.
     */
    private fun levelVolume(player: ExoPlayer) {
        val container = (application as MusicApplication).container
        fun apply() {
            val uri = player.currentMediaItem?.mediaId
            player.volume = fade * if (normalize) VolumeLeveling.volumeFor(levels[uri], fallbackLufs) else 1f
        }
        applyVolume = ::apply
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                apply()
                if (normalize && mediaItem != null && mediaItem.mediaId !in levels) {
                    container.loudnessQueue.prioritize(mediaItem.mediaId)
                }
            }
        })
        val measured = container.libraryRepository.songs
            .map { songs -> songs.mapNotNull { s -> s.loudnessLufs?.let { s.uri to it } }.toMap() }
            .flowOn(Dispatchers.Default)
        scope.launch {
            combine(measured, container.settings.normalizeVolume, ::Pair).collect { (map, enabled) ->
                levels = map
                fallbackLufs = VolumeLeveling.median(map.values)
                normalize = enabled
                apply()
            }
        }
    }

    /**
     * Gli item che attraversano il confine controller → sessione perdono l'URI locale:
     * lo ricostruiamo dal mediaId, che contiene l'URI del documento.
     */
    private class LocalFilesCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(
                mediaItems.map { item ->
                    item.buildUpon().setUri(item.requestMetadata.mediaUri ?: Uri.parse(item.mediaId)).build()
                }.toMutableList()
            )
    }
}
