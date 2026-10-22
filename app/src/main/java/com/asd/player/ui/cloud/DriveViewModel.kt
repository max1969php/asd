package com.asd.player.ui.cloud

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.asd.player.AppContainer
import com.asd.player.L10n
import com.asd.player.R
import com.asd.player.data.drive.DriveAuth
import com.asd.player.data.drive.DriveClient
import com.asd.player.data.drive.DriveSourceScanner
import com.asd.player.data.drive.OwnGoogleClient
import com.asd.player.data.settings.CloudFolder
import com.asd.player.domain.model.SongSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class DriveViewModel(private val container: AppContainer) : CloudViewModel(
    prefs = container.settings.drive,
    library = container.libraryRepository,
    source = SongSource.DRIVE,
    root = CloudFolder(DriveClient.ROOT, L10n.str(R.string.my_drive)),
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
            _messages.tryEmit(L10n.str(R.string.drive_no_consent))
        }
    }

    override fun forget(email: String) = auth.forget(email)

    /** Progetto Google dell'utente (null = client di ASD con gli account del telefono). */
    val ownClient: StateFlow<OwnGoogleClient.Config?> = auth.own.config

    /** Restituisce false se il client ID non ha la forma giusta. */
    fun saveOwnClient(clientId: String, clientSecret: String): Boolean {
        if (!OwnGoogleClient.isValidClientId(clientId) || clientSecret.isBlank()) return false
        auth.own.save(clientId, clientSecret)
        auth.clearCache()
        return true
    }

    fun clearOwnClient() {
        auth.own.clear()
        auth.clearCache()
    }

    /** "Collega un account" col progetto dell'utente: l'account lo sceglie nel browser. */
    fun addAccountWithBrowser() {
        viewModelScope.launch {
            _busy.value = true
            try {
                login(hint = null)
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun login(hint: String?) {
        _messages.tryEmit(L10n.str(R.string.drive_login_wait))
        try {
            activate(auth.browserLogin(hint))
        } catch (e: Exception) {
            _messages.tryEmit(e.message ?: L10n.str(R.string.drive_no_consent))
        }
    }

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
                    DriveAuth.Outcome.NeedsBrowser -> login(hint = email)
                }
            } catch (e: Exception) {
                _messages.tryEmit(
                    L10n.str(R.string.drive_google_said_no, e.message.orEmpty())
                )
            } finally {
                _busy.value = false
            }
        }
    }
}
