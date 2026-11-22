package com.asd.player.playback

import com.asd.player.domain.model.SongSource
import com.asd.player.domain.usecase.FolderPath
import com.asd.player.domain.usecase.LibraryView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Gli id dei nodi di Android Auto: andata e ritorno senza perdere niente. */
class CarLibraryTest {

    @Test
    fun `cartelle con nomi qualsiasi, fonti e viste`() {
        val paths = listOf(
            FolderPath(),
            FolderPath(view = LibraryView.ARTISTS),
            FolderPath(SongSource.DRIVE, listOf("Punk"), LibraryView.FOLDERS),
            FolderPath(null, listOf("AC/DC", "Back in | Black", "100% ☠", "a\u001Fb", ""), LibraryView.FOLDERS),
            FolderPath(null, listOf("Ramones", "Rocket to Russia"), LibraryView.ARTISTS),
        )
        for (p in paths) assertEquals(p, CarLibrary.pathOf(CarLibrary.node(p)))
    }

    @Test
    fun `id che non sono cartelle`() {
        assertNull(CarLibrary.pathOf("root"))
        assertNull(CarLibrary.pathOf("p|3"))
        assertNull(CarLibrary.pathOf("v|NIENTE||"))
    }
}
