package com.asd.player.data.loudness

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.IOException
import java.nio.ByteOrder

/**
 * Decodifica un brano intero in PCM (MediaCodec, quindi tutti i formati che Android legge)
 * e lo passa al [LoudnessMeter]. Bloccante e pesante: un brano di 4 minuti costa qualche secondo.
 */
class LoudnessAnalyzer(private val context: Context) {

    fun analyzeLocal(uri: Uri): Loudness = analyze { setDataSource(context, uri, null) }

    fun analyzeRemote(url: String, headers: Map<String, String>): Loudness = analyze { setDataSource(url, headers) }

    private fun analyze(open: MediaExtractor.() -> Unit): Loudness {
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

    private fun decode(extractor: MediaExtractor, codec: MediaCodec, input: MediaFormat): Loudness {
        var meter: LoudnessMeter? = null
        var channels = input.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var sampleRate = input.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var floatPcm = false
        var samples = FloatArray(0)
        val info = MediaCodec.BufferInfo()
        var inputDone = false

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
                    m.feed(samples, count / channels)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
        return meter?.result() ?: throw IOException("Niente audio decodificato")
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}
