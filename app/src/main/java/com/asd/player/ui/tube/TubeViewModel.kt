package com.asd.player.ui.tube

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asd.player.AppContainer
import com.asd.player.data.drive.DriveAuth
import com.asd.player.data.settings.DriveFolder
import com.asd.player.data.settings.DriveSettings
import com.asd.player.data.tube.TubeJob
import com.asd.player.data.tube.TubeLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TubeViewModel(private val container: AppContainer) : ViewModel() {

    private val importer = container.tubeImporter
    private val auth = container.driveAuth

    val drive: StateFlow<DriveSettings> = container.settings.drive
    val jobs: StateFlow<List<TubeJob>> = importer.jobs

    private val _ytDlpVersion = MutableStateFlow<String?>(null)
    val ytDlpVersion: StateFlow<String?> = _ytDlpVersion.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    /** Serve il permesso di scrittura su Drive: la UI lancia la schermata di consenso di Google. */
    private val _consentRequests = MutableSharedFlow<PendingIntent>(extraBufferCapacity = 1)
    val consentRequests: SharedFlow<PendingIntent> = _consentRequests.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Link in attesa del consenso, tra il lancio dell'intent e il suo risultato. */
    private var pending: Triple<String, String, DriveFolder>? = null

    /** Invio sul campo URL. Restituisce true se il link è buono e il campo si può svuotare. */
    fun submit(input: String): Boolean {
        val url = TubeLink.clean(input) ?: return false.also { _messages.tryEmit("Questo non è un link YouTube.") }
        val state = drive.value
        val email = state.defaultAccount
            ?: return false.also { _messages.tryEmit("Prima collega Google Drive in SETUP.") }
        val folder = state.folder
            ?: return false.also { _messages.tryEmit("Scegli una cartella Drive in SETUP.") }
        viewModelScope.launch {
            try {
                when (val outcome = auth.authorize(email)) {
                    is DriveAuth.Outcome.Granted -> importer.enqueue(url, email, folder)
                    is DriveAuth.Outcome.NeedsConsent -> {
                        pending = Triple(url, email, folder)
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
        val (url, email, folder) = pending ?: return
        pending = null
        try {
            auth.onConsentResult(email, data)
            importer.enqueue(url, email, folder)
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
}
