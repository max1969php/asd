package com.asd.player.ui.player

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asd.player.AppContainer
import com.asd.player.data.loudness.VolumeLeveling
import com.asd.player.domain.model.Song
import com.asd.player.playback.PlaybackController
import com.asd.player.playback.PlaybackState
import com.asd.player.playback.SleepTimer
import com.asd.player.playback.SleepTimerState
import com.asd.player.playback.TrackDetails
import kotlin.math.log10
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

/** Il pezzo che suona con quello che si sa di lui: per copertina, pannello info e testo. */
data class NowPlayingInfo(
    val song: Song? = null,
    val details: TrackDetails? = null,
    val artwork: ImageBitmap? = null,
    val loading: Boolean = false,
    /** Correzione del volume uniforme in dB (0 o negativa); null se spento o non misurato. */
    val gainDb: Double? = null,
)

class PlayerViewModel(container: AppContainer) : ViewModel() {

    private val controller: PlaybackController = container.playbackController
    private val sleepTimer: SleepTimer = container.sleepTimer
    private val reader = container.trackDetails

    private val currentSong: Flow<Song?> = combine(
        controller.state.map { it.nowPlaying?.mediaId }.distinctUntilChanged(),
        container.libraryRepository.songs,
    ) { uri, songs -> songs.find { it.uri == uri } }.distinctUntilChanged()

    private val gain: Flow<(Song?) -> Double?> = combine(
        container.libraryRepository.songs.map { songs -> VolumeLeveling.median(songs.mapNotNull { it.loudnessLufs }) },
        container.settings.normalizeVolume,
    ) { median, normalize ->
        { song: Song? ->
            song?.loudnessLufs?.takeIf { normalize }?.let { 20 * log10(VolumeLeveling.volumeFor(it, median).toDouble()) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val details: Flow<Pair<Song?, NowPlayingInfo?>> = currentSong
        .distinctUntilChanged { a, b -> a?.uri == b?.uri }
        .transformLatest { song ->
            emit(song to null)
            if (song == null) return@transformLatest
            val read = withContext(Dispatchers.IO) { reader.read(song) }
            val art = read?.artwork?.let { bytes -> withContext(Dispatchers.Default) { decodeArtwork(bytes) } }
            emit(song to NowPlayingInfo(song, read, art))
        }

    /** La copertina viene letta solo per il pezzo che suona, mai per la lista. */
    val info: StateFlow<NowPlayingInfo> = combine(currentSong, details, gain) { song, (detailsFor, loaded), gainOf ->
        val ready = loaded?.takeIf { detailsFor?.uri == song?.uri }
        NowPlayingInfo(
            song = song,
            details = ready?.details,
            artwork = ready?.artwork,
            loading = song != null && ready == null,
            gainDb = gainOf(song),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingInfo())

    val state: StateFlow<PlaybackState> = controller.state
    val errors: SharedFlow<String> = controller.errors

    /** La posizione non arriva via eventi: la campioniamo finché qualcuno la osserva. */
    val position: Flow<Long> = flow {
        while (true) {
            emit(controller.positionMs)
            delay(500)
        }
    }

    val sleepState: StateFlow<SleepTimerState> = sleepTimer.state

    /** Millisecondi al timer di spegnimento, null se non c'è un timer a tempo. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sleepLeft: Flow<Long?> = sleepTimer.state.flatMapLatest { state ->
        if (state !is SleepTimerState.At) flowOf(null) else flow {
            while (true) {
                emit((state.endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                delay(1_000)
            }
        }
    }

    fun sleepIn(minutes: Int) = sleepTimer.inMinutes(minutes)
    fun sleepAtEndOfSong() = sleepTimer.atEndOfSong()
    fun cancelSleep() = sleepTimer.cancel()

    fun togglePlayPause() = controller.togglePlayPause()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun seekTo(ms: Long) = controller.seekTo(ms)
    fun toggleShuffle() = controller.toggleShuffle()
    fun cycleRepeat() = controller.cycleRepeat()

    init {
        controller.connect()
    }

    private companion object {
        /** Abbastanza per lo schermo del player, senza tenere in memoria immagini da 3000 px. */
        const val ARTWORK_PX = 1024

        fun decodeArtwork(bytes: ByteArray): ImageBitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ARTWORK_PX) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.asImageBitmap()
        }.getOrNull()
    }
}
