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

/** Quello che si vede in INFO e TESTO: tutto facoltativo. */
data class SongDetails(
    val year: String? = null,
    val genre: String? = null,
    val track: String? = null,
    val format: String? = null,
    val bitrateKbps: Long? = null,
    val sampleRateHz: Int? = null,
    val cover: ByteArray? = null,
    val lyrics: String? = null,
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

    /** Dettagli per INFO/TESTO: si leggono quando servono, non durante la scansione. */
    fun details(file: File): SongDetails = runCatching {
        val audio = AudioFileIO.read(file)
        val tag = audio.tag
        val header = audio.audioHeader
        fun field(key: FieldKey): String? = runCatching { tag?.getFirst(key) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val track = field(FieldKey.TRACK)?.let { n -> field(FieldKey.TRACK_TOTAL)?.let { "$n/$it" } ?: n }
        SongDetails(
            year = field(FieldKey.YEAR),
            genre = field(FieldKey.GENRE),
            track = track,
            format = runCatching { header.format }.getOrNull()?.takeIf { it.isNotBlank() },
            bitrateKbps = runCatching { header.bitRateAsNumber }.getOrNull()?.takeIf { it > 0 },
            sampleRateHz = runCatching { header.sampleRateAsNumber }.getOrNull()?.takeIf { it > 0 },
            cover = runCatching { tag?.firstArtwork?.binaryData }.getOrNull()?.takeIf { it.isNotEmpty() },
            lyrics = field(FieldKey.LYRICS),
        )
    }.getOrDefault(SongDetails())

    /** "3/12" → 3, "03" → 3. */
    internal fun leadingNumber(value: String): Int? = value.trim().takeWhile { it.isDigit() }.toIntOrNull()?.takeIf { it > 0 }
}
