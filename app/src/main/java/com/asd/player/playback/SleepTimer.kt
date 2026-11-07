package com.asd.player.playback

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Timer di spegnimento: tra N minuti, oppure a fine pezzo. */
sealed interface SleepTimerState {
    data object Off : SleepTimerState
    /** [endsAt] in [SystemClock.elapsedRealtime]: non cambia se si sposta l'ora del telefono. */
    data class At(val endsAt: Long) : SleepTimerState
    data object EndOfSong : SleepTimerState
}

/**
 * Lo imposta la UI, lo esegue PlaybackService (stesso processo): sfuma il volume negli ultimi
 * secondi e mette in pausa.
 */
class SleepTimer {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    fun inMinutes(minutes: Int) {
        _state.value = SleepTimerState.At(SystemClock.elapsedRealtime() + minutes * 60_000L)
    }

    fun atEndOfSong() {
        _state.value = SleepTimerState.EndOfSong
    }

    fun cancel() {
        _state.value = SleepTimerState.Off
    }

    companion object {
        const val FADE_MS = 10_000L
        val MINUTES = listOf(15, 30, 60)
    }
}
