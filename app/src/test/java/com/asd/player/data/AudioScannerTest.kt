package com.asd.player.data

import com.asd.player.data.scanner.AudioScanner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioScannerTest {

    @Test
    fun `accetta solo i formati della whitelist`() {
        assertTrue(AudioScanner.isSupported("pezzo.mp3"))
        assertTrue(AudioScanner.isSupported("DEMO.WAV"))
        assertTrue(AudioScanner.isSupported("live.flac"))
        assertFalse(AudioScanner.isSupported("video.mp4"))
        assertFalse(AudioScanner.isSupported("copertina.jpg"))
        assertFalse(AudioScanner.isSupported("senza_estensione"))
    }

    @Test
    fun `ignora i file nascosti`() {
        assertFalse(AudioScanner.isSupported("._pezzo.mp3"))
    }
}
