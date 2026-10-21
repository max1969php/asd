package com.asd.player.domain.model

enum class SongSource { LOCAL, DRIVE, DROPBOX }

data class Song(
    val id: Long,
    val uri: String,
    val fileName: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val mimeType: String?,
    val fileSize: Long,
    val modifiedAt: Long,
    val source: SongSource = SongSource.LOCAL,
) {
    /** Estensione in maiuscolo (MP3, WAV, ...), usata per il filtro per formato. */
    val format: String
        get() = fileName.substringAfterLast('.', "").uppercase()

    val displayArtist: String
        get() = artist?.takeIf { it.isNotBlank() } ?: UNKNOWN_ARTIST

    companion object {
        const val UNKNOWN_ARTIST = "Anonimo"
    }
}
