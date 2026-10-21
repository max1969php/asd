package com.asd.player.data.loudness

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

/** Volume percepito di un brano: loudness integrata (LUFS) e picco campione (dBFS). */
data class Loudness(val integratedLufs: Double, val samplePeakDb: Double)

/**
 * Misuratore ITU-R BS.1770-4 / EBU R128, lo stesso standard su cui si basano ReplayGain 2.0
 * e la normalizzazione di Spotify, YouTube e Apple Music:
 * filtro K (shelving + passa-alto), blocchi da 400 ms sovrapposti al 75%,
 * gate assoluto a -70 LUFS e gate relativo a -10 LU.
 */
class LoudnessMeter(private val sampleRate: Int, private val channels: Int) {

    private val filters = Array(channels) { KWeighting(sampleRate) }

    /** Pesi dei canali: 1 per L/R/C, 0 per il subwoofer, 1.41 per i surround (layout 5.1). */
    private val weights = DoubleArray(channels) { ch ->
        when {
            channels < 6 -> 1.0
            ch == 3 -> 0.0
            ch >= 4 -> 1.41
            else -> 1.0
        }
    }

    /** Un blocco da 100 ms: quattro consecutivi fanno un blocco di gating da 400 ms. */
    private val stepFrames = sampleRate / 10
    private val stepEnergy = DoubleArray(channels)
    private var stepFilled = 0
    private val steps = ArrayList<Double>()
    private var peak = 0.0

    /** Campioni interleaved in [-1, 1]. */
    fun feed(samples: FloatArray, frames: Int) {
        var i = 0
        repeat(frames) {
            for (ch in 0 until channels) {
                val x = samples[i++].toDouble()
                peak = max(peak, abs(x))
                val y = filters[ch].process(x)
                stepEnergy[ch] += y * y
            }
            if (++stepFilled == stepFrames) closeStep()
        }
    }

    fun result(): Loudness {
        val blocks = (3 until steps.size).map { i ->
            (steps[i - 3] + steps[i - 2] + steps[i - 1] + steps[i]) / (4.0 * stepFrames)
        }
        val absoluteGated = blocks.filter { lufs(it) > ABSOLUTE_GATE }
        val integrated = if (absoluteGated.isEmpty()) {
            Double.NEGATIVE_INFINITY
        } else {
            val relativeGate = lufs(absoluteGated.average()) - 10.0
            val gated = absoluteGated.filter { lufs(it) > relativeGate }
            lufs(gated.average())
        }
        return Loudness(integrated, if (peak > 0) 20 * log10(peak) else Double.NEGATIVE_INFINITY)
    }

    private fun closeStep() {
        var weighted = 0.0
        for (ch in 0 until channels) {
            weighted += weights[ch] * stepEnergy[ch]
            stepEnergy[ch] = 0.0
        }
        steps += weighted
        stepFilled = 0
    }

    private fun lufs(meanSquare: Double) = -0.691 + 10 * log10(meanSquare)

    /**
     * Filtro K come due biquad in cascata, con i coefficienti ricavati per qualsiasi
     * frequenza di campionamento (a 48 kHz coincidono con quelli tabulati nella BS.1770).
     */
    private class KWeighting(fs: Int) {
        private val shelf: Biquad
        private val highPass: Biquad

        init {
            var f0 = 1681.974450955533
            val gain = 3.999843853973347
            var q = 0.7071752369554196
            var k = tan(PI * f0 / fs)
            val vh = 10.0.pow(gain / 20)
            val vb = vh.pow(0.4996667741545416)
            var a0 = 1 + k / q + k * k
            shelf = Biquad(
                b0 = (vh + vb * k / q + k * k) / a0,
                b1 = 2 * (k * k - vh) / a0,
                b2 = (vh - vb * k / q + k * k) / a0,
                a1 = 2 * (k * k - 1) / a0,
                a2 = (1 - k / q + k * k) / a0,
            )
            f0 = 38.13547087602444
            q = 0.5003270373238773
            k = tan(PI * f0 / fs)
            a0 = 1 + k / q + k * k
            highPass = Biquad(b0 = 1.0, b1 = -2.0, b2 = 1.0, a1 = 2 * (k * k - 1) / a0, a2 = (1 - k / q + k * k) / a0)
        }

        fun process(x: Double) = highPass.process(shelf.process(x))
    }

    private class Biquad(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double,
    ) {
        private var z1 = 0.0
        private var z2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    private companion object {
        const val ABSOLUTE_GATE = -70.0
    }
}
