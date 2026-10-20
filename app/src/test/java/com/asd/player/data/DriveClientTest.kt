package com.asd.player.data

import com.asd.player.data.drive.DriveClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveClientTest {

    @Test
    fun `interpreta una pagina della Drive API`() {
        val json = """
            {
              "nextPageToken": "abc",
              "files": [
                {"id": "F1", "name": "Punk", "mimeType": "application/vnd.google-apps.folder",
                 "modifiedTime": "2026-01-02T03:04:05.000Z"},
                {"id": "S1", "name": "pezzo.mp3", "mimeType": "audio/mpeg", "size": "4096",
                 "modifiedTime": "2026-01-02T03:04:05.000Z"}
              ]
            }
        """.trimIndent()
        val (files, next) = DriveClient.parseFileList(json)
        assertEquals("abc", next)
        assertEquals(2, files.size)
        assertTrue(files[0].isFolder)
        assertEquals(4096L, files[1].size)
        assertEquals(1767323045000L, files[1].modifiedAt)
    }

    @Test
    fun `ultima pagina senza token e senza size`() {
        val (files, next) = DriveClient.parseFileList("""{"files":[{"id":"X","name":"a.wav","mimeType":"audio/wav"}]}""")
        assertNull(next)
        assertEquals(0L, files.single().size)
        assertEquals(0L, files.single().modifiedAt)
    }

    @Test
    fun `uri interni gdrive`() {
        assertEquals("gdrive://file/1AbC_x-Y", DriveClient.uriFor("1AbC_x-Y"))
    }
}
