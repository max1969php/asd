package com.asd.player.ui.cloud

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.asd.player.AppContainer
import com.asd.player.data.drive.DriveAuth
import com.asd.player.data.drive.DriveClient
import com.asd.player.data.drive.DriveSourceScanner
import com.asd.player.data.settings.CloudFolder
import com.asd.player.domain.model.SongSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class DriveViewModel(private val container: AppContainer) : CloudViewModel(
    prefs = container.settings.drive,
    library = container.libraryRepository,
    source = SongSource.DRIVE,
    root = CloudFolder(DriveClient.ROOT, "Il mio Drive"),
) {
    private val auth = container.driveAuth

    /** Google chiede il consenso: la UI deve lanciare questo intent. */
    private val _consentRequests = MutableSharedFlow<PendingIntent>(extraBufferCapacity = 1)
    val consentRequests: SharedFlow<PendingIntent> = _consentRequests.asSharedFlow()

    /** Account in attesa di consenso, tra il lancio dell'intent e il suo risultato. */
    private var pendingEmail: String? = null

    /** Account appena scelto dal selettore di sistema di Android. */
    fun onAccountPicked(email: String?) {
        email ?: return
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

    override fun forget(email: String) = auth.invalidate(email)

    override fun listFolders(email: String, folderId: String) =
        container.driveClient.listFolders(email, folderId).map { CloudFolder(it.id, it.name) }

    override fun scanner(email: String, folderId: String) =
        DriveSourceScanner(email, folderId, container.driveClient, auth, container.metadataReader)

    override fun authorizeAndActivate(email: String) {
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
}
