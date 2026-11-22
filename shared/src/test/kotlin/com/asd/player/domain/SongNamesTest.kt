package com.asd.player.domain

import com.asd.player.domain.model.Song
import com.asd.player.domain.usecase.SongNames
import org.junit.Assert.assertEquals
import org.junit.Test

class SongNamesTest {

    private var nextId = 1L

    /** Brano senza tag: nel database il titolo è il nome del file. */
    private fun file(name: String) = name.substringBeforeLast('.').let { base ->
        Song(nextId++, "content://x/$nextId", name, base, null, null, 0, null, 0, 0)
    }

    private fun tagged(name: String, title: String, artist: String?) =
        Song(nextId++, "content://x/$nextId", name, title, artist, null, 0, null, 0, 0)

    private fun Song.names() = displayArtist to title

    @Test
    fun `numeri di catalogazione in testa`() {
        assertEquals("Gli anni", SongNames.cleanTitle("001 - Gli anni"))
        assertEquals("Gli anni", SongNames.cleanTitle("01. Gli anni"))
        assertEquals("Gli anni", SongNames.cleanTitle("1) Gli anni"))
        assertEquals("Gli anni", SongNames.cleanTitle("(03) Gli anni"))
        assertEquals("Gli anni", SongNames.cleanTitle("CD1-03 Gli anni"))
        assertEquals("Gli anni", SongNames.cleanTitle("1-03 - Gli anni"))
        assertEquals("Gli anni", SongNames.cleanTitle("07 Gli anni"))
        assertEquals("Gli anni", SongNames.cleanTitle("07_gli_anni").replaceFirstChar { it.uppercase() })
    }

    @Test
    fun `anni e nomi numerici restano`() {
        assertEquals("1979", SongNames.cleanTitle("1979"))
        assertEquals("1979 - Live", SongNames.cleanTitle("1979 - Live"))
        assertEquals("2000 miglia", SongNames.cleanTitle("2000 miglia"))
        assertEquals("99 Luftballons", SongNames.cleanTitle("99 Luftballons"))
        assertEquals("883 - Gli anni", SongNames.cleanTitle("883 - Gli anni"))
        assertEquals("2-4-6-8 Motorway", SongNames.cleanTitle("2-4-6-8 Motorway"))
        assertEquals("1.5 Grad", SongNames.cleanTitle("1.5 Grad"))
    }

    @Test
    fun `code dei download`() {
        assertEquals("Gli anni", SongNames.cleanTitle("Gli anni (Official Video)"))
        assertEquals("Gli anni", SongNames.cleanTitle("Gli anni [Official Music Video HD]"))
        assertEquals("Gli anni", SongNames.cleanTitle("Gli anni (Lyric Video) [HD]"))
        assertEquals("Gli anni", SongNames.cleanTitle("Gli anni (Video ufficiale)"))
        assertEquals("Gli anni", SongNames.cleanTitle("Gli anni (Audio)"))
        assertEquals("Gli anni (Live)", SongNames.cleanTitle("Gli anni (Live)"))
        assertEquals("Gli anni (Remastered 2011)", SongNames.cleanTitle("Gli anni (Remastered 2011)"))
    }

    @Test
    fun `artista e titolo dal nome file`() {
        val songs = SongNames.resolve(listOf(file("001 - Max Pezzali - Gli anni (Official Video).mp3")))
        assertEquals("Max Pezzali" to "Gli anni", songs.single().names())
    }

    @Test
    fun `titolo prima e artista dopo, riconosciuto da un altro brano`() {
        val songs = SongNames.resolve(
            listOf(
                file("Gli anni - Max Pezzali.mp3"),
                tagged("x.mp3", "Nord sud ovest est", "Max Pezzali"),
            )
        )
        assertEquals("Max Pezzali" to "Gli anni", songs[0].names())
    }

    @Test
    fun `artista che torna in piu nomi file`() {
        val songs = SongNames.resolve(
            listOf(
                file("Gli anni - 883.mp3"),
                file("Hanno ucciso l'uomo ragno - 883.mp3"),
            )
        )
        assertEquals("883" to "Gli anni", songs[0].names())
        assertEquals("883" to "Hanno ucciso l'uomo ragno", songs[1].names())
    }

    @Test
    fun `senza indizi vale Artista - Titolo`() {
        val song = SongNames.resolve(listOf(file("Ramones - Blitzkrieg Bop.mp3"))).single()
        assertEquals("Ramones" to "Blitzkrieg Bop", song.names())
    }

    @Test
    fun `tag veri restano, solo puliti`() {
        val song = SongNames.resolve(listOf(tagged("a.mp3", "01 - Gli anni", "Max Pezzali - Topic"))).single()
        assertEquals("Max Pezzali" to "Gli anni", song.names())
    }

    @Test
    fun `brani divisi dalle versioni precedenti si rifanno`() {
        // Fino alla 0.6 "001 - Max Pezzali - Gli anni" diventava artista "001", titolo "Max Pezzali - Gli anni".
        val old = tagged("001 - Max Pezzali - Gli anni.mp3", "Max Pezzali - Gli anni", "001")
        assertEquals("Max Pezzali" to "Gli anni", SongNames.resolve(listOf(old)).single().names())
    }
}
