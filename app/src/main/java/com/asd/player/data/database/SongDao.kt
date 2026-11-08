package com.asd.player.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE source = :source")
    suspend fun getBySource(source: String): List<SongEntity>

    @Insert
    suspend fun insertAll(songs: List<SongEntity>)

    /** Brani indicizzati da una versione vecchia (senza cartella o con meno tag): la riscansione li sistema. */
    @Query("SELECT COUNT(*) FROM songs WHERE source = :source AND (folder IS NULL OR tagsVersion < ${SongEntity.TAGS_VERSION})")
    suspend fun countOutdated(source: String): Int

    @Query("SELECT * FROM songs WHERE loudnessLufs IS NULL OR analysis < ${SongEntity.ANALYSIS_VERSION}")
    suspend fun getUnmeasured(): List<SongEntity>

    @Query(
        """UPDATE songs SET loudnessLufs = :lufs, peakDb = :peakDb, soundStartMs = :startMs, soundEndMs = :endMs,
        analysis = ${SongEntity.ANALYSIS_VERSION} WHERE id = :id"""
    )
    suspend fun setAnalysis(id: Long, lufs: Double, peakDb: Double?, startMs: Long?, endMs: Long?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: SongEntity)

    @Update
    suspend fun updateAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM songs WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Transaction
    suspend fun applyScan(insert: List<SongEntity>, update: List<SongEntity>, deleteIds: List<Long>) {
        // SQLite limita il numero di parametri: cancelliamo a blocchi.
        deleteIds.chunked(500).forEach { deleteByIds(it) }
        if (update.isNotEmpty()) updateAll(update)
        if (insert.isNotEmpty()) insertAll(insert)
    }
}
