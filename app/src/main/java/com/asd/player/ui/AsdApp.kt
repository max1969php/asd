package com.asd.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asd.player.ui.components.Slogans
import com.asd.player.ui.library.LibraryScreen
import com.asd.player.ui.library.LibraryViewModel
import com.asd.player.ui.player.MiniPlayer
import com.asd.player.ui.player.PlayerScreen
import com.asd.player.ui.player.PlayerViewModel
import com.asd.player.ui.playlists.PlaylistsScreen
import com.asd.player.ui.playlists.PlaylistsViewModel
import com.asd.player.ui.settings.SettingsScreen
import com.asd.player.ui.theme.Asd

private enum class Tab(val label: String) { LIBRARY("Roba"), PLAYLISTS("Mixtape"), SETTINGS("Setup") }

@Composable
fun AsdApp() {
    val library: LibraryViewModel = viewModel(factory = AsdViewModels.Factory)
    val player: PlayerViewModel = viewModel(factory = AsdViewModels.Factory)
    val playlists: PlaylistsViewModel = viewModel(factory = AsdViewModels.Factory)

    val libraryState by library.uiState.collectAsStateWithLifecycle()
    val libraryPlaylists by library.playlists.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val position by player.position.collectAsStateWithLifecycle(initialValue = 0L)
    val allPlaylists by playlists.playlists.collectAsStateWithLifecycle()
    val openedPlaylist by playlists.opened.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.LIBRARY) }
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    val slogan = remember { Slogans.random() }
    val snackbar = remember { SnackbarHostState() }

    val pickDirectory = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(library::onDirectoryChosen)
    }
    val onPick = { pickDirectory.launch(null) }

    LaunchedEffect(Unit) {
        player.errors.collect { snackbar.showSnackbar(it) }
    }

    // Finché le preferenze non dicono che una cartella c'è, mostriamo il manifesto.
    if (libraryState.loaded && libraryState.directoryUri == null) {
        Onboarding(onPickDirectory = onPick, modifier = Modifier.background(Asd.Toner).statusBarsPadding())
        return
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Asd.Toner,
            snackbarHost = {
                SnackbarHost(snackbar) { Snackbar(it, containerColor = Asd.Spray, contentColor = Asd.Paper) }
            },
            bottomBar = {
                Column(Modifier.navigationBarsPadding()) {
                    MiniPlayer(
                        state = playback,
                        positionMs = position,
                        onToggle = player::togglePlayPause,
                        onNext = player::next,
                        onExpand = { playerExpanded = true },
                    )
                    TabBar(selected = tab, onSelect = { tab = it })
                }
            },
        ) { padding ->
            val contentModifier = Modifier.padding(padding)
            when (tab) {
                Tab.LIBRARY -> LibraryScreen(
                    state = libraryState,
                    playlists = libraryPlaylists,
                    currentMediaId = playback.nowPlaying?.mediaId,
                    slogan = slogan,
                    viewModel = library,
                    modifier = contentModifier,
                )
                Tab.PLAYLISTS -> PlaylistsScreen(
                    playlists = allPlaylists,
                    opened = openedPlaylist,
                    currentMediaId = playback.nowPlaying?.mediaId,
                    viewModel = playlists,
                    modifier = contentModifier,
                )
                Tab.SETTINGS -> SettingsScreen(
                    state = libraryState,
                    viewModel = library,
                    onPickDirectory = onPick,
                    modifier = contentModifier,
                )
            }
        }

        AnimatedVisibility(
            visible = playerExpanded && playback.nowPlaying != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            Box(Modifier.background(Asd.Toner).statusBarsPadding().navigationBarsPadding()) {
                PlayerScreen(
                    state = playback,
                    positionMs = position,
                    viewModel = player,
                    onClose = { playerExpanded = false },
                )
            }
        }
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Asd.Paper)
            .padding(top = 2.dp)
            .background(Asd.Toner)
    ) {
        Tab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .background(if (active) Asd.Acid else Asd.Toner)
                    .clickable { onSelect(tab) },
            ) {
                Text(
                    text = if (active) "[${tab.label.uppercase()}]" else tab.label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) Asd.Toner else Asd.Paper,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
