package com.asd.player.data.dropbox

import android.content.Context
import com.asd.player.BuildConfig
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class DropboxAuthException(message: String) : IOException(message)

/**
 * Login Dropbox con OAuth PKCE: niente segreti nell'app, solo la app key (pubblica).
 * Per ogni account si conserva il refresh token; l'SDK rinnova da solo i token di accesso.
 */
class DropboxAuth(context: Context) {

    private val tokens = context.getSharedPreferences("asd_dropbox_tokens", Context.MODE_PRIVATE)
    private val clients = ConcurrentHashMap<String, DbxClientV2>()
    private val config = DbxRequestConfig.newBuilder("ASD/${BuildConfig.VERSION_NAME}").build()

    /** True tra l'apertura del login e il ritorno nell'app. */
    private var loginInProgress = false

    /** Apre il login di Dropbox (app Dropbox se c'è, altrimenti browser). */
    fun startLogin(context: Context) {
        loginInProgress = true
        Auth.startOAuth2PKCE(context, BuildConfig.DROPBOX_APP_KEY, config, SCOPES)
    }

    /** Al ritorno nell'app: la credenziale del login appena concluso, se c'è. Non tocca la rete. */
    fun takeLoginResult(): DbxCredential? {
        if (!loginInProgress) return null
        val credential = Auth.getDbxCredential() ?: return null
        loginInProgress = false
        return credential
    }

    /** Bloccante: chiede a Dropbox l'email dell'account e ne salva la credenziale. */
    fun register(credential: DbxCredential): String {
        val client = DbxClientV2(config, credential)
        val email = client.users().currentAccount.email
        tokens.edit().putString(email, DbxCredential.Writer.writeToString(credential)).apply()
        clients[email] = client
        return email
    }

    fun client(email: String): DbxClientV2 = clients.getOrPut(email) {
        val json = tokens.getString(email, null)
            ?: throw DropboxAuthException("Dropbox $email non è collegato: SETUP → Dropbox")
        DbxClientV2(config, DbxCredential.Reader.readFully(json))
    }

    fun forget(email: String) {
        clients.remove(email)
        tokens.edit().remove(email).apply()
    }

    private companion object {
        /** Devono essere spuntati anche in App Console → Permissions. */
        val SCOPES = listOf("account_info.read", "files.metadata.read", "files.content.read", "files.content.write")
    }
}
