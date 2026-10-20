package com.asd.player.domain

import com.asd.player.domain.model.LibraryQuery
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SortField
import com.asd.player.domain.model.SortOrder
import com.asd.player.domain.usecase.SearchSongs
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSongsTest {

    private val search = SearchSongs()

    private fun song(id: Long, title: String, artist: String?, album: String? = null, file: String = "$title.mp3", duration: Long = 0) =
        Song(id, "content://x/$id", file, title, artist, album, duration, null, 0, id)

    private val songs = listOf(
        song(1, "Anarchy in the UK", "Sex Pistols", "Never Mind the Bollocks", duration = 210_000),
        song(2, "Blitzkrieg Bop", "Ramones", "Ramones", file = "blitz.wav", duration = 132_000),
        song(3, "Ça plane pour moi", "Plastic Bertrand", duration = 180_000),
        song(4, "traccia_live_03", null, file = "traccia_live_03.mp3", duration = 300_000),
        song(5, "Holidays in the Sun", "Sex Pistols", "Never Mind the Bollocks", file = "holidays.flac"),
    )

    @Test
    fun `testo cerca in titolo, artista, album e nome file`() {
        assertEquals(listOf(1L, 5L), search(songs, LibraryQuery(text = "pistols")).map { it.id })
        assertEquals(listOf(1L, 5L), search(songs, LibraryQuery(text = "bollocks")).map { it.id })
        assertEquals(listOf(2L), search(songs, LibraryQuery(text = "BLITZ")).map { it.id })
        assertEquals(listOf(4L), search(songs, LibraryQuery(text = "live")).map { it.id })
    }

    @Test
    fun `piu parole devono comparire tutte`() {
        assertEquals(listOf(5L), search(songs, LibraryQuery(text = "sun pistols")).map { it.id })
    }

    @Test
    fun `filtri e testo si combinano`() {
        val q = LibraryQuery(text = "the", formats = setOf("FLAC"), artist = "Sex Pistols")
        assertEquals(listOf(5L), search(songs, q).map { it.id })
    }

    @Test
    fun `brani senza artista finiscono sotto Anonimo`() {
        val q = LibraryQuery(artist = Song.UNKNOWN_ARTIST)
        assertEquals(listOf(4L), search(songs, q).map { it.id })
    }

    @Test
    fun `ordinamento per titolo ignora maiuscole e accenti`() {
        val result = search(songs, LibraryQuery(sort = SortOrder(SortField.TITLE))).map { it.id }
        assertEquals(listOf(1L, 2L, 3L, 5L, 4L), result)
    }

    @Test
    fun `ordinamento per durata decrescente`() {
        val result = search(songs, LibraryQuery(sort = SortOrder(SortField.DURATION, ascending = false))).map { it.id }
        assertEquals(listOf(4L, 1L, 3L, 2L, 5L), result)
    }
}
