package com.asd.player.data.tube

import org.json.JSONObject
import java.net.URI

object TubeLink {
    private val HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")

    /**
     * Tiene solo la parte prima di "&list": un brano, mai la playlist intera.
     * Restituisce null se il testo incollato non è un link YouTube.
     */
    fun clean(input: String): String? {
        val url = input.trim().substringBefore("&list").let { if ("://" in it) it else "https://$it" }
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return null
        return url.takeIf { host in HOSTS }
    }
}

/** Un brano scaricato da YouTube, con titolo e artista ripuliti per la libreria. */
data class TubeTrack(
    val id: String,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val ext: String,
) {
    /** Nome del file su Drive: "Artista - Titolo.m4a", senza caratteri vietati. */
    val fileName: String
        get() = listOfNotNull(artist, title).joinToString(" - ")
            .replace(FORBIDDEN, "_").trim().take(150) + ".$ext"

    val mimeType: String
        get() = if (ext == "mp3") "audio/mpeg" else "audio/mp4"

    companion object {
        private val FORBIDDEN = Regex("""[\\/:*?"<>|\x00-\x1F]""")

        /** "(Official Video)", "[Lyrics]", "(HD)"…: rumore nei titoli dei video musicali. */
        private val NOISE = Regex(
            """\s*[(\[][^)\]]*\b(official|video|videoclip|lyrics?|audio|visualizer|hd|hq|4k)\b[^)\]]*[)\]]""",
            RegexOption.IGNORE_CASE,
        )

        /** Legge la riga JSON che yt-dlp scrive con --print-to-file. */
        fun parse(json: String): TubeTrack {
            val o = JSONObject(json)
            val track = o.str("track")
            val tagArtist = o.str("artist")
            var title = track ?: o.str("title") ?: o.getString("id")
            var artist = tagArtist
            if (track == null || tagArtist == null) {
                title = title.replace(NOISE, "").trim()
                // Tipico titolo di video musicale: "Artista - Brano".
                val dash = title.indexOf(" - ")
                if (tagArtist == null && dash > 0) {
                    artist = title.substring(0, dash).trim()
                    title = title.substring(dash + 3).trim()
                } else if (artist == null) {
                    artist = o.str("uploader")?.removeSuffix(" - Topic")
                }
            }
            return TubeTrack(
                id = o.getString("id"),
                title = title,
                artist = artist,
                durationMs = (o.optDouble("duration", 0.0).takeIf { !it.isNaN() } ?: 0.0).times(1000).toLong(),
                ext = o.str("ext") ?: "m4a",
            )
        }

        // yt-dlp scrive null per i campi assenti: org.json li leggerebbe come la stringa "null".
        private fun JSONObject.str(key: String): String? =
            if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }
    }
}
