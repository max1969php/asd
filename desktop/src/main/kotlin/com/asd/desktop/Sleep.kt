package com.asd.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Timer di spegnimento, come nell'app: tra N minuti (sfuma negli ultimi 10 s) o a fine pezzo. */
sealed interface SleepState {
    data object Off : SleepState
    /** [endsAt] in System.nanoTime()/1e6: non cambia se si sposta l'ora del PC. */
    data class At(val endsAt: Long) : SleepState
    data object EndOfSong : SleepState
}

class SleepTimer(private val player: Player, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<SleepState>(SleepState.Off)
    val state: StateFlow<SleepState> = _state.asStateFlow()
    private var job: Job? = null
    private var generation = 0

    fun inMinutes(minutes: Int) = arm(SleepState.At(now() + minutes * 60_000L)) { s ->
        while (true) {
            val left = s.endsAt - now()
            if (left <= 0) break
            if (left < FADE_MS) player.setFade(left.toDouble() / FADE_MS)
            delay(if (left < FADE_MS) 100 else minOf(left - FADE_MS + 1, 1_000))
        }
        player.pause()
    }

    fun atEndOfSong() {
        arm(SleepState.EndOfSong) { waitEndOfSong() }
        player.stopAtEnd = true
    }

    private suspend fun waitEndOfSong() {
        // Si ferma da solo (niente prossimo) oppure, se il prossimo era già in gapless, al cambio pezzo.
        player.state.first { it.playing }
        val index = player.state.value.index
        player.state.first { it.index != index || !it.playing }
        player.pause()
    }

    @Synchronized
    fun cancel() {
        generation++
        job?.cancel()
        reset()
    }

    /** Millisecondi rimasti, per la UI. */
    fun leftMs(): Long? = (_state.value as? SleepState.At)?.let { (it.endsAt - now()).coerceAtLeast(0) }

    @Synchronized
    private fun <S : SleepState> arm(state: S, run: suspend (S) -> Unit) {
        job?.cancel()
        reset()
        _state.value = state
        val mine = ++generation
        job = scope.launch {
            try {
                run(state)
            } finally {
                synchronized(this@SleepTimer) { if (generation == mine) reset() }
            }
        }
    }

    private fun reset() {
        player.stopAtEnd = false
        player.setFade(1.0)
        _state.value = SleepState.Off
    }

    private fun now() = System.nanoTime() / 1_000_000

    companion object {
        const val FADE_MS = 10_000L
        val MINUTES = listOf(15, 30, 60)
    }
}
