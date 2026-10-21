package com.asd.player.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.asd.player.MusicApplication
import com.asd.player.ui.cloud.DriveViewModel
import com.asd.player.ui.cloud.DropboxViewModel
import com.asd.player.ui.library.LibraryViewModel
import com.asd.player.ui.player.PlayerViewModel
import com.asd.player.ui.playlists.PlaylistsViewModel
import com.asd.player.ui.tube.TubeViewModel

object AsdViewModels {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = this[APPLICATION_KEY] as MusicApplication
            LibraryViewModel(app, app.container)
        }
        initializer { PlayerViewModel((this[APPLICATION_KEY] as MusicApplication).container.playbackController) }
        initializer {
            val container = (this[APPLICATION_KEY] as MusicApplication).container
            PlaylistsViewModel(container.playlistRepository, container.playbackController)
        }
        initializer { DriveViewModel((this[APPLICATION_KEY] as MusicApplication).container) }
        initializer { DropboxViewModel((this[APPLICATION_KEY] as MusicApplication).container) }
        initializer { TubeViewModel((this[APPLICATION_KEY] as MusicApplication).container) }
    }
}
