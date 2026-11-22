package com.asd.desktop

import com.asd.player.data.loudness.LoudnessMeter
import com.asd.player.data.loudness.SilenceTrim
import java.io.IOException
import java.nio.ByteOrder
import kotlin.math.abs
import org.freedesktop.gstreamer.Bus
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.GstObject
import org.freedesktop.gstreamer.Pipeline
import org.freedesktop.gstreamer.State
import org.freedesktop.gstreamer.elements.AppSink

/**
 * Decodifica un pezzo intero con GStreamer, il più in fretta possibile (niente orologio), e misura
 * volume (BS.1770, lo stesso [LoudnessMeter] dell'app) e silenzio ai lati (soglia −54 dBFS come
 * LoudnessAnalyzer dell'app). Bloccante: un pezzo di 4 minuti costa un paio di secondi.
 */
object Analyzer {

    /** −54 dBFS: sotto è silenzio (anche il fruscio di un vinile o di una cassetta). */
    private const val SILENCE_THRESHOLD = 0.002f

    /** Un file di solo silenzio: lo trattiamo come molto basso, come l'app. */
    private const val SILENCE_LUFS = -70.0

    fun analyze(uri: String): Analysis {
        GstInit.ensure()
        val pipeline = Gst.parseLaunch(
            "uridecodebin uri=\"$uri\" ! audioconvert ! audio/x-raw,format=F32LE,layout=interleaved ! " +
                "appsink name=sink sync=false max-buffers=16"
        ) as Pipeline
        val sink = pipeline.getElementByName("sink") as AppSink
        val error = java.util.concurrent.atomic.AtomicReference<String?>(null)
        // Un errore di decodifica non sblocca appsink da solo: fermare la pipeline lo fa.
        pipeline.bus.connect(Bus.ERROR { _: GstObject, _: Int, message: String ->
            error.set(message)
            Thread { pipeline.setState(State.NULL) }.start()
        })
        try {
            pipeline.play()
            var meter: LoudnessMeter? = null
            var channels = 0
            var rate = 0
            var samples = FloatArray(0)
            var frames = 0L
            var first = -1L
            var last = -1L
            while (true) {
                val sample = sink.pullSample() ?: break
                try {
                    if (meter == null) {
                        val caps = sample.caps.getStructure(0)
                        rate = caps.getInteger("rate")
                        channels = caps.getInteger("channels")
                        meter = LoudnessMeter(rate, channels)
                    }
                    val buffer = sample.buffer
                    val bytes = buffer.map(false).order(ByteOrder.LITTLE_ENDIAN)
                    val count = bytes.remaining() / 4
                    if (samples.size < count) samples = FloatArray(count)
                    bytes.asFloatBuffer().get(samples, 0, count)
                    buffer.unmap()
                    val n = count / channels
                    meter.feed(samples, n)
                    for (i in 0 until n * channels) {
                        if (abs(samples[i]) > SILENCE_THRESHOLD) {
                            val frame = frames + i / channels
                            if (first < 0) first = frame
                            last = frame + 1
                        }
                    }
                    frames += n
                } finally {
                    sample.dispose()
                }
            }
            error.get()?.let { throw IOException(it) }
            val loudness = meter?.result() ?: throw IOException("Niente audio decodificato")
            fun ms(frame: Long) = frame * 1000 / rate
            val (start, end) = SilenceTrim.bounds(first.takeIf { it >= 0 }?.let(::ms), last.takeIf { it >= 0 }?.let(::ms), ms(frames))
            return Analysis(
                lufs = loudness.integratedLufs.takeIf { it.isFinite() } ?: SILENCE_LUFS,
                peakDb = loudness.samplePeakDb.takeIf { it.isFinite() },
                startMs = start,
                endMs = end,
            )
        } finally {
            pipeline.setState(State.NULL)
            pipeline.dispose()
        }
    }
}
