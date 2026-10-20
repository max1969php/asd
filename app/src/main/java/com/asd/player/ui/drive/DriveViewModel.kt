package com.asd.player.ui.drive

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asd.player.AppContainer
import com.asd.player.data.drive.DriveAuth
import com.asd.player.data.drive.DriveClient
import com.asd.player.data.drive.DriveFile
import com.asd.player.data.drive.DriveSourceScanner
import com.asd.player.data.settings.DriveFolder
import com.asd.player.data.settings.DriveSettings
import com.asd.player.domain.model.SongSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Navigazione tra le cartelle di Drive per scegliere quella da indicizzare. */
data class FolderPickerState(
    val path: List<DriveFolder>,
    val folders: List<DriveFile> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
) {
    val current: DriveFolder get() = path.last()
}

class DriveViewModel(private val container: AppContainer) : ViewModel() {

    private val settings = container.settings
    private val auth = container.driveAuth

    val drive: StateFlow<DriveSettings> = settings.drive

    private val _picker = MutableStateFlow<FolderPickerState?>(null)
    val picker: StateFlow<FolderPickerState?> = _picker.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Google chiede il consenso: la UI deve lanciare questo intent. */
    private val _consentRequests = MutableSharedFlow<PendingIntent>(extraBufferCapacity = 1)
    val consentRequests: SharedFlow<PendingIntent> = _consentRequests.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Account in attesa di consenso, tra il lancio dell'intent e il suo risultato. */
    private var pendingEmail: String? = null

    /** Account appena scelto dal selettore di sistema di Android. */
    fun onAccountPicked(email: String?) {
        email ?: return
        authorizeAndActivate(email)
    }

    /** Dalla select in SETUP: cambia l'account predefinito. */
    fun selectDefault(email: String) {
        if (email == drive.value.defaultAccount) return
        authorizeAndActivate(email)
    }

    fun onConsentResult(data: Intent?) {
        val email = pendingEmail ?: return
        pendingEmail = null
        try {
            auth.onConsentResult(email, data)
            activate(email)
        } catch (e: Exception) {
            _messages.tryEmit("Niente consenso, niente Drive.")
        }
    }

    fun disconnect(email: String) = viewModelScope.launch {
        val wasDefault = drive.value.defaultAccount == email
        settings.removeDriveAccount(email)
        auth.invalidate(email)
        if (wasDefault) {
            container.libraryRepository.clearSource(SongSource.DRIVE)
            drive.value.defaultAccount?.let { authorizeAndActivate(it) }
        }
    }

    fun rescan() {
        val state = drive.value
        val email = state.defaultAccount ?: return
        val folder = state.folder ?: return openPicker()
        viewModelScope.launch {
            val scanner = DriveSourceScanner(email, folder.id, container.driveClient, auth, container.metadataReader)
            if (container.libraryRepository.rescan(scanner)) settings.setDriveLastScanAt(System.currentTimeMillis())
        }
    }

    // --- selettore cartella ---

    fun openPicker() {
        if (drive.value.defaultAccount == null) return
        _picker.value = FolderPickerState(path = listOf(DriveFolder(DriveClient.ROOT, "Il mio Drive")))
        loadPicker()
    }

    fun enterFolder(folder: DriveFile) {
        val state = _picker.value ?: return
        _picker.value = FolderPickerState(path = state.path + DriveFolder(folder.id, folder.name))
        loadPicker()
    }

    fun goUp() {
        val state = _picker.value ?: return
        if (state.path.size <= 1) return
        _picker.value = FolderPickerState(path = state.path.dropLast(1))
        loadPicker()
    }

    fun confirmFolder() {
        val state = _picker.value ?: return
        val email = drive.value.defaultAccount ?: return
        val name = state.path.drop(1).joinToString("/") { it.name }.ifEmpty { state.current.name }
        settings.setDriveFolder(email, DriveFolder(state.current.id, name))
        _picker.value = null
        rescan()
    }

    fun closePicker() {
        _picker.value = null
    }

    private fun loadPicker() {
        val state = _picker.value ?: return
        val email = drive.value.defaultAccount ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { container.driveClient.listFolders(email, state.current.id) }
            }
            // L'utente potrebbe essere già entrato altrove nel frattempo.
            if (_picker.value?.path != state.path) return@launch
            _picker.value = result.fold(
                onSuccess = { state.copy(folders = it, loading = false) },
                onFailure = { state.copy(loading = false, error = it.message ?: "Drive irraggiungibile") },
            )
        }
    }

    // --- autorizzazione ---

    private fun authorizeAndActivate(email: String) {
        viewModelScope.launch {
            _busy.value = true
            try {
                when (val outcome = auth.authorize(email)) {
                    is DriveAuth.Outcome.Granted -> activate(email)
                    is DriveAuth.Outcome.NeedsConsent -> {
                        pendingEmail = email
                        _consentRequests.emit(outcome.intent)
                    }
                }
            } catch (e: Exception) {
                _messages.tryEmit(
                    "Google ha detto no (${e.message}). Il client OAuth è configurato? Vedi README."
                )
            } finally {
                _busy.value = false
            }
        }
    }

    /** L'account è autorizzato: diventa il predefinito e i brani Drive passano a lui. */
    private fun activate(email: String) {
        settings.addDriveAccount(email)
        settings.setDefaultDriveAccount(email)
        if (drive.value.folder == null) {
            viewModelScope.launch { container.libraryRepository.clearSource(SongSource.DRIVE) }
            openPicker()
        } else {
            rescan()
        }
    }
}
