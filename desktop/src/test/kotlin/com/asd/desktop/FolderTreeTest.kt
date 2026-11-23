package com.asd.desktop

import com.asd.desktop.ui.FolderTrees
import com.asd.player.domain.model.Song
import com.asd.player.domain.usecase.LibraryView
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderTreeTest {
    private fun song(id: Long, folder: List<String>, artist: String? = null, album: String? = null) =
        Song(id, "file:/$id", "$id.mp3", "t$id", artist, album, 0, null, 0, 0, folder = folder)

    private val songs = listOf(
        song(1, listOf("Punk", "Ramones", "1976"), "Ramones", "Ramones"),
        song(2, listOf("Punk", "Ramones", "1977"), "RAMONES", "Rocket to Russia"),
        song(3, listOf("punk", "Clash"), "The Clash", "London Calling"),
        song(4, listOf("Italiani"), "Nino Manfredi", null),
        song(5, emptyList(), null, null),
    )

    @Test
    fun `cartelle annidate con i conteggi, maiuscole accorpate`() {
        val roots = FolderTrees.build(songs, LibraryView.FOLDERS)
        assertEquals(listOf("Italiani" to 1, "Punk" to 3), roots.map { it.name to it.count })
        val punk = roots[1]
        assertEquals(listOf("Clash", "Ramones"), punk.children.map { it.name })
        assertEquals(listOf("1976", "1977"), punk.children[1].children.map { it.name })
        assertEquals(listOf("Punk", "Ramones", "1977"), punk.children[1].children[1].path)
    }

    @Test
    fun `solo i rami aperti sono visibili`() {
        val roots = FolderTrees.build(songs, LibraryView.FOLDERS)
        assertEquals(listOf("Italiani", "Punk"), FolderTrees.visible(roots, emptySet()).map { it.first.name })
        val open = setOf(FolderTrees.key(listOf("Punk")), FolderTrees.key(listOf("Punk", "Ramones")))
        assertEquals(
            listOf("Italiani" to 0, "Punk" to 0, "Clash" to 1, "Ramones" to 1, "1976" to 2, "1977" to 2),
            FolderTrees.visible(roots, open).map { it.first.name to it.second },
        )
    }

    @Test
    fun `artisti con i loro album`() {
        Song.unknownArtist = "Anonimo"
        val roots = FolderTrees.build(songs, LibraryView.ARTISTS)
        assertEquals(listOf("Anonimo", "Nino Manfredi", "Ramones", "The Clash"), roots.map { it.name })
        assertEquals(listOf("Ramones", "Rocket to Russia"), roots[2].children.map { it.name })
    }
}
