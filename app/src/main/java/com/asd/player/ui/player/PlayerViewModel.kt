package com.asd.player.ui.player

import androidx.lifecycle.ViewModel
import com.asd.player.playback.PlaybackController
import com.asd.player.playback.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

class PlayerViewModel(private val controller: PlaybackController) : ViewModel() {

    val state: StateFlow<PlaybackState> = controller.state
    val errors: SharedFlow<String> = controller.errors

    /** La posizione non arriva via eventi: la campioniamo finché qualcuno la osserva. */
    val position: Flow<Long> = flow {
        while (true) {
            emit(controller.positionMs)
            delay(500)
        }
    }

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
