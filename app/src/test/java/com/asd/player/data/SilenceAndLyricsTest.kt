package com.asd.player.data

import com.asd.player.data.loudness.SilenceTrim
import com.asd.player.playback.TrackDetailsReader
import org.junit.Assert.assertEquals
import org.junit.Test

class SilenceAndLyricsTest {

    @Test
    fun `silenzio lungo ai lati si salta, corto resta`() {
        assertEquals(2_850L to 180_400L, SilenceTrim.bounds(3_000, 180_000, 185_000))
        assertEquals(null to null, SilenceTrim.bounds(200, 184_800, 185_000))
        assertEquals(null to null, SilenceTrim.bounds(null, null, 185_000))
    }

    @Test
    fun `testo ID3 USLT in UTF-8 e UTF-16`() {
        val utf8 = byteArrayOf(3, 'i'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(), 0) + "Ciao\nmondo".toByteArray()
        assertEquals("Ciao\nmondo", TrackDetailsReader.decodeUslt(utf8))

        val desc = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0)
        val text = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Più".toByteArray(Charsets.UTF_16LE)
        val utf16 = byteArrayOf(1, 'i'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()) + desc + text
        assertEquals("Più", TrackDetailsReader.decodeUslt(utf16))
    }
}
