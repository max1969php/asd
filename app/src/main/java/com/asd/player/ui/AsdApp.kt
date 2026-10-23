package com.asd.player.ui

import android.accounts.AccountManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asd.player.BuildConfig
import com.asd.player.R
import com.asd.player.data.drive.DriveAuth
import com.asd.player.ui.cloud.DriveViewModel
import com.asd.player.ui.cloud.FolderPicker
import com.asd.player.ui.library.LibraryScreen
import com.asd.player.ui.library.LibraryViewModel
import com.asd.player.ui.player.MiniPlayer
import com.asd.player.ui.player.PlayerScreen
import com.asd.player.ui.player.PlayerViewModel
import com.asd.player.ui.playlists.PlaylistsScreen
import com.asd.player.ui.playlists.PlaylistsViewModel
import com.asd.player.ui.settings.SettingsScreen
import com.asd.player.ui.theme.Asd
import com.asd.player.ui.tube.TubeScreen
import com.asd.player.ui.tube.TubeViewModel
import kotlinx.coroutines.launch

/** [enabled]: la scheda TUBO c'è solo nelle versioni con yt-dlp (full e tubo). */
private enum class Tab(@StringRes val label: Int, val enabled: Boolean = true) {
    LIBRARY(R.string.tab_library), PLAYLISTS(R.string.tab_playlists), TUBE(R.string.tab_tube, BuildConfig.TUBE), SETTINGS(R.string.tab_settings);

    companion object {
        val visible = entries.filter { it.enabled }
    }
}

@Composable
fun AsdApp() {
    val library: LibraryViewModel = viewModel(factory = AsdViewModels.Factory)
    val player: PlayerViewModel = viewModel(factory = AsdViewModels.Factory)
    val playlists: PlaylistsViewModel = viewModel(factory = AsdViewModels.Factory)
    val driveVm: DriveViewModel = viewModel(factory = AsdViewModels.Factory)
    val tubeVm: TubeViewModel = viewModel(factory = AsdViewModels.Factory)

    val libraryState by library.uiState.collectAsStateWithLifecycle()
    val libraryPlaylists by library.playlists.collectAsStateWithLifecycle()
    val playback by player.state.collectAsStateWithLifecycle()
    val position by player.position.collectAsStateWithLifecycle(initialValue = 0L)
    val allPlaylists by playlists.playlists.collectAsStateWithLifecycle()
    val openedPlaylist by playlists.opened.collectAsStateWithLifecycle()
    val drive by driveVm.settings.collectAsStateWithLifecycle()
    val driveFolderPicker by driveVm.picker.collectAsStateWithLifecycle()
    val tubeOptions by tubeVm.options.collectAsStateWithLifecycle()
    val tubeDestination by tubeVm.destination.collectAsStateWithLifecycle()
    val tubeJobs by tubeVm.jobs.collectAsStateWithLifecycle()
    val ytDlpVersion by tubeVm.ytDlpVersion.collectAsStateWithLifecycle()
    val ytDlpUpdating by tubeVm.updating.collectAsStateWithLifecycle()
    val youtubeCookies by tubeVm.cookies.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.LIBRARY) }
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    val slogans = stringArrayResource(R.array.slogans)
    val slogan = remember(slogans) { slogans.random() }
    val snackbar = remember { SnackbarHostState() }

    val pickDirectory = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(library::onDirectoryChosen)
    }
    val onPick = { pickDirectory.launch(null) }

    // Selettore di sistema: elenca gli account Google presenti sul telefono.
    val pickAccount = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        driveVm.onAccountPicked(result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME))
    }
    val onAddGoogleAccount = {
        pickAccount.launch(
            AccountManager.newChooseAccountIntent(
                null, null, arrayOf(DriveAuth.GOOGLE_ACCOUNT_TYPE), null, null, null, null,
            )
        )
    }
    // Schermata di consenso di Google per l'accesso a Drive.
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        driveVm.onConsentResult(result.data)
    }
    // Stessa schermata, chiesta dal Tubo prima di caricare: alla fine riparte il download in attesa.
    val tubeConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        tubeVm.onConsentResult(result.data)
    }

    LaunchedEffect(Unit) {
        launch { player.errors.collect { snackbar.showSnackbar(it) } }
        launch { driveVm.messages.collect { snackbar.showSnackbar(it) } }
        launch { tubeVm.messages.collect { snackbar.showSnackbar(it) } }
        launch { library.messages.collect { snackbar.showSnackbar(it) } }
        launch {
            tubeVm.consentRequests.collect {
                tubeConsent.launch(IntentSenderRequest.Builder(it.intentSender).build())
            }
        }
        launch {
            driveVm.consentRequests.collect {
                consent.launch(IntentSenderRequest.Builder(it.intentSender).build())
            }
        }
    }

    driveFolderPicker?.let { FolderPicker(stringResource(R.string.drive_folder_title), it, drive.defaultAccount, driveVm) }

    // Finché non c'è almeno una fonte, mostriamo il manifesto.
    if (libraryState.loaded && libraryState.directoryUri == null && drive.defaultAccount == null) {
        Box(Modifier.fillMaxSize()) {
            Onboarding(
                onPickDirectory = onPick,
                onConnectDrive = onAddGoogleAccount.takeIf { BuildConfig.DRIVE },
                modifier = Modifier.background(Asd.Toner).statusBarsPadding(),
            )
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                Snackbar(it, containerColor = Asd.Spray, contentColor = Asd.Paper)
            }
        }
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
                Tab.TUBE -> TubeScreen(
                    options = tubeOptions,
                    destination = tubeDestination,
                    jobs = tubeJobs,
                    ytDlpVersion = ytDlpVersion,
                    updating = ytDlpUpdating,
                    cookies = youtubeCookies,
                    viewModel = tubeVm,
                    onOpenSetup = { tab = Tab.SETTINGS },
                    modifier = contentModifier,
                )
                Tab.SETTINGS -> SettingsScreen(
                    state = libraryState,
                    viewModel = library,
                    driveViewModel = driveVm,
                    onPickDirectory = onPick,
                    onAddGoogleAccount = onAddGoogleAccount,
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
        Tab.visible.forEach { tab ->
            val active = tab == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .background(if (active) Asd.Acid else Asd.Toner)
                    .clickable { onSelect(tab) },
            ) {
                val label = stringResource(tab.label).uppercase()
                Text(
                    text = if (active) "[$label]" else label,
                    // Quattro schede su un telefono stretto: "[MIXTAPE]" deve stare su una riga.
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) Asd.Toner else Asd.Paper,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
