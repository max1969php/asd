package com.asd.player.ui.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asd.player.data.repository.MusicLibraryRepository
import com.asd.player.data.scanner.SourceScanner
import com.asd.player.data.settings.CloudFolder
import com.asd.player.data.settings.CloudSettings
import com.asd.player.data.settings.SettingsStore
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

data class FolderPickerState(
    val path: List<CloudFolder>,
    val folders: List<CloudFolder> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
) {
    val current: CloudFolder get() = path.last()
}

/**
 * La parte generica di una fonte cloud: account predefinito, scelta della cartella,
 * riscansione. Le sottoclassi sanno solo come si autorizza un account e come si leggono le cartelle.
 */
abstract class CloudViewModel(
    private val prefs: SettingsStore.CloudPrefs,
    private val library: MusicLibraryRepository,
    private val source: SongSource,
    private val root: CloudFolder,
) : ViewModel() {

    val settings: StateFlow<CloudSettings> = prefs.state

    private val _picker = MutableStateFlow<FolderPickerState?>(null)
    val picker: StateFlow<FolderPickerState?> = _picker.asStateFlow()

    protected val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    protected val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Si assicura che [email] sia autorizzato (chiedendo il consenso se serve), poi chiama [activate]. */
    protected abstract fun authorizeAndActivate(email: String)

    protected abstract fun listFolders(email: String, folderId: String): List<CloudFolder>

    protected abstract fun scanner(email: String, folderId: String): SourceScanner

    protected open fun forget(email: String) {}

    /**
     * Dalla select in SETUP: cambia l'account predefinito. Riscegliere lo stesso account non è
     * un no-op: rinnova l'autorizzazione e riscansiona.
     */
    fun selectDefault(email: String) = authorizeAndActivate(email)

    /** Bottone "Riscansiona": passa dall'autorizzazione, così un consenso scaduto si chiede a schermo. */
    fun refresh() {
        settings.value.defaultAccount?.let(::authorizeAndActivate)
    }

    fun disconnect(email: String) = viewModelScope.launch {
        val wasDefault = settings.value.defaultAccount == email
        prefs.removeAccount(email)
        forget(email)
        if (wasDefault) {
            library.clearSource(source)
            settings.value.defaultAccount?.let { authorizeAndActivate(it) }
        }
    }

    fun rescan() {
        val state = settings.value
        val email = state.defaultAccount ?: return
        val folder = state.folder ?: return openPicker()
        viewModelScope.launch {
            if (library.rescan(scanner(email, folder.id))) prefs.setLastScanAt(System.currentTimeMillis())
        }
    }

    /** L'account è autorizzato: diventa il predefinito e i brani di questa fonte passano a lui. */
    protected fun activate(email: String) {
        prefs.addAccount(email)
        prefs.setDefault(email)
        if (settings.value.folder == null) {
            viewModelScope.launch { library.clearSource(source) }
            openPicker()
        } else {
            rescan()
        }
    }

    // --- selettore cartella ---

    fun openPicker() {
        if (settings.value.defaultAccount == null) return
        _picker.value = FolderPickerState(path = listOf(root))
        loadPicker()
    }

    fun enterFolder(folder: CloudFolder) {
        val state = _picker.value ?: return
        _picker.value = FolderPickerState(path = state.path + folder)
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
        val email = settings.value.defaultAccount ?: return
        val name = state.path.drop(1).joinToString("/") { it.name }.ifEmpty { state.current.name }
        prefs.setFolder(email, CloudFolder(state.current.id, name))
        _picker.value = null
        rescan()
    }

    fun closePicker() {
        _picker.value = null
    }

    private fun loadPicker() {
        val state = _picker.value ?: return
        val email = settings.value.defaultAccount ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { listFolders(email, state.current.id) } }
            // L'utente potrebbe essere già entrato altrove nel frattempo.
            if (_picker.value?.path != state.path) return@launch
            _picker.value = result.fold(
                onSuccess = { state.copy(folders = it, loading = false) },
                onFailure = { state.copy(loading = false, error = it.message ?: "Cloud irraggiungibile") },
            )
        }
    }
}
