package com.asd.player.ui.cloud

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.asd.player.AppContainer
import com.asd.player.data.dropbox.DropboxClient
import com.asd.player.data.dropbox.DropboxSourceScanner
import com.asd.player.data.settings.CloudFolder
import com.asd.player.domain.model.SongSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DropboxViewModel(private val container: AppContainer) : CloudViewModel(
    prefs = container.settings.dropbox,
    library = container.libraryRepository,
    source = SongSource.DROPBOX,
    root = CloudFolder(DropboxClient.ROOT, "Dropbox"),
) {
    private val auth = container.dropboxAuth
    private val client = container.dropboxClient

    /** "+ COLLEGA UN ACCOUNT DROPBOX": apre il login di Dropbox. Il risultato arriva in [onResume]. */
    fun login(context: Context) = auth.startLogin(context)

    /** Al ritorno nell'app dopo il login. */
    fun onResume() {
        val credential = auth.takeLoginResult() ?: return
        viewModelScope.launch {
            _busy.value = true
            try {
                activate(withContext(Dispatchers.IO) { auth.register(credential) })
            } catch (e: Exception) {
                _messages.tryEmit("Dropbox ha detto no (${e.message}).")
            } finally {
                _busy.value = false
            }
        }
    }

    override fun forget(email: String) = auth.forget(email)

    override fun listFolders(email: String, folderId: String) = client.listFolders(email, folderId)

    override fun scanner(email: String, folderId: String) =
        DropboxSourceScanner(email, folderId, client, container.metadataReader)

    /** Con Dropbox non c'è un consenso da rinnovare: il refresh token basta, finché l'utente non lo revoca. */
    override fun authorizeAndActivate(email: String) {
        viewModelScope.launch {
            _busy.value = true
            try {
                withContext(Dispatchers.IO) { auth.client(email).users().currentAccount }
                activate(email)
            } catch (e: Exception) {
                _messages.tryEmit("Dropbox non risponde per $email (${e.message}). Ricollegalo dalla select.")
            } finally {
                _busy.value = false
            }
        }
    }
}
