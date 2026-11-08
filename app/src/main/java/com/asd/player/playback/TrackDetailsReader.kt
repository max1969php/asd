package com.asd.player.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.flac.VorbisComment
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.asd.player.domain.model.Song
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/** Quello che c'è nel file oltre a titolo e artista: per il player (copertina, info, testo). */
class TrackDetails(
    val artwork: ByteArray?,
    val year: Int?,
    val genre: String?,
    val trackNumber: Int?,
    val totalTracks: Int?,
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val channels: Int?,
    val lyrics: String?,
)

/**
 * Legge i tag del brano che suona con gli stessi estrattori di ExoPlayer: ID3 (MP3), Vorbis
 * comment (FLAC, Ogg, Opus), atomi MP4 (M4A/AAC). Per i brani Drive passa dal resolver, come il player.
 */
@OptIn(UnstableApi::class)
class TrackDetailsReader(context: Context, resolver: CloudStreamResolver) {

    private val factory = DefaultMediaSourceFactory(
        ResolvingDataSource.Factory(DefaultDataSource.Factory(context), resolver)
    )

    /** Bloccante: da chiamare fuori dal main thread. null se il file non si legge. */
    fun read(song: Song): TrackDetails? = runCatching {
        val groups = MetadataRetriever.retrieveMetadata(factory, MediaItem.fromUri(song.uri)).get(20, TimeUnit.SECONDS)
        val format = (0 until groups.length).map { groups[it].getFormat(0) }
            .firstOrNull { MimeTypes.isAudio(it.sampleMimeType) } ?: return null
        val meta = MediaMetadata.Builder()
        var lyrics: String? = null
        var vorbisYear: Int? = null
        var vorbisGenre: String? = null
        var vorbisTrack: Int? = null
        format.metadata?.let { entries ->
            for (i in 0 until entries.length()) {
                val entry = entries[i]
                entry.populateMediaMetadata(meta)
                when (entry) {
                    // MP3: USLT è un frame binario (codifica, lingua, descrizione, testo).
                    is BinaryFrame -> if (entry.id == "USLT") lyrics = lyrics ?: decodeUslt(entry.data)
                    // M4A: ©lyr arriva come frame di testo "USLT".
                    is TextInformationFrame -> if (entry.id == "USLT") lyrics = lyrics ?: entry.values.joinToString("\n")
                    is VorbisComment -> when (entry.key.uppercase()) {
                        "LYRICS", "UNSYNCEDLYRICS" -> lyrics = lyrics ?: entry.value
                        "DATE", "YEAR" -> vorbisYear = vorbisYear ?: entry.value.take(4).toIntOrNull()
                        "GENRE" -> vorbisGenre = vorbisGenre ?: entry.value
                        "TRACKNUMBER" -> vorbisTrack = vorbisTrack ?: entry.value.substringBefore('/').trim().toIntOrNull()
                    }
                }
            }
        }
        val m = meta.build()
        TrackDetails(
            artwork = m.artworkData,
            year = m.recordingYear ?: m.releaseYear ?: vorbisYear,
            genre = m.genre?.toString()?.takeIf { it.isNotBlank() } ?: vorbisGenre,
            trackNumber = m.trackNumber ?: vorbisTrack ?: song.trackNumber,
            totalTracks = m.totalTrackCount,
            mimeType = format.sampleMimeType,
            // Molti MP3 non dichiarano il bitrate: dalla dimensione e dalla durata.
            bitrate = listOf(format.averageBitrate, format.bitrate).firstOrNull { it != Format.NO_VALUE && it > 0 }
                ?: song.fileSize.takeIf { it > 0 && song.durationMs > 0 }?.let { (it * 8_000 / song.durationMs).toInt() },
            sampleRate = format.sampleRate.takeIf { it != Format.NO_VALUE },
            channels = format.channelCount.takeIf { it != Format.NO_VALUE },
            lyrics = lyrics?.trim()?.takeIf { it.isNotEmpty() },
        )
    }.getOrNull()

    companion object {
        /** Frame ID3 USLT: [codifica][lingua x3][descrizione]\0[testo]. */
        fun decodeUslt(data: ByteArray): String? {
            if (data.size < 5) return null
            val (charset, terminator) = when (data[0].toInt()) {
                0 -> Charsets.ISO_8859_1 to 1
                1 -> Charsets.UTF_16 to 2
                2 -> Charsets.UTF_16BE to 2
                else -> Charsets.UTF_8 to 1
            }
            var i = 4
            // Salta la descrizione fino al terminatore (allineato a 2 byte per UTF-16).
            while (i + terminator <= data.size) {
                if ((0 until terminator).all { data[i + it] == 0.toByte() }) break
                i += terminator
            }
            val start = i + terminator
            if (start >= data.size) return null
            var text = String(data, start, data.size - start, charset(charset, data, start, terminator))
            text = text.trimEnd('\u0000')
            return text.takeIf { it.isNotBlank() }
        }

        /** UTF-16 con BOM: il BOM del testo può essere diverso da quello della descrizione. */
        private fun charset(base: Charset, data: ByteArray, start: Int, terminator: Int): Charset =
            if (terminator == 2 && base == Charsets.UTF_16 && start + 1 < data.size) {
                when {
                    data[start] == 0xFF.toByte() && data[start + 1] == 0xFE.toByte() -> Charsets.UTF_16
                    data[start] == 0xFE.toByte() && data[start + 1] == 0xFF.toByte() -> Charsets.UTF_16
                    else -> Charsets.UTF_16LE
                }
            } else {
                base
            }
    }
}
