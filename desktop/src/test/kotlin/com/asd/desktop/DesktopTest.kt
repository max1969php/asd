package com.asd.desktop

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopTest {

    @Test
    fun `numero di traccia e di disco dai tag`() {
        assertEquals(3, TagReader.leadingNumber("3/12"))
        assertEquals(7, TagReader.leadingNumber("07"))
        assertNull(TagReader.leadingNumber(""))
        assertNull(TagReader.leadingNumber("0"))
        assertNull(TagReader.leadingNumber("A1"))
    }

    @Test
    fun `solo i formati di ASD, niente file nascosti`() {
        assertTrue(Library.isSupported("Ramones - Blitzkrieg Bop.MP3"))
        assertTrue(Library.isSupported("x.opus"))
        assertFalse(Library.isSupported(".nascosto.mp3"))
        assertFalse(Library.isSupported("copertina.jpg"))
        assertFalse(Library.isSupported("senza estensione"))
    }

    @Test
    fun `scansione incrementale con cartelle, nomi dal file e cache`(): Unit = runBlocking {
        val root = Files.createTempDirectory("asd-music").toFile()
        val cache = File(Files.createTempDirectory("asd-cache").toFile(), "library.json")
        File(root, "Punk/77").mkdirs()
        File(root, "Punk/77/Ramones - Blitzkrieg Bop.mp3").writeText("non audio: tag vuoti")
        File(root, "01 - Sex Pistols - Anarchy.flac").writeText("x")
        File(root, ".nascosta").mkdirs()
        File(root, ".nascosta/no.mp3").writeText("x")

        val library = Library(cache)
        library.rescan(root.path)
        val songs = library.songs.value.sortedBy { it.fileName }
        assertEquals(2, songs.size)
        val pistols = songs.first { it.fileName.startsWith("01") }
        assertEquals(emptyList<String>(), pistols.folder)
        val ramones = songs.first { it.fileName.startsWith("Ramones") }
        assertEquals(listOf("Punk", "77"), ramones.folder)
        assertEquals("Blitzkrieg Bop", ramones.title)
        assertEquals("Ramones", ramones.artist)
        assertEquals(ScanState.Done(2, 0, 0), library.scan.value)

        // Un file sparito e uno nuovo; il catalogo salvato si ricarica uguale.
        File(root, "01 - Sex Pistols - Anarchy.flac").delete()
        File(root, "Clash - London Calling.ogg").writeText("x")
        library.rescan(root.path)
        assertEquals(ScanState.Done(1, 0, 1), library.scan.value)
        val reloaded = Library(cache).also { it.load(root.path) }
        assertEquals(library.songs.value.map { it.uri to it.id }.toSet(), reloaded.songs.value.map { it.uri to it.id }.toSet())

        root.deleteRecursively()
        cache.parentFile.deleteRecursively()
    }
}
