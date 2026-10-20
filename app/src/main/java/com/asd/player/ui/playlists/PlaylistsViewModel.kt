package com.asd.player.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asd.player.data.repository.PlaylistRepository
import com.asd.player.domain.model.Playlist
import com.asd.player.domain.model.PlaylistWithSongs
import com.asd.player.domain.model.Song
import com.asd.player.playback.PlaybackController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsViewModel(
    private val repository: PlaylistRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val openId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val opened: StateFlow<PlaylistWithSongs?> = openId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.observePlaylist(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun open(id: Long?) {
        openId.value = id
    }

    fun create(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) open(repository.create(name))
    }

    fun rename(id: Long, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) repository.rename(id, name)
    }

    fun delete(id: Long) = viewModelScope.launch {
        if (openId.value == id) open(null)
        repository.delete(id)
    }

    fun removeAt(id: Long, index: Int) = viewModelScope.launch { repository.removeAt(id, index) }
    fun move(id: Long, from: Int, to: Int) = viewModelScope.launch { repository.move(id, from, to) }

    fun play(songs: List<Song>, index: Int = 0, shuffle: Boolean = false) =
        playback.playQueue(songs, index, shuffle)
}
