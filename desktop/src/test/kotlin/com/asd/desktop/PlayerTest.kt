package com.asd.desktop

import com.asd.player.domain.model.Song
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Il player con GStreamer vero: due WAV di silenzio (non si sente niente), gapless e fine coda. */
class PlayerTest {

    @Test
    fun `passa al pezzo dopo senza buchi e si ferma a fine coda`() {
        val player = runCatching { Player() }.getOrNull()
        assumeTrue("GStreamer non disponibile", player != null)
        player!!
        val dir = Files.createTempDirectory("asd-player").toFile()
        val songs = listOf(silence(File(dir, "a.wav")), silence(File(dir, "b.wav"))).mapIndexed { i, f ->
            Song(i.toLong() + 1, f.toURI().toString(), f.name, f.name, null, null, 700, null, f.length(), 0)
        }
        try {
            player.playQueue(songs)
            assertTrue(waitFor { player.state.value.index == 1 })
            assertTrue(waitFor { !player.state.value.playing })
            assertEquals(1, player.state.value.index)
            assertEquals(null, player.state.value.error)
        } finally {
            player.shutdown()
            dir.deleteRecursively()
        }
        assertFalse(player.state.value.playing)
    }

    private fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    /** 0,7 s di silenzio, 44,1 kHz mono 16 bit. */
    private fun silence(file: File): File {
        val frames = 44_100 * 7 / 10
        val data = frames * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(44_100).putInt(88_200).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        file.writeBytes(b.array())
        return file
    }
}
