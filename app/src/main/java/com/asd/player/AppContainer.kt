package com.asd.player

import android.content.Context
import com.asd.player.data.database.AppDatabase
import com.asd.player.data.drive.DriveAuth
import com.asd.player.data.drive.DriveClient
import com.asd.player.data.drive.DriveStreamResolver
import com.asd.player.data.repository.MusicLibraryRepository
import com.asd.player.data.repository.MusicLibraryRepositoryImpl
import com.asd.player.data.repository.PlaylistRepository
import com.asd.player.data.repository.PlaylistRepositoryImpl
import com.asd.player.data.scanner.AudioScanner
import com.asd.player.data.scanner.MetadataReader
import com.asd.player.data.settings.SettingsStore
import com.asd.player.domain.usecase.SearchSongs
import com.asd.player.playback.PlaybackController

/** Dependency injection fatta a mano: per un'app così non serve un framework. */
class AppContainer(context: Context) {
    private val database = AppDatabase.build(context)

    val settings = SettingsStore(context)
    val audioScanner = AudioScanner(context.contentResolver)
    val metadataReader = MetadataReader(context)
    val libraryRepository: MusicLibraryRepository = MusicLibraryRepositoryImpl(database.songDao())

    val driveAuth = DriveAuth(context)
    val driveClient = DriveClient(driveAuth)
    val driveStreamResolver = DriveStreamResolver(driveAuth, settings)
    val playlistRepository: PlaylistRepository = PlaylistRepositoryImpl(database.playlistDao())
    val playbackController = PlaybackController(context)
    val searchSongs = SearchSongs()
}
