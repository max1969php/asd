package com.asd.desktop

import com.asd.player.domain.model.Song
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.freedesktop.gstreamer.Bus
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.GstObject
import org.freedesktop.gstreamer.message.Message
import org.freedesktop.gstreamer.message.MessageType
import org.freedesktop.gstreamer.State
import org.freedesktop.gstreamer.Version
import org.freedesktop.gstreamer.elements.PlayBin

enum class Repeat { OFF, ALL, ONE }

data class PlayerState(
    val queue: List<Song> = emptyList(),
    /** Indice in [queue] del pezzo corrente; -1 = niente in coda. */
    val index: Int = -1,
    val playing: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Repeat = Repeat.OFF,
    val error: String? = null,
) {
    val current: Song? get() = queue.getOrNull(index)
}

/**
 * Il motore audio di ASD Desktop: GStreamer (playbin), che sul PC legge tutti i formati di ASD.
 * Gapless: a fine pezzo ("about-to-finish") playbin riceve già il prossimo, e il cambio vero si
 * vede con il messaggio STREAM_START. La UI vede solo [state] e i comandi, mai GStreamer.
 */
class Player {

    private val lock = Any()
    private val playbin: PlayBin

    /** C'è un decoder AAC (gstreamer1.0-libav)? Senza, gli M4A del TUBO non suonano. */
    val canPlayAac: Boolean

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Ordine di riproduzione: indici di queue (mescolati con shuffle). */
    @Volatile private var order: List<Int> = emptyList()
    /** Il pezzo già passato a playbin per il gapless, che diventa corrente allo STREAM_START. */
    @Volatile private var pending: Int? = null
    /** Punto da cui ripartire quando la pipeline è pronta (ultimo ascolto). */
    private var pendingSeekMs: Long = 0

    init {
        Gst.init(Version.of(1, 16), "asd-desktop")
        playbin = PlayBin("asd")
        canPlayAac = listOf("avdec_aac", "faad").any { name ->
            runCatching { org.freedesktop.gstreamer.ElementFactory.find(name) != null }.getOrDefault(false)
        }
        // Niente video: solo audio.
        playbin.setVideoSink(org.freedesktop.gstreamer.ElementFactory.make("fakesink", "novideo"))
        // Arriva dal thread audio di GStreamer: niente lock, altrimenti uno stop() dalla UI
        // (che aspetta proprio quel thread) si bloccherebbe per sempre.
        playbin.connect(PlayBin.ABOUT_TO_FINISH { pb ->
            val next = nextIndex(automatic = true) ?: return@ABOUT_TO_FINISH
            pending = next
            pb.setURI(URI(_state.value.queue[next].uri))
        })
        playbin.bus.connect(Bus.MESSAGE { _, message -> onMessage(message) })
        playbin.bus.connect(Bus.EOS { _: GstObject ->
            synchronized(lock) {
                // Fine della coda: si resta sull'ultimo pezzo, fermi all'inizio.
                playbin.stop()
                _state.value = _state.value.copy(playing = false)
            }
        })
        playbin.bus.connect(Bus.ERROR { _: GstObject, _: Int, message: String ->
            synchronized(lock) {
                _state.value = _state.value.copy(error = message)
                val next = nextIndex(automatic = true)
                if (next != null) start(next, play = true) else {
                    playbin.stop()
                    _state.value = _state.value.copy(playing = false)
                }
            }
        })
    }

    private fun onMessage(message: Message) {
        when (message.type) {
            MessageType.STREAM_START -> synchronized(lock) {
                pending?.let { next ->
                    pending = null
                    _state.value = _state.value.copy(index = next, durationMs = _state.value.queue[next].durationMs)
                }
            }
            MessageType.ASYNC_DONE -> synchronized(lock) {
                if (pendingSeekMs > 0) {
                    playbin.seek(pendingSeekMs, TimeUnit.MILLISECONDS)
                    pendingSeekMs = 0
                }
                val d = playbin.queryDuration(TimeUnit.MILLISECONDS)
                if (d > 0) _state.value = _state.value.copy(durationMs = d)
            }
            else -> Unit
        }
    }

    val positionMs: Long
        get() = runCatching { playbin.queryPosition(TimeUnit.MILLISECONDS) }.getOrDefault(0L).coerceAtLeast(0L)

    /** Suona [songs] da [startIndex]; con [shuffle] in ordine casuale, partendo comunque da lì. */
    fun playQueue(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        synchronized(lock) {
            val start = if (shuffle && startIndex == 0) songs.indices.random() else startIndex.coerceIn(songs.indices)
            _state.value = _state.value.copy(queue = songs, shuffle = shuffle, error = null)
            order = buildOrder(songs.size, start, shuffle)
            start(start, play = true)
        }
    }

    /** Rimette la coda dell'ultimo ascolto, in pausa, al punto salvato. */
    fun restore(songs: List<Song>, index: Int, positionMs: Long, shuffle: Boolean, repeat: Repeat) {
        if (songs.isEmpty()) return
        synchronized(lock) {
            val i = index.coerceIn(songs.indices)
            _state.value = _state.value.copy(queue = songs, shuffle = shuffle, repeat = repeat)
            order = buildOrder(songs.size, i, shuffle)
            pendingSeekMs = positionMs
            start(i, play = false)
        }
    }

    fun togglePlayPause() = synchronized(lock) {
        val s = _state.value
        if (s.current == null) return@synchronized
        if (s.playing) {
            playbin.pause()
            _state.value = s.copy(playing = false)
        } else {
            playbin.play()
            _state.value = s.copy(playing = true)
        }
    }

    fun next() = synchronized(lock) { nextIndex(automatic = false)?.let { start(it, _state.value.playing) } }

    /** Indietro: dopo i primi 3 secondi torna all'inizio del pezzo, altrimenti al precedente. */
    fun previous() = synchronized(lock) {
        if (positionMs > 3_000) {
            seekTo(0)
            return@synchronized
        }
        val pos = order.indexOf(_state.value.index)
        val prev = when {
            pos > 0 -> order[pos - 1]
            _state.value.repeat == Repeat.ALL && order.isNotEmpty() -> order.last()
            else -> null
        }
        if (prev != null) start(prev, _state.value.playing) else seekTo(0)
    }

    fun seekTo(ms: Long) {
        synchronized(lock) { playbin.seek(ms.coerceAtLeast(0), TimeUnit.MILLISECONDS) }
    }

    fun toggleShuffle() = synchronized(lock) {
        val s = _state.value
        val shuffle = !s.shuffle
        order = buildOrder(s.queue.size, s.index.coerceAtLeast(0), shuffle)
        _state.value = s.copy(shuffle = shuffle)
        refreshPending()
    }

    fun cycleRepeat() = synchronized(lock) {
        val s = _state.value
        _state.value = s.copy(
            repeat = when (s.repeat) {
                Repeat.OFF -> Repeat.ALL
                Repeat.ALL -> Repeat.ONE
                Repeat.ONE -> Repeat.OFF
            }
        )
        refreshPending()
    }

    fun shutdown() {
        runCatching { playbin.stop() }
        runCatching { playbin.dispose() }
    }

    private fun start(index: Int, play: Boolean) {
        pending = null
        val song = _state.value.queue[index]
        playbin.stop()
        playbin.setURI(URI(song.uri))
        if (play) playbin.play() else playbin.pause()
        _state.value = _state.value.copy(index = index, playing = play, durationMs = song.durationMs)
    }

    /** Il prossimo pezzo; [automatic] = fine naturale del pezzo (lì vale "ripeti uno"). */
    private fun nextIndex(automatic: Boolean): Int? {
        val s = _state.value
        if (s.queue.isEmpty()) return null
        if (automatic && s.repeat == Repeat.ONE) return s.index
        val pos = order.indexOf(s.index)
        return when {
            pos in 0 until order.lastIndex -> order[pos + 1]
            s.repeat != Repeat.OFF -> order.first()
            else -> null
        }
    }

    /** Se il pezzo dopo era già stato passato a playbin, con shuffle/repeat cambiati non vale più. */
    private fun refreshPending() {
        if (pending == null) return
        val next = nextIndex(automatic = true)
        if (next != null && next != pending) {
            pending = next
            playbin.setURI(URI(_state.value.queue[next].uri))
        }
    }

    private fun buildOrder(size: Int, start: Int, shuffle: Boolean): List<Int> =
        if (!shuffle) List(size) { it } else listOf(start) + (0 until size).filter { it != start }.shuffled()
}
