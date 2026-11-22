package com.asd.player.domain.model

data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class PlaylistWithSongs(
    val playlist: Playlist,
    val songs: List<Song>,
)
