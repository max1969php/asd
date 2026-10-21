package com.asd.player.data

import com.asd.player.data.loudness.LoudnessMeter
import com.asd.player.data.loudness.VolumeLeveling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class LoudnessTest {

    /** Sinusoide stereo da [seconds] secondi a [dbfs] di ampiezza su entrambi i canali. */
    private fun sine(sampleRate: Int, hz: Double, dbfs: Double, seconds: Double): FloatArray {
        val amplitude = 10.0.pow(dbfs / 20)
        val frames = (sampleRate * seconds).toInt()
        return FloatArray(frames * 2) { i -> (amplitude * sin(2 * PI * hz * (i / 2) / sampleRate)).toFloat() }
    }

    private fun measure(sampleRate: Int, vararg parts: FloatArray): Double {
        val meter = LoudnessMeter(sampleRate, 2)
        parts.forEach { meter.feed(it, it.size / 2) }
        return meter.result().integratedLufs
    }

    @Test
    fun `sinusoide 1 kHz a -23 dBFS misura -23 LUFS (EBU Tech 3341)`() {
        assertEquals(-23.0, measure(48_000, sine(48_000, 1000.0, -23.0, 20.0)), 0.1)
        assertEquals(-23.0, measure(44_100, sine(44_100, 1000.0, -23.0, 20.0)), 0.1)
    }

    @Test
    fun `sinusoide 1 kHz a -33 dBFS misura -33 LUFS`() {
        assertEquals(-33.0, measure(48_000, sine(48_000, 1000.0, -33.0, 20.0)), 0.1)
    }

    @Test
    fun `il silenzio non abbassa la misura grazie al gate`() {
        val silence = FloatArray(48_000 * 2 * 10)
        // I pochi blocchi a cavallo tra silenzio e tono superano il gate e pesano un decimo di LU.
        assertEquals(-23.0, measure(48_000, silence, sine(48_000, 1000.0, -23.0, 10.0), silence), 0.2)
    }

    @Test
    fun `i passaggi molto piu bassi vengono esclusi dal gate relativo`() {
        // 10 s a -36 dBFS (oltre 10 LU sotto) + 10 s a -23: conta solo la parte forte.
        assertEquals(-23.0, measure(48_000, sine(48_000, 1000.0, -36.0, 10.0), sine(48_000, 1000.0, -23.0, 10.0)), 0.2)
    }

    @Test
    fun `picco campione in dBFS`() {
        val meter = LoudnessMeter(48_000, 2)
        val tone = sine(48_000, 1000.0, -6.0, 1.0)
        meter.feed(tone, tone.size / 2)
        assertEquals(-6.0, meter.result().samplePeakDb, 0.05)
    }

    @Test
    fun `il volume abbassa i pezzi forti e non alza quelli deboli`() {
        assertEquals(10.0.pow(-10.0 / 20).toFloat(), VolumeLeveling.volumeFor(-8.0, null), 1e-4f)
        assertEquals(1f, VolumeLeveling.volumeFor(-18.0, null), 1e-4f)
        assertEquals(1f, VolumeLeveling.volumeFor(-24.0, null), 1e-4f)
        // Non ancora misurato: si usa la mediana della libreria.
        assertEquals(10.0.pow(-4.0 / 20).toFloat(), VolumeLeveling.volumeFor(null, -14.0), 1e-4f)
        assertEquals(1f, VolumeLeveling.volumeFor(null, null), 1e-4f)
    }

    @Test
    fun `mediana`() {
        assertNull(VolumeLeveling.median(emptyList()))
        assertEquals(-10.0, VolumeLeveling.median(listOf(-12.0, -10.0, -8.0))!!, 0.0)
        assertEquals(-11.0, VolumeLeveling.median(listOf(-14.0, -12.0, -10.0, -8.0))!!, 0.0)
    }
}
