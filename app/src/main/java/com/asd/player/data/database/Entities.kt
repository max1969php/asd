package com.asd.player.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SongSource

@Entity(tableName = "songs", indices = [Index(value = ["uri"], unique = true)])
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val fileName: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val mimeType: String?,
    val fileSize: Long,
    val modifiedAt: Long,
    @ColumnInfo(defaultValue = "LOCAL") val source: String = SongSource.LOCAL.name,
    /** Loudness integrata EBU R128; null = ancora da misurare, [LOUDNESS_FAILED] = illeggibile. */
    val loudnessLufs: Double? = null,
    val peakDb: Double? = null,
) {
    fun toDomain() = Song(
        id, uri, fileName, title, artist, album, durationMs, mimeType, fileSize, modifiedAt,
        source = runCatching { SongSource.valueOf(source) }.getOrDefault(SongSource.LOCAL),
        loudnessLufs = loudnessLufs?.takeIf { it != LOUDNESS_FAILED },
    )

    companion object {
        /** Segnaposto per i file che non si riescono a decodificare: non li riproviamo a ogni avvio. */
        const val LOUDNESS_FAILED = 999.0
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Relazione molti-a-molti playlist ↔ brani. Se un brano sparisce dalla cartella
 * la voce viene rimossa in cascata; la playlist resta.
 */
@Entity(
    tableName = "playlist_entries",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("songId")],
)
data class PlaylistEntryEntity(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)

data class PlaylistSummaryRow(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(name = "songCount") val songCount: Int,
)
