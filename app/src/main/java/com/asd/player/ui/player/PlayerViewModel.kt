package com.asd.player.ui.player

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.asd.player.playback.PlaybackController
import com.asd.player.playback.PlaybackState
import com.asd.player.playback.SleepTimer
import com.asd.player.playback.SleepTimerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

class PlayerViewModel(
    private val controller: PlaybackController,
    private val sleepTimer: SleepTimer,
) : ViewModel() {

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
}
