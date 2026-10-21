package com.asd.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * Volume uniforme: a ogni cambio di brano (e quando arriva una misura nuova) il volume del
     * player diventa il guadagno ReplayGain del brano corrente.
     */
    private fun levelVolume(player: ExoPlayer) {
        val container = (application as MusicApplication).container
        fun apply() {
            val uri = player.currentMediaItem?.mediaId
            player.volume = if (normalize) VolumeLeveling.volumeFor(levels[uri], fallbackLufs) else 1f
        }
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
