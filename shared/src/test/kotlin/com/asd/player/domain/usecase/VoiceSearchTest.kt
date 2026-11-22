package com.asd.player.domain.usecase

import com.asd.player.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSearchTest {

    private fun song(id: Long, title: String, artist: String?, album: String? = null, track: Int? = null) =
        Song(id, "file:/$id.mp3", "$id.mp3", title, artist, album, 180_000, null, 1000, 0, trackNumber = track)

    private val songs = listOf(
        song(1, "Blitzkrieg Bop", "Ramones", "Ramones", 1),
        song(2, "Judy Is a Punk", "Ramones", "Ramones", 3),
        song(3, "Beat on the Brat", "Ramones", "Ramones", 2),
        song(4, "London Calling", "The Clash", "London Calling", 1),
        song(5, "Tanto pè cantà", "Nino Manfredi", null),
        song(6, "Ramones", "Motörhead", "1916"),
    )
    private val voice = VoiceSearch()

    @Test
    fun `artista intero in ordine di album e traccia`() {
        assertEquals(listOf(1L, 3L, 2L), voice.find("ramones", songs).map { it.id })
        assertEquals(listOf(4L), voice.find("the clash", songs).map { it.id })
    }

    @Test
    fun `album, titolo, titolo con artista`() {
        assertEquals(listOf(4L), voice.find("London Calling", songs).map { it.id })
        assertEquals(listOf(1L), voice.find("blitzkrieg bop", songs).map { it.id })
        assertEquals(listOf(1L), voice.find("Blitzkrieg Bop dei Ramones", songs).map { it.id })
        assertEquals(listOf(1L), voice.find("blitzkrieg bop by ramones", songs).map { it.id })
        assertEquals(listOf(5L), voice.find("tanto pe canta", songs).map { it.id })
    }

    @Test
    fun `campi separati dall'assistente`() {
        assertEquals(listOf(6L), voice.find("ramones motorhead", songs, title = "Ramones", artist = "Motörhead").map { it.id })
        assertEquals(listOf(1L, 3L, 2L), voice.find("", songs, artist = "Ramones").map { it.id })
        assertEquals(listOf(4L), voice.find("", songs, album = "london calling").map { it.id })
    }

    @Test
    fun `mixtape per nome, anche con la parola mixtape`() {
        val tapes = listOf(VoiceSearch.Tape("Roba da corriera", listOf(songs[3], songs[0])))
        assertEquals(listOf(4L, 1L), voice.find("roba da corriera", songs, tapes).map { it.id })
        assertEquals(listOf(4L, 1L), voice.find("mixtape roba da corriera", songs, tapes).map { it.id })
    }

    @Test
    fun `niente trovato`() {
        assertTrue(voice.find("vasco rossi", songs).isEmpty())
        assertTrue(voice.find("", songs).isEmpty())
    }
}
