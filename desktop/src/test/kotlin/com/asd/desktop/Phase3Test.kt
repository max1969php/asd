package com.asd.desktop

import com.asd.player.domain.model.Song
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Fase 3 con GStreamer e D-Bus veri: analisi, guadagno e silenzio nel player, MPRIS. */
class Phase3Test {

    private fun gstOk() = runCatching { GstInit.ensure() }.isSuccess

    @Test
    fun `analisi di volume e silenzio come nell'app`() {
        assumeTrue("GStreamer non disponibile", gstOk())
        val dir = Files.createTempDirectory("asd-an").toFile()
        try {
            // 1 s di silenzio, 3 s di tono 1 kHz a -23 dBFS, 1,5 s di silenzio.
            val f = wav(File(dir, "t.wav"), 48_000, listOf(0.0 to 1.0, -23.0 to 3.0, 0.0 to 1.5))
            val a = Analyzer.analyze(f.toURI().toString())
            assertEquals(-23.0, a.lufs!!, 0.5)
            assertEquals(-23.0, a.peakDb!!, 0.2)
            assertEquals(850.0, a.startMs!!.toDouble(), 30.0) // 1000 - 150 di margine
            assertEquals(4400.0, a.endMs!!.toDouble(), 30.0) // 4000 + 400 di margine
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `un file illeggibile da un errore, non si blocca`() {
        assumeTrue("GStreamer non disponibile", gstOk())
        val f = File.createTempFile("rotto", ".mp3").apply { writeText("non sono un mp3") }
        try {
            assertTrue(runCatching { Analyzer.analyze(f.toURI().toString()) }.isFailure)
        } finally {
            f.delete()
        }
    }

    @Test
    fun `il player salta il silenzio iniziale e passa avanti all'ultimo suono`() {
        val player = runCatching { Player() }.getOrNull()
        assumeTrue("GStreamer non disponibile", player != null)
        player!!
        val dir = Files.createTempDirectory("asd-skip").toFile()
        try {
            val a = wav(File(dir, "a.wav"), 44_100, listOf(0.0 to 2.0, -30.0 to 1.0, 0.0 to 3.0))
            val b = wav(File(dir, "b.wav"), 44_100, listOf(-30.0 to 1.0))
            val songs = listOf(a, b).mapIndexed { i, f -> Song(i + 1L, f.toURI().toString(), f.name, f.name, null, null, 0, null, f.length(), 0) }
            // Il primo: suono da 1,85 s a 3,4 s; guadagno +6 dB (sopra 0: limitatore acceso).
            player.adjust = { s -> if (s.id == 1L) Adjust(6.0, 1_850, 3_400) else Adjust() }
            val t0 = System.currentTimeMillis()
            player.playQueue(songs)
            assertTrue(waitFor { player.state.value.index == 0 && player.positionMs >= 1_850 })
            assertEquals(1.995, player.appliedVolume, 0.01) // +6 dB
            assertTrue("dovrebbe passare al secondo dopo ~1,5 s di suono", waitFor(6_000) { player.state.value.index == 1 })
            // Senza il salto ci sarebbero voluti 6 s.
            assertTrue(System.currentTimeMillis() - t0 < 4_500)
            assertTrue(waitFor { player.appliedVolume in 0.99..1.01 }) // il secondo: 0 dB
        } finally {
            player.shutdown()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `MPRIS risponde su D-Bus e i comandi arrivano al player`() {
        val player = runCatching { Player() }.getOrNull()
        assumeTrue("GStreamer non disponibile", player != null)
        player!!
        val mpris = Mpris(player, onRaise = {}, onQuit = {})
        assumeTrue("bus di sessione non disponibile o nome già preso", mpris.start())
        val dir = Files.createTempDirectory("asd-mpris").toFile()
        try {
            val f = wav(File(dir, "m.wav"), 44_100, listOf(-40.0 to 5.0))
            player.playQueue(listOf(Song(7, f.toURI().toString(), "m.wav", "Pezzo di prova", "ASD", null, 5000, null, f.length(), 0)))
            assertTrue(waitFor { player.state.value.playing })
            val all = gdbus("--method", "org.freedesktop.DBus.Properties.GetAll", "org.mpris.MediaPlayer2.Player")
            assertNotNull(all)
            assertTrue(all, all!!.contains("'PlaybackStatus': <'Playing'>"))
            assertTrue(all, all.contains("Pezzo di prova"))
            val root = gdbus("--method", "org.freedesktop.DBus.Properties.Get", "org.mpris.MediaPlayer2", "Identity")
            assertTrue(root ?: "", root?.contains("ASD Desktop") == true)
            gdbus("--method", "org.mpris.MediaPlayer2.Player.PlayPause")
            assertTrue(waitFor { !player.state.value.playing })
        } finally {
            mpris.close()
            player.shutdown()
            dir.deleteRecursively()
        }
    }

    private fun gdbus(vararg args: String): String? = runCatching {
        val p = ProcessBuilder(
            listOf("gdbus", "call", "--session", "--dest", Mpris.BUS_NAME, "--object-path", Mpris.PATH) + args,
        ).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(10, TimeUnit.SECONDS)
        out
    }.getOrNull()

    private fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    /** WAV stereo 16 bit a tratti: (dBFS del tono 1 kHz, 0.0 = silenzio; secondi). */
    private fun wav(file: File, rate: Int, parts: List<Pair<Double, Double>>): File {
        val samples = ArrayList<Short>()
        for ((db, seconds) in parts) {
            val amp = if (db == 0.0) 0.0 else 10.0.pow(db / 20)
            val n = (rate * seconds).toInt()
            for (i in 0 until n) {
                val v = (amp * sin(2 * PI * 1000 * i / rate) * 32767).toInt().toShort()
                samples += v
                samples += v
            }
        }
        val data = samples.size * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(rate).putInt(rate * 4).putShort(4).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        samples.forEach { b.putShort(it) }
        file.writeBytes(b.array())
        return file
    }
}
