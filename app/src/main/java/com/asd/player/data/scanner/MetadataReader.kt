package com.asd.player.data.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

data class AudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
)

/** Legge i tag ID3/Vorbis/RIFF. Un file corrotto restituisce metadati vuoti, mai un crash. */
class MetadataReader(private val context: Context) {

    fun read(uri: Uri): AudioMetadata = extract { setDataSource(context, uri) }

    /** Per i file remoti: il retriever scarica solo i byte che servono a leggere i tag. */
    fun readRemote(url: String, headers: Map<String, String>): AudioMetadata = extract { setDataSource(url, headers) }

    private fun extract(open: MediaMetadataRetriever.() -> Unit): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.open()
            AudioMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).clean(),
                artist = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)).clean(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).clean(),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER).number(),
                discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER).number(),
            )
        } catch (e: Exception) {
            AudioMetadata(null, null, null, 0L)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    /** "3/12" → 3. */
    private fun String?.number(): Int? = this?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 }
}
