package com.asd.desktop

import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

/** I tag di un file: quello che manca resta null (il titolo allora viene dal nome del file). */
data class Tags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
)

/** Legge i tag con jaudiotagger (MP3, FLAC, OGG, M4A, WAV). Un file illeggibile dà tag vuoti, mai un errore. */
object TagReader {

    init {
        // jaudiotagger scrive molto su java.util.logging: per un lettore musicale è solo rumore.
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    fun read(file: File): Tags = runCatching {
        val audio = AudioFileIO.read(file)
        val tag = audio.tag
        fun field(key: FieldKey): String? = runCatching { tag?.getFirst(key) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        Tags(
            title = field(FieldKey.TITLE),
            artist = field(FieldKey.ARTIST) ?: field(FieldKey.ALBUM_ARTIST),
            album = field(FieldKey.ALBUM),
            durationMs = runCatching { (audio.audioHeader.preciseTrackLength * 1000).toLong() }.getOrDefault(0L),
            trackNumber = field(FieldKey.TRACK)?.let(::leadingNumber),
            discNumber = field(FieldKey.DISC_NO)?.let(::leadingNumber),
        )
    }.getOrDefault(Tags())

    /** "3/12" → 3, "03" → 3. */
    internal fun leadingNumber(value: String): Int? = value.trim().takeWhile { it.isDigit() }.toIntOrNull()?.takeIf { it > 0 }
}
