package com.asd.player.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAll(): List<SongEntity>

    @Insert
    suspend fun insertAll(songs: List<SongEntity>)

    @Update
    suspend fun updateAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    @Transaction
    suspend fun applyScan(insert: List<SongEntity>, update: List<SongEntity>, deleteIds: List<Long>) {
        // SQLite limita il numero di parametri: cancelliamo a blocchi.
        deleteIds.chunked(500).forEach { deleteByIds(it) }
        if (update.isNotEmpty()) updateAll(update)
        if (insert.isNotEmpty()) insertAll(insert)
    }
}
