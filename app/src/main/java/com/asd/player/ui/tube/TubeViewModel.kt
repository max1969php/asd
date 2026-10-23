package com.asd.player.ui.tube

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asd.player.AppContainer
import com.asd.player.L10n
import com.asd.player.R
import com.asd.player.data.drive.DriveAuth
import com.asd.player.data.drive.DriveClient
import com.asd.player.data.drive.DriveFile
import com.asd.player.data.settings.CloudFolder
import com.asd.player.data.settings.CloudSettings
import com.asd.player.data.settings.TubeDestination
import com.asd.player.data.tube.JobSource
import com.asd.player.data.tube.TubeCookies
import com.asd.player.data.tube.TubeJob
import com.asd.player.data.tube.TubeLink
import com.asd.player.data.tube.TubeTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Il browser di "Scarica dal Drive": dove siamo e cosa c'è (sottocartelle e file audio). */
data class DriveBrowserState(
    val email: String,
    val path: List<CloudFolder>,
    val entries: List<DriveFile> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
) {
    val current: CloudFolder get() = path.last()
}

/** Una voce della select "salva in": la destinazione pronta, oppure cosa manca per usarla. */
data class DestinationOption(
    val destination: TubeDestination,
    val target: TubeTarget?,
    val problem: String?,
)

class TubeViewModel(private val container: AppContainer) : ViewModel() {

    private val importer = container.tubeImporter
    private val auth = container.driveAuth
    private val settings = container.settings

    val jobs: StateFlow<List<TubeJob>> = importer.jobs
    val destination: StateFlow<TubeDestination> = settings.tubeDestination

    val options: StateFlow<List<DestinationOption>> = combine(
        settings.directoryUri,
        settings.drive.state,
    ) { dir, drive ->
        val local = localOption(dir != null)
        val cloud = cloudOption(TubeDestination.DRIVE, L10n.str(R.string.drive), drive)
        listOf(bothOption(local, cloud), local, cloud)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ytDlpVersion = MutableStateFlow<String?>(null)
    val ytDlpVersion: StateFlow<String?> = _ytDlpVersion.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    /** Serve il permesso di scrittura su Drive: la UI lancia la schermata di consenso di Google. */
    private val _consentRequests = MutableSharedFlow<PendingIntent>(extraBufferCapacity = 1)
    val consentRequests: SharedFlow<PendingIntent> = _consentRequests.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Azione in attesa del consenso Google (per quell'account), tra il lancio dell'intent e il suo risultato. */
    private var pending: Pair<String, () -> Unit>? = null

    private val _browser = MutableStateFlow<DriveBrowserState?>(null)
    val browser: StateFlow<DriveBrowserState?> = _browser.asStateFlow()

    val cookies: StateFlow<TubeCookies.Origin> = importer.cookies.origin

    /** L'utente ha chiuso la pagina di accesso a YouTube. */
    fun onLoginClosed() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { importer.cookies.saveFromLogin() }
            _messages.tryEmit(if (ok) L10n.str(R.string.yt_login_ok) else L10n.str(R.string.yt_login_failed))
        }
    }

    fun importCookies(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    container.contentResolver.openInputStream(uri)!!.use(importer.cookies::import)
                }
                _messages.tryEmit(L10n.str(R.string.yt_cookies_imported))
            } catch (e: Exception) {
                _messages.tryEmit(L10n.str(R.string.yt_cookies_failed, e.message.orEmpty()))
            }
        }
    }

    fun logoutYoutube() {
        importer.cookies.clear()
        _messages.tryEmit(L10n.str(R.string.yt_logged_out))
    }

    fun setDestination(destination: TubeDestination) = settings.setTubeDestination(destination)

    /** Invio sul campo URL. Restituisce true se il link è buono e il campo si può svuotare. */
    fun submit(input: String): Boolean {
        val url = TubeLink.clean(input) ?: return false.also { _messages.tryEmit(L10n.str(R.string.not_youtube_link)) }
        val option = options.value.find { it.destination == destination.value }
        val target = option?.target
            ?: return false.also { _messages.tryEmit(L10n.str(R.string.destination_not_ready, option?.problem ?: L10n.str(R.string.go_to_setup_lower))) }
        if (!target.needsDrive) {
            importer.enqueue(url, target)
            return true
        }
        withDrive(generateSequence(target) { it.also }.first { it.destination == TubeDestination.DRIVE }.account!!) {
            importer.enqueue(url, target)
        }
        return true
    }

    fun onConsentResult(data: Intent?) {
        val (email, action) = pending ?: return
        pending = null
        try {
            auth.onConsentResult(email, data)
            action()
        } catch (e: Exception) {
            _messages.tryEmit(L10n.str(R.string.drive_needs_permission))
        }
    }

    // --- Scarica dal Drive: un file o una cartella intera, verso la cartella del telefono ---

    fun openBrowser() {
        if (importer.localTarget() == null) {
            _messages.tryEmit(L10n.str(R.string.need_writable_folder))
            return
        }
        val email = settings.drive.state.value.defaultAccount
            ?: return run { _messages.tryEmit(L10n.str(R.string.link_drive_first)) }
        withDrive(email) {
            _browser.value = DriveBrowserState(email, listOf(CloudFolder(DriveClient.ROOT, L10n.str(R.string.my_drive))))
            loadBrowser()
        }
    }

    fun browseInto(folder: DriveFile) {
        val state = _browser.value ?: return
        _browser.value = state.copy(path = state.path + CloudFolder(folder.id, folder.name), entries = emptyList(), loading = true, error = null)
        loadBrowser()
    }

    fun browseUp() {
        val state = _browser.value ?: return
        if (state.path.size <= 1) return
        _browser.value = state.copy(path = state.path.dropLast(1), entries = emptyList(), loading = true, error = null)
        loadBrowser()
    }

    fun closeBrowser() {
        _browser.value = null
    }

    fun downloadFile(file: DriveFile) {
        val state = _browser.value ?: return
        val target = importer.localTarget() ?: return
        importer.enqueue(listOf(JobSource.DriveFile(state.email, file.id, file.name, emptyList(), file.size)), target)
        _messages.tryEmit(L10n.str(R.string.queued, file.name))
    }

    /** Tutta la cartella corrente, sottocartelle comprese, ricreata dentro la cartella del telefono. */
    fun downloadFolder() {
        val state = _browser.value ?: return
        val target = importer.localTarget() ?: return
        val folder = state.current
        _browser.value = state.copy(loading = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { container.driveClient.audioTree(state.email, folder.id) } }
            _browser.value = _browser.value?.copy(loading = false)
            result.fold(
                onSuccess = { files ->
                    if (files.isEmpty()) {
                        _messages.tryEmit(L10n.str(R.string.folder_no_audio, folder.name))
                    } else {
                        // La radice del Drive non diventa una cartella: i pezzi finiscono in quella del telefono.
                        val base = if (folder.id == DriveClient.ROOT) emptyList() else listOf(folder.name)
                        importer.enqueue(
                            files.map { (dirs, file) -> JobSource.DriveFile(state.email, file.id, file.name, base + dirs, file.size) },
                            target,
                        )
                        _messages.tryEmit(L10n.str(R.string.queued_many, files.size))
                        _browser.value = null
                    }
                },
                onFailure = { _messages.tryEmit(L10n.str(R.string.drive_not_answering, it.message.orEmpty())) },
            )
        }
    }

    private fun loadBrowser() {
        val state = _browser.value ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { container.driveClient.listEntries(state.email, state.current.id) }
            }
            if (_browser.value?.path != state.path) return@launch
            _browser.value = result.fold(
                onSuccess = { state.copy(entries = it, loading = false) },
                onFailure = { state.copy(loading = false, error = it.message ?: L10n.str(R.string.drive_unreachable)) },
            )
        }
    }

    /** Esegue [action] quando [email] ha il permesso su Drive, chiedendo il consenso se serve. */
    private fun withDrive(email: String, action: () -> Unit) {
        viewModelScope.launch {
            try {
                when (val outcome = auth.authorize(email)) {
                    is DriveAuth.Outcome.Granted -> action()
                    is DriveAuth.Outcome.NeedsConsent -> {
                        pending = email to action
                        _consentRequests.emit(outcome.intent)
                    }
                }
            } catch (e: Exception) {
                _messages.tryEmit(L10n.str(R.string.google_said_no, e.message.orEmpty()))
            }
        }
    }

    private fun localOption(hasFolder: Boolean): DestinationOption {
        val target = importer.localTarget()
        return when {
            target != null -> DestinationOption(TubeDestination.LOCAL, target, null)
            !hasFolder -> DestinationOption(TubeDestination.LOCAL, null, L10n.str(R.string.no_phone_folder))
            else -> DestinationOption(TubeDestination.LOCAL, null, L10n.str(R.string.repick_folder_to_write))
        }
    }

    fun retry(job: TubeJob) = importer.retry(job.id)

    fun clearFinished() = importer.clearFinished()

    fun loadVersion() {
        if (_ytDlpVersion.value != null) return
        viewModelScope.launch {
            _ytDlpVersion.value = withContext(Dispatchers.IO) { importer.ytDlpVersion() } ?: L10n.str(R.string.ytdlp_bundled)
        }
    }

    fun updateYtDlp() {
        if (_updating.value) return
        viewModelScope.launch {
            _updating.value = true
            try {
                val version = withContext(Dispatchers.IO) { importer.updateYtDlp() }
                _ytDlpVersion.value = version
                _messages.tryEmit(L10n.str(R.string.ytdlp_updated, version))
            } catch (e: Exception) {
                _messages.tryEmit(L10n.str(R.string.ytdlp_update_failed, e.message.orEmpty()))
            } finally {
                _updating.value = false
            }
        }
    }

    /** Telefono + Drive: pronta solo se lo sono tutte e due. */
    private fun bothOption(local: DestinationOption, cloud: DestinationOption): DestinationOption {
        val phone = local.target ?: return DestinationOption(TubeDestination.BOTH, null, local.problem)
        val drive = cloud.target ?: return DestinationOption(TubeDestination.BOTH, null, cloud.problem)
        return DestinationOption(
            TubeDestination.BOTH,
            phone.copy(destination = TubeDestination.BOTH, label = "${phone.label} + ${drive.label}", also = drive),
            null,
        )
    }

    private fun cloudOption(destination: TubeDestination, name: String, state: CloudSettings): DestinationOption {
        val email = state.defaultAccount ?: return DestinationOption(destination, null, L10n.str(R.string.no_cloud_account, name))
        val folder = state.folder ?: return DestinationOption(destination, null, L10n.str(R.string.no_cloud_folder, name))
        return DestinationOption(destination, TubeTarget(destination, email, folder.id, "$name · $email · ${folder.name}"), null)
    }
}
