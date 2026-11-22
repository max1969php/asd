package com.asd.desktop

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fase 4: lingue dagli strings.xml dell'app, mixtape, timer. */
class Phase4Test {

    @After
    fun italian() = I18n.use("it")

    @Test
    fun `le lingue arrivano dagli strings xml dell'app, con i testi solo desktop`() {
        I18n.use("it")
        assertEquals("Roba", Texts.TAB_LIBRARY)
        assertEquals("ROBA: INDICIZZO 3/10…", Texts.scanIndexing(3, 10))
        assertEquals("misuro il volume: ne mancano 4", Texts.measuring(4))
        assertEquals("1 pezzo", Texts.songs(1))
        // Dalla minimal: senza la frase su Drive.
        assertFalse(Texts.VOLUME_HINT.contains("Drive"))
        assertTrue(Texts.burnMessage("x").contains("\"x\""))

        I18n.use("en")
        assertEquals("Stuff", Texts.TAB_LIBRARY)
        assertEquals("No folder chosen.", Texts.NO_FOLDER)

        // Tutte le lingue: le chiavi dell'app e quelle solo desktop.
        for (tag in I18n.SUPPORTED) {
            I18n.use(tag)
            assertNotEquals(tag, "tab_library", Texts.TAB_LIBRARY)
            assertNotEquals(tag, "volume_title", Texts.VOLUME_TITLE)
            assertNotEquals(tag, "desktop_no_folder", Texts.NO_FOLDER)
            if (tag != "en") assertNotEquals(tag, "No folder chosen.", Texts.NO_FOLDER)
            assertTrue(tag, Texts.PLAYLIST_EMPTY.contains(Texts.TAB_LIBRARY.uppercase()))
            // In arabo e persiano le cifre sono quelle locali, come su Android.
            assertTrue(tag, Texts.measuring(5).let { "5" in it || "٥" in it || "۵" in it })
            assertEquals(tag == "ar" || tag == "fa", I18n.isRtl)
        }
        I18n.use("de")
        assertNotEquals("Stuff", Texts.TAB_LIBRARY)
    }

    @Test
    fun `sequenze di escape di Android`() {
        assertEquals("l'altro \"x\"\nok @", I18n.unescape("l\\'altro \\\"x\\\"\\nok \\@"))
        assertEquals("tra virgolette", I18n.unescape("\"tra virgolette\""))
    }

    @Test
    fun `mixtape creati, riempiti, riordinati e ritrovati dopo il riavvio`() {
        val dir = Files.createTempDirectory("asd-mix").toFile()
        try {
            val file = File(dir, "playlists.json")
            val m = Mixtapes(file)
            val a = m.create("roba da corriera", listOf("file:/a", "file:/b"))
            val b = m.create("vuoto")
            m.add(a, listOf("file:/b", "file:/c")) // b c'è già
            assertEquals(listOf("file:/a", "file:/b", "file:/c"), m.all.value.first { it.id == a }.uris)
            m.move(a, 2, 0)
            m.remove(a, 1)
            m.rename(b, "  pieno  ")
            val again = Mixtapes(file).all.value
            assertEquals(listOf("file:/c", "file:/b"), again.first { it.id == a }.uris)
            assertEquals("pieno", again.first { it.id == b }.name)
            // Il più recente in cima.
            assertEquals(b, again.first().id)
            m.delete(a)
            assertEquals(listOf(b), Mixtapes(file).all.value.map { it.id })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `timer a fine pezzo e in minuti`() {
        val player = runCatching { Player() }.getOrNull() ?: return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sleep = SleepTimer(player, scope)
            sleep.atEndOfSong()
            assertEquals(SleepState.EndOfSong, sleep.state.value)
            assertTrue(player.stopAtEnd)
            sleep.inMinutes(15)
            assertTrue(sleep.state.value is SleepState.At)
            assertFalse(player.stopAtEnd)
            assertTrue(sleep.leftMs()!! in 14 * 60_000L..15 * 60_000L)
            sleep.cancel()
            assertEquals(SleepState.Off, sleep.state.value)
        } finally {
            scope.cancel()
            player.shutdown()
        }
    }
}
