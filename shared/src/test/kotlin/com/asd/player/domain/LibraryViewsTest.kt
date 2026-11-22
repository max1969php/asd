package com.asd.player.domain

import com.asd.player.domain.model.LibraryQuery
import com.asd.player.domain.model.Song
import com.asd.player.domain.usecase.BrowseFolder
import com.asd.player.domain.usecase.Duplicates
import com.asd.player.domain.usecase.FolderEntry
import com.asd.player.domain.usecase.FolderPath
import com.asd.player.domain.usecase.LibraryView
import com.asd.player.domain.usecase.SearchSongs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryViewsTest {

    private val browse = BrowseFolder(SearchSongs())

    private fun song(
        id: Long, title: String, artist: String?, album: String? = null, track: Int? = null,
        duration: Long = id * 10_000, size: Long = id * 100_000, vararg folder: String,
    ) = Song(
        id, "content://x/$id", "$title.mp3", title, artist, album, duration, null, size, id,
        folder = folder.toList(), trackNumber = track,
    )

    private val songs = listOf(
        song(1, "Blitzkrieg Bop", "Ramones", "Ramones", track = 1),
        song(2, "Judy Is a Punk", "RAMONES", "Ramones", track = 3),
        song(3, "Beat on the Brat", "Ramones", "Ramones", track = 2),
        song(4, "Rockaway Beach", "Ramones", null),
        song(5, "Anarchy in the UK", "Sex Pistols", "Never Mind the Bollocks"),
    )

    @Test
    fun `artisti, poi album, poi pezzi in ordine di traccia`() {
        val root = browse(songs, FolderPath(view = LibraryView.ARTISTS), LibraryQuery())
        assertEquals(listOf(FolderEntry("Ramones", 4), FolderEntry("Sex Pistols", 1)), root.folders)

        val ramones = browse(songs, FolderPath(dirs = listOf("Ramones"), view = LibraryView.ARTISTS), LibraryQuery())
        assertEquals(listOf(FolderEntry("Ramones", 3)), ramones.folders)
        assertEquals(listOf(4L), ramones.songs.map { it.id })

        val album = browse(songs, FolderPath(dirs = listOf("Ramones", "Ramones"), view = LibraryView.ARTISTS), LibraryQuery())
        assertEquals(listOf(1L, 3L, 2L), album.songs.map { it.id })
    }

    @Test
    fun `vista album, i pezzi senza album alla radice`() {
        val root = browse(songs, FolderPath(view = LibraryView.ALBUMS), LibraryQuery())
        assertEquals(listOf("Never Mind the Bollocks", "Ramones"), root.folders.map { it.name })
        assertEquals(listOf(4L), root.songs.map { it.id })
    }

    @Test
    fun `doppioni per nome o per durata e dimensione`() {
        val list = listOf(
            song(1, "Gli anni", "883"),
            song(2, "GLI ANNI", "883"),
            song(3, "Altro titolo", "X", duration = 200_000, size = 5_000_000),
            song(4, "Copia", "Y", duration = 200_500, size = 5_050_000),
            song(5, "Unico", "Z", duration = 300_000, size = 9_000_000),
        )
        val groups = Duplicates.groups(list).map { g -> g.map { it.id }.sorted() }.sortedBy { it.first() }
        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L)), groups)

        val view = browse(list, FolderPath(), LibraryQuery(duplicates = true))
        assertTrue(view.folders.isEmpty())
        assertEquals(setOf(1L, 2L, 3L, 4L), view.songs.map { it.id }.toSet())
    }
}
