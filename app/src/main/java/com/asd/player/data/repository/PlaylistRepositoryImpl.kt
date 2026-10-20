package com.asd.player.data.repository

import com.asd.player.data.database.PlaylistDao
import com.asd.player.data.database.PlaylistEntity
import com.asd.player.domain.model.Playlist
import com.asd.player.domain.model.PlaylistWithSongs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class PlaylistRepositoryImpl(private val dao: PlaylistDao) : PlaylistRepository {

    override val playlists: Flow<List<Playlist>> = dao.observeSummaries().map { rows ->
        rows.map { Playlist(it.id, it.name, it.songCount, it.createdAt, it.updatedAt) }
    }

    override fun observePlaylist(id: Long): Flow<PlaylistWithSongs?> =
        combine(dao.observePlaylist(id), dao.observeSongs(id)) { playlist, songs ->
            playlist?.let {
                PlaylistWithSongs(
                    Playlist(it.id, it.name, songs.size, it.createdAt, it.updatedAt),
                    songs.map { song -> song.toDomain() },
                )
            }
        }

    override suspend fun create(name: String): Long {
        val now = System.currentTimeMillis()
        return dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now))
    }

    override suspend fun rename(id: Long, name: String) =
        dao.rename(id, name.trim(), System.currentTimeMillis())

    override suspend fun delete(id: Long) = dao.deletePlaylist(id)

    override suspend fun addSongs(id: Long, songIds: List<Long>) {
        edit(id) { it + songIds }
    }

    override suspend fun removeAt(id: Long, index: Int) {
        edit(id) { ids -> ids.filterIndexed { i, _ -> i != index } }
    }

    override suspend fun move(id: Long, from: Int, to: Int) {
        edit(id) { ids ->
            if (from !in ids.indices || to !in ids.indices) ids
            else ids.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    private suspend fun edit(id: Long, transform: (List<Long>) -> List<Long>) {
        val current = dao.getSongIds(id)
        dao.replaceEntries(id, transform(current), System.currentTimeMillis())
    }
}
