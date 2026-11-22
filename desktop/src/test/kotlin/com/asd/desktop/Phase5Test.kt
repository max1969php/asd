package com.asd.desktop

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fase 5: il TUBO con un finto yt-dlp (stessi argomenti e stesso output di quello vero, senza rete). */
class Phase5Test {

    private fun fakeYtdlp(dir: File): File {
        val script = File(dir, "yt-dlp")
        script.writeText(
            """
            #!/bin/sh
            [ "${'$'}1" = "--version" ] && { echo 2099.01.01; exit 0; }
            for a in "${'$'}@"; do url="${'$'}a"; done
            case "${'$'}url" in *bot*) echo "ERROR: [youtube] abc: Sign in to confirm you're not a bot"; exit 1;; esac
            while [ ${'$'}# -gt 0 ]; do
              case "${'$'}1" in
                -o) out="${'$'}2"; shift;;
                --print-to-file) meta="${'$'}3"; shift 2;;
              esac
              shift
            done
            echo "ASD   10.0%"; echo "ASD  55.5%"; echo "ASD 100.0%"
            f=${'$'}(echo "${'$'}out" | sed 's/%(id)s/abc123/; s/%(ext)s/m4a/')
            echo audio > "${'$'}f"
            echo '{"id": "abc123", "title": "Ramones - Blitzkrieg Bop (Official Video)", "track": null, "artist": null, "uploader": "Ramones", "duration": 132.0, "ext": "m4a"}' > "${'$'}meta"
            """.trimIndent() + "\n",
        )
        script.setExecutable(true)
        return script
    }

    @Test
    fun `scarica in TUBO, mai sopra un file che c'è, errori come nell'app`() {
        val dir = Files.createTempDirectory("asd-tube").toFile()
        try {
            val music = File(dir, "music").apply { mkdirs() }
            val settings = SettingsStore(File(dir, "settings.json")).apply { update { it.copy(folder = music.path) } }
            var saved = 0
            val exe = fakeYtdlp(dir)
            val tube = Tube(settings, onSaved = { saved++ }, cache = File(dir, "cache"), locate = { exe })
            assertEquals("2099.01.01", tube.version())
            assertFalse(tube.add("https://example.com/video"))
            assertTrue(tube.add("https://youtu.be/abc123?si=x"))
            assertTrue(tube.add("https://www.youtube.com/watch?v=abc123&list=PL1"))
            assertTrue(tube.add("https://youtu.be/bot"))
            val end = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < end && tube.jobs.value.any { it.state !is TubeJob.State.Done && it.state !is TubeJob.State.Failed }) Thread.sleep(50)
            val states = tube.jobs.value.reversed().map { it.state }
            assertEquals(TubeJob.State.Done("Ramones - Blitzkrieg Bop.m4a"), states[0])
            assertEquals(TubeJob.State.Done("Ramones - Blitzkrieg Bop (2).m4a"), states[1])
            assertEquals(TubeJob.State.Failed(Texts.YT_BOT), states[2])
            assertEquals(setOf("Ramones - Blitzkrieg Bop.m4a", "Ramones - Blitzkrieg Bop (2).m4a"), File(music, "TUBO").list()!!.toSet())
            assertEquals(2, saved)
            assertTrue(File(dir, "cache").list().isNullOrEmpty())
            tube.cleanUp()
            assertTrue(tube.jobs.value.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `senza yt-dlp il TUBO lo dice`() {
        val dir = Files.createTempDirectory("asd-tube0").toFile()
        try {
            val tube = Tube(SettingsStore(File(dir, "s.json")), onSaved = {}, cache = File(dir, "c"), locate = { null })
            assertEquals(null, tube.ytdlp.value)
            assertEquals(null, tube.version())
        } finally {
            dir.deleteRecursively()
        }
    }
}
