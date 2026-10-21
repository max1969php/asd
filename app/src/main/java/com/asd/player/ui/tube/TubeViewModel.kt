package com.asd.player.ui.tube

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asd.player.AppContainer
import com.asd.player.data.drive.DriveAuth
import com.asd.player.data.scanner.AudioScanner
import com.asd.player.data.settings.CloudSettings
import com.asd.player.data.settings.TubeDestination
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
        settings.dropbox.state,
    ) { dir, drive, dropbox ->
        listOf(
            when {
                dir == null -> DestinationOption(TubeDestination.LOCAL, null, "nessuna cartella del telefono")
                !container.audioScanner.canWrite(dir) ->
                    DestinationOption(TubeDestination.LOCAL, null, "riscegli la cartella in SETUP per poterci scrivere")
                else -> DestinationOption(
                    TubeDestination.LOCAL,
                    TubeTarget(TubeDestination.LOCAL, null, dir.toString(), "Telefono · ${AudioScanner.readablePath(dir)}"),
                    null,
                )
            },
            cloudOption(TubeDestination.DRIVE, "Drive", drive),
            cloudOption(TubeDestination.DROPBOX, "Dropbox", dropbox),
        )
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

    /** Link in attesa del consenso Google, tra il lancio dell'intent e il suo risultato. */
    private var pending: Pair<String, TubeTarget>? = null

    val cookies: StateFlow<TubeCookies.Origin> = importer.cookies.origin

    /** L'utente ha chiuso la pagina di accesso a YouTube. */
    fun onLoginClosed() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { importer.cookies.saveFromLogin() }
            _messages.tryEmit(if (ok) "Dentro YouTube: niente più domande da bot." else "Accesso a YouTube non completato.")
        }
    }

    fun importCookies(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    container.contentResolver.openInputStream(uri)!!.use(importer.cookies::import)
                }
                _messages.tryEmit("Cookie di YouTube importati.")
            } catch (e: Exception) {
                _messages.tryEmit("Cookie non importati: ${e.message}")
            }
        }
    }

    fun logoutYoutube() {
        importer.cookies.clear()
        _messages.tryEmit("Uscito da YouTube: ASD non ha più i tuoi cookie.")
    }

    fun setDestination(destination: TubeDestination) = settings.setTubeDestination(destination)

    /** Invio sul campo URL. Restituisce true se il link è buono e il campo si può svuotare. */
    fun submit(input: String): Boolean {
        val url = TubeLink.clean(input) ?: return false.also { _messages.tryEmit("Questo non è un link YouTube.") }
        val option = options.value.find { it.destination == destination.value }
        val target = option?.target
            ?: return false.also { _messages.tryEmit("Destinazione non pronta: ${option?.problem ?: "vai in SETUP"}.") }
        if (target.destination != TubeDestination.DRIVE) {
            importer.enqueue(url, target)
            return true
        }
        val email = target.account!!
        viewModelScope.launch {
            try {
                when (val outcome = auth.authorize(email)) {
                    is DriveAuth.Outcome.Granted -> importer.enqueue(url, target)
                    is DriveAuth.Outcome.NeedsConsent -> {
                        pending = url to target
                        _consentRequests.emit(outcome.intent)
                    }
                }
            } catch (e: Exception) {
                _messages.tryEmit("Google ha detto no (${e.message}).")
            }
        }
        return true
    }

    fun onConsentResult(data: Intent?) {
        val (url, target) = pending ?: return
        pending = null
        try {
            auth.onConsentResult(target.account!!, data)
            importer.enqueue(url, target)
        } catch (e: Exception) {
            _messages.tryEmit("Senza il permesso di scrivere su Drive non si carica niente.")
        }
    }

    fun retry(job: TubeJob) = importer.retry(job.id)

    fun clearFinished() = importer.clearFinished()

    fun loadVersion() {
        if (_ytDlpVersion.value != null) return
        viewModelScope.launch {
            _ytDlpVersion.value = withContext(Dispatchers.IO) { importer.ytDlpVersion() } ?: "quello di serie"
        }
    }

    fun updateYtDlp() {
        if (_updating.value) return
        viewModelScope.launch {
            _updating.value = true
            try {
                val version = withContext(Dispatchers.IO) { importer.updateYtDlp() }
                _ytDlpVersion.value = version
                _messages.tryEmit("yt-dlp aggiornato: $version")
            } catch (e: Exception) {
                _messages.tryEmit("Aggiornamento fallito: ${e.message}")
            } finally {
                _updating.value = false
            }
        }
    }

    private fun cloudOption(destination: TubeDestination, name: String, state: CloudSettings): DestinationOption {
        val email = state.defaultAccount ?: return DestinationOption(destination, null, "nessun account $name collegato")
        val folder = state.folder ?: return DestinationOption(destination, null, "nessuna cartella $name scelta")
        return DestinationOption(destination, TubeTarget(destination, email, folder.id, "$name · $email · ${folder.name}"), null)
    }
}
