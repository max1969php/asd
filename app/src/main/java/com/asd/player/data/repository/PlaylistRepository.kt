package com.asd.player.data.repository

import com.asd.player.domain.model.Playlist
import com.asd.player.domain.model.PlaylistWithSongs
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    val playlists: Flow<List<Playlist>>
    fun observePlaylist(id: Long): Flow<PlaylistWithSongs?>
    suspend fun create(name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun delete(id: Long)
    suspend fun addSongs(id: Long, songIds: List<Long>)
    suspend fun removeAt(id: Long, index: Int)
    suspend fun move(id: Long, from: Int, to: Int)
}
