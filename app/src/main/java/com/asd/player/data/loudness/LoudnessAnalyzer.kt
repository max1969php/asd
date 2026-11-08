package com.asd.player.data.loudness

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.IOException
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Il risultato dell'analisi: volume percepito e dove il suono comincia e finisce
 * (in ms dall'inizio del file decodificato; null se è tutto silenzio).
 */
data class AudioAnalysis(
    val loudness: Loudness,
    val firstSoundMs: Long?,
    val lastSoundMs: Long?,
    val totalMs: Long,
)

/**
 * Decodifica un brano intero in PCM (MediaCodec, quindi tutti i formati che Android legge)
 * e lo passa al [LoudnessMeter]. Bloccante e pesante: un brano di 4 minuti costa qualche secondo.
 */
class LoudnessAnalyzer(private val context: Context) {

    fun analyzeLocal(uri: Uri): AudioAnalysis = analyze { setDataSource(context, uri, null) }

    fun analyzeRemote(url: String, headers: Map<String, String>): AudioAnalysis = analyze { setDataSource(url, headers) }

    private fun analyze(open: MediaExtractor.() -> Unit): AudioAnalysis {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.open()
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            } ?: throw IOException("Nessuna traccia audio")
            extractor.selectTrack(track)
            val input = extractor.getTrackFormat(track)
            codec = MediaCodec.createDecoderByType(input.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(input, null, null, 0)
            codec.start()
            return decode(extractor, codec, input)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun decode(extractor: MediaExtractor, codec: MediaCodec, input: MediaFormat): AudioAnalysis {
        var meter: LoudnessMeter? = null
        var channels = input.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var sampleRate = input.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var floatPcm = false
        var samples = FloatArray(0)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        // Tempo in µs già decodificato, e primo/ultimo istante sopra la soglia del silenzio.
        var decodedUs = 0.0
        var firstSoundUs = -1.0
        var lastSoundUs = -1.0

        while (true) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val output = codec.outputFormat
                    channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    floatPcm = output.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        output.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    meter = null
                }
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)!!.order(ByteOrder.nativeOrder())
                    buffer.position(info.offset).limit(info.offset + info.size)
                    val count = if (floatPcm) info.size / 4 else info.size / 2
                    if (samples.size < count) samples = FloatArray(count)
                    if (floatPcm) {
                        buffer.asFloatBuffer().get(samples, 0, count)
                    } else {
                        val shorts = buffer.asShortBuffer()
                        for (i in 0 until count) samples[i] = shorts.get(i) / 32768f
                    }
                    codec.releaseOutputBuffer(index, false)
                    val m = meter ?: LoudnessMeter(sampleRate, channels).also { meter = it }
                    val frames = count / channels
                    m.feed(samples, frames)
                    val usPerFrame = 1_000_000.0 / sampleRate
                    var first = -1
                    var last = -1
                    for (i in 0 until frames * channels) {
                        if (abs(samples[i]) > SILENCE_THRESHOLD) {
                            if (first < 0) first = i / channels
                            last = i / channels
                        }
                    }
                    if (first >= 0) {
                        if (firstSoundUs < 0) firstSoundUs = decodedUs + first * usPerFrame
                        lastSoundUs = decodedUs + (last + 1) * usPerFrame
                    }
                    decodedUs += frames * usPerFrame
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
        val loudness = meter?.result() ?: throw IOException("Niente audio decodificato")
        return AudioAnalysis(
            loudness,
            firstSoundUs.takeIf { it >= 0 }?.let { (it / 1000).toLong() },
            lastSoundUs.takeIf { it >= 0 }?.let { (it / 1000).toLong() },
            (decodedUs / 1000).toLong(),
        )
    }

    private companion object {
        const val TIMEOUT_US = 10_000L

        /** −54 dBFS: sotto è silenzio (anche il fruscio di un vinile o di una cassetta). */
        const val SILENCE_THRESHOLD = 0.002f
    }
}
