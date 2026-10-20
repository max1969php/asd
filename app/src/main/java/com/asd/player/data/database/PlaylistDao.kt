package com.asd.player.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, p.createdAt, p.updatedAt, COUNT(e.songId) AS songCount
        FROM playlists p LEFT JOIN playlist_entries e ON e.playlistId = p.id
        GROUP BY p.id ORDER BY p.updatedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<PlaylistSummaryRow>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query(
        """
        SELECT s.* FROM playlist_entries e JOIN songs s ON s.id = e.songId
        WHERE e.playlistId = :playlistId ORDER BY e.position
        """
    )
    fun observeSongs(playlistId: Long): Flow<List<SongEntity>>

    @Query("SELECT songId FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getSongIds(playlistId: Long): List<Long>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun clearEntries(playlistId: Long)

    @Insert
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    /** Riscrive l'intera sequenza: semplice e sempre coerente per playlist "umane". */
    @Transaction
    suspend fun replaceEntries(playlistId: Long, songIds: List<Long>, now: Long) {
        clearEntries(playlistId)
        insertEntries(songIds.mapIndexed { index, songId -> PlaylistEntryEntity(playlistId, songId, index) })
        touch(playlistId, now)
    }
}
