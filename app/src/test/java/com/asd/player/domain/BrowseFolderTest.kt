package com.asd.player.domain

import com.asd.player.data.database.SongEntity
import com.asd.player.domain.model.LibraryQuery
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SongSource
import com.asd.player.domain.usecase.BrowseFolder
import com.asd.player.domain.usecase.FolderEntry
import com.asd.player.domain.usecase.FolderPath
import com.asd.player.domain.usecase.SearchSongs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseFolderTest {

    private val browse = BrowseFolder(SearchSongs())

    private fun song(id: Long, title: String, vararg folder: String, source: SongSource = SongSource.LOCAL) =
        Song(id, "content://x/$id", "$title.mp3", title, null, null, 0, null, 0, id, source, folder = folder.toList())

    private val flat = listOf(song(1, "Uno"), song(2, "Due"))

    private val nested = listOf(
        song(1, "Radice"),
        song(2, "Bop", "Punk"),
        song(3, "Anarchy", "Punk", "1977"),
        song(4, "Holidays", "Punk", "1977"),
        song(5, "Tanto", "Folk"),
    )

    @Test
    fun `senza sottocartelle solo i file audio`() {
        val view = browse(flat, FolderPath(), LibraryQuery())
        assertTrue(view.folders.isEmpty())
        assertEquals(listOf(2L, 1L), view.songs.map { it.id })
    }

    @Test
    fun `con sottocartelle si vedono cartelle e file del livello`() {
        val root = browse(nested, FolderPath(), LibraryQuery())
        assertEquals(listOf(FolderEntry("Folk", 1), FolderEntry("Punk", 3)), root.folders)
        assertEquals(listOf(1L), root.songs.map { it.id })
        assertEquals(5, root.below.size)

        val punk = browse(nested, FolderPath(dirs = listOf("Punk")), LibraryQuery())
        assertEquals(listOf(FolderEntry("1977", 2)), punk.folders)
        assertEquals(listOf(2L), punk.songs.map { it.id })

        val year = browse(nested, FolderPath(dirs = listOf("Punk", "1977")), LibraryQuery())
        assertTrue(year.folders.isEmpty())
        assertEquals(listOf(3L, 4L), year.songs.map { it.id })
    }

    @Test
    fun `la ricerca cerca da qui in giu senza cartelle`() {
        val view = browse(nested, FolderPath(dirs = listOf("Punk")), LibraryQuery(text = "a"))
        assertTrue(view.folders.isEmpty())
        assertEquals(listOf(3L, 4L), view.songs.map { it.id })
    }

    @Test
    fun `cartella sparita risale alla piu vicina`() {
        val view = browse(nested, FolderPath(dirs = listOf("Punk", "1978")), LibraryQuery())
        assertEquals(listOf("Punk"), view.path.dirs)
    }

    @Test
    fun `con due fonti alla radice ci sono le fonti`() {
        val songs = nested + song(9, "Cloud", "Rock", source = SongSource.DRIVE)
        val root = browse(songs, FolderPath(), LibraryQuery())
        assertEquals(listOf(SongSource.LOCAL, SongSource.DRIVE), root.folders.map { it.source })
        assertTrue(root.songs.isEmpty())

        val drive = browse(songs, FolderPath(SongSource.DRIVE), LibraryQuery())
        assertEquals(listOf(FolderEntry("Rock", 1)), drive.folders)
    }

    @Test
    fun `cartella salvata e riletta dal database`() {
        val folder = listOf("Punk", "a/b")
        assertEquals(folder, SongEntity.decodeFolder(SongEntity.encodeFolder(folder)))
        assertEquals(emptyList<String>(), SongEntity.decodeFolder(SongEntity.encodeFolder(emptyList())))
        assertEquals(emptyList<String>(), SongEntity.decodeFolder(null))
    }
}
