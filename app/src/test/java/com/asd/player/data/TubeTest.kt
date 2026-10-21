package com.asd.player.data

import com.asd.player.data.tube.TubeCookies
import com.asd.player.data.tube.TubeLink
import com.asd.player.data.tube.TubeTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TubeTest {

    @Test
    fun `il link perde tutto da list in poi`() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            TubeLink.clean("  https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123&index=4 "),
        )
        assertEquals("https://youtu.be/dQw4w9WgXcQ?si=abc", TubeLink.clean("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("https://music.youtube.com/watch?v=x1", TubeLink.clean("music.youtube.com/watch?v=x1&list=RDAMVM"))
    }

    @Test
    fun `i link che non sono YouTube vengono rifiutati`() {
        assertNull(TubeLink.clean("https://vimeo.com/123"))
        assertNull(TubeLink.clean("ciao come va"))
        assertNull(TubeLink.clean(""))
    }

    @Test
    fun `i tag di YouTube Music vincono sul titolo del video`() {
        val track = TubeTrack.parse(
            """{"id":"a1","title":"Song (Official Video)","track":"Song","artist":"Band","uploader":"Band - Topic","duration":215.5,"ext":"m4a"}"""
        )
        assertEquals(TubeTrack("a1", "Song", "Band", 215_500, "m4a"), track)
        assertEquals("Band - Song.m4a", track.fileName)
        assertEquals("audio/mp4", track.mimeType)
    }

    @Test
    fun `senza tag, artista e titolo si ricavano da Artista - Titolo`() {
        val track = TubeTrack.parse(
            """{"id":"b2","title":"CCCP - Curami [Official Video HD]","track":null,"artist":null,"uploader":"Canale Qualsiasi","duration":180,"ext":"m4a"}"""
        )
        assertEquals("Curami", track.title)
        assertEquals("CCCP", track.artist)
    }

    @Test
    fun `senza trattino l'artista è il canale, senza Topic`() {
        val track = TubeTrack.parse(
            """{"id":"c3","title":"Demo nel garage (lyrics)","track":null,"artist":null,"uploader":"Garage Band - Topic","duration":null,"ext":"m4a"}"""
        )
        assertEquals("Demo nel garage", track.title)
        assertEquals("Garage Band", track.artist)
        assertEquals(0L, track.durationMs)
    }

    @Test
    fun `il nome file non contiene caratteri vietati`() {
        val track = TubeTrack("d4", "Che fai? / A/B: \"live\"", "AC/DC", 0, "m4a")
        assertEquals("AC_DC - Che fai_ _ A_B_ _live_.m4a", track.fileName)
    }

    @Test
    fun `i cookie della WebView diventano un cookies txt valido`() {
        val text = TubeCookies.toNetscape("PREF=f6=40; SAPISID=abc/def; __Secure-3PSID=x=y", now = 0)
        val lines = text.lines().filter { it.isNotEmpty() }
        assertEquals("# Netscape HTTP Cookie File", lines[0])
        assertEquals(".youtube.com\tTRUE\t/\tTRUE\t31536000\tPREF\tf6=40", lines[1])
        assertEquals(".youtube.com\tTRUE\t/\tTRUE\t31536000\t__Secure-3PSID\tx=y", lines[3])
        assertTrue(TubeCookies.isNetscapeFile(text))
        assertTrue(TubeCookies.isLoggedIn(text))
    }

    @Test
    fun `senza i cookie di accesso non si è dentro YouTube`() {
        assertFalse(TubeCookies.isLoggedIn("PREF=f6=40; VISITOR_INFO1_LIVE=abc; YSC=x"))
        assertFalse(TubeCookies.isLoggedIn("NOTSAPISID=1"))
        assertTrue(TubeCookies.isLoggedIn("YSC=x; SAPISID=1"))
        assertFalse(TubeCookies.isNetscapeFile("name=value"))
        assertTrue(TubeCookies.isNetscapeFile("\uFEFF# Netscape HTTP Cookie File\n.youtube.com\tTRUE"))
    }
}
