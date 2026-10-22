package com.asd.player.data.drive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.asd.player.L10n
import com.asd.player.R
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Il refresh token non vale più (revocato, o scaduto: in stato Test Google lo tiene 7 giorni). */
class NeedsLoginException(message: String) : DriveAuthException(message)

/**
 * Drive col progetto Google dell'utente invece che con quello di ASD.
 *
 * Un client OAuth Android è legato a pacchetto + SHA-1, che Google vuole unici fra tutti i
 * progetti: l'APK pubblicato può stare in un solo progetto. Per questo qui si usa un client
 * "App desktop" creato dall'utente: login nel browser del telefono, risposta raccolta da un
 * server su 127.0.0.1 (redirect di loopback, con PKCE), poi refresh token salvato.
 *
 * Client e token stanno in un file di preferenze escluso dal backup: dopo una reinstallazione
 * si ricollega da capo.
 */
class OwnGoogleClient(private val context: Context) {

    data class Config(val clientId: String, val clientSecret: String)

    data class Login(val email: String, val accessToken: String, val expiresAt: Long)

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    /** null = si usa il client di ASD (account del telefono). */
    val config: StateFlow<Config?> = _config.asStateFlow()

    fun save(clientId: String, clientSecret: String) {
        prefs.edit().clear().putString(KEY_ID, clientId.trim()).putString(KEY_SECRET, clientSecret.trim()).apply()
        _config.value = loadConfig()
    }

    /** Toglie il progetto e con lui tutti i token: si torna al client di ASD. */
    fun clear() {
        prefs.edit().clear().apply()
        _config.value = null
    }

    fun forget(email: String) {
        prefs.edit().remove(refreshKey(email)).apply()
    }

    /** Bloccante: un access token nuovo dal refresh token di [email]. */
    fun refresh(email: String): Login {
        val config = requireConfig()
        val refreshToken = prefs.getString(refreshKey(email), null)
            ?: throw NeedsLoginException(L10n.str(R.string.consent_again, email))
        val json = post(
            mapOf(
                "client_id" to config.clientId,
                "client_secret" to config.clientSecret,
                "refresh_token" to refreshToken,
                "grant_type" to "refresh_token",
            ),
            email,
        )
        return Login(email, json.getString("access_token"), expiresAt(json))
    }

    /**
     * Apre il login di Google nel browser e aspetta che torni sul server locale.
     * [hint] precompila l'account; l'account vero è quello che l'utente sceglie.
     */
    suspend fun login(hint: String?): Login = withContext(Dispatchers.IO) {
        val config = requireConfig()
        ServerSocket(0, 8, InetAddress.getByName(LOOPBACK)).use { server ->
            val redirect = "http://$LOOPBACK:${server.localPort}"
            val verifier = randomToken(48)
            val state = randomToken(16)
            val url = Uri.parse(AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", config.clientId)
                .appendQueryParameter("redirect_uri", redirect)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", "openid email ${DriveAuth.DRIVE}")
                .appendQueryParameter("code_challenge", sha256(verifier))
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("state", state)
                .appendQueryParameter("access_type", "offline")
                // Senza "consent" Google dà il refresh token solo al primo accesso.
                .appendQueryParameter("prompt", "consent select_account")
                .apply { hint?.let { appendQueryParameter("login_hint", it) } }
                .build()
            val results = LinkedBlockingQueue<Result<String>>()
            serve(server, state, results)
            // Senza il servizio Android congela ASD mentre si è nel browser, e il server non risponde.
            DriveLoginService.start(context)
            val code = try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                runInterruptible {
                    results.poll(LOGIN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        ?: throw DriveAuthException(L10n.str(R.string.drive_login_timeout))
                }.getOrThrow()
            } finally {
                DriveLoginService.stop(context)
            }
            val json = post(
                mapOf(
                    "client_id" to config.clientId,
                    "client_secret" to config.clientSecret,
                    "code" to code,
                    "code_verifier" to verifier,
                    "redirect_uri" to redirect,
                    "grant_type" to "authorization_code",
                ),
                null,
            )
            val email = emailFrom(json.getString("id_token"))
            val refreshToken = json.optString("refresh_token").ifEmpty {
                throw DriveAuthException(L10n.str(R.string.drive_login_refused, "no refresh_token"))
            }
            prefs.edit().putString(refreshKey(email), refreshToken).apply()
            Login(email, json.getString("access_token"), expiresAt(json))
        }
    }

    /**
     * Accetta le connessioni del browser finché il socket resta aperto, ognuna nel suo thread:
     * Chrome può aprirne di "preventive" senza mandarci niente, e non devono bloccare le altre.
     * In [results] finisce il codice di autorizzazione, o l'errore di Google.
     */
    private fun serve(server: ServerSocket, state: String, results: LinkedBlockingQueue<Result<String>>) {
        thread(isDaemon = true, name = "asd-oauth") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { socket.use { handle(it, state)?.let(results::put) } }
            }
        }
    }

    private fun handle(socket: Socket, state: String): Result<String>? {
        socket.soTimeout = 30_000
        val reader = socket.getInputStream().bufferedReader()
        val requestLine = runCatching { reader.readLine() }.getOrNull() ?: return null
        // Leggiamo tutti gli header: chiudere con byte non letti fa arrivare al browser un reset.
        runCatching { while (!reader.readLine().isNullOrEmpty()) Unit }
        val query = Uri.parse("http://$LOOPBACK" + requestLine.split(" ").getOrElse(1) { "/" })
        val code = query.getQueryParameter("code")
        val error = query.getQueryParameter("error")
        if (code == null && error == null) {
            runCatching { respond(socket, 404, "") }  // favicon e simili
            return null
        }
        runCatching { respond(socket, 200, page()) }
        return when {
            error != null -> Result.failure(DriveAuthException(L10n.str(R.string.drive_login_refused, error)))
            query.getQueryParameter("state") != state -> Result.failure(DriveAuthException(L10n.str(R.string.drive_login_refused, "state")))
            else -> Result.success(code!!)
        }
    }

    private fun respond(socket: Socket, status: Int, html: String) {
        val body = html.toByteArray()
        val head = "HTTP/1.1 $status ${if (status == 200) "OK" else "Not Found"}\r\n" +
            "Content-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(head.toByteArray())
            write(body)
            flush()
        }
    }

    /** La pagina che il browser mostra a login finito: un bottone riporta in ASD. */
    private fun page(): String {
        val done = L10n.str(R.string.drive_login_done)
        return """<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width">
            |<title>ASD</title><body style="margin:0;padding:32px 16px;background:#0b0b0b;color:#ede8da;font:18px monospace">
            |<h1 style="font:900 64px Arial Black,Arial,sans-serif;margin:0">ASD</h1><p>$done</p>
            |<p><a href="$RETURN_URI" style="display:inline-block;padding:12px 18px;background:#d7ff1f;color:#0b0b0b;
            |font:900 16px Arial Black,Arial,sans-serif;text-decoration:none;box-shadow:5px 5px 0 #ff2b2b">ASD ↩</a></p>
            |</body>""".trimMargin()
    }

    private fun post(params: Map<String, String>, email: String?): JSONObject {
        val connection = (URL(TOKEN_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            val body = params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (status in 200..299) return json
            val error = json.optString("error").ifEmpty { "HTTP $status" }
            if (error == "invalid_grant" && email != null) {
                forget(email)
                throw NeedsLoginException(L10n.str(R.string.consent_again, email))
            }
            throw DriveAuthException(
                L10n.str(R.string.drive_login_refused, listOf(error, json.optString("error_description")).filter { it.isNotEmpty() }.joinToString(": "))
            )
        } catch (e: IOException) {
            if (e is DriveAuthException) throw e
            throw DriveAuthException(L10n.str(R.string.google_not_answering, e.message.orEmpty()))
        } finally {
            connection.disconnect()
        }
    }

    private fun requireConfig() = _config.value ?: throw DriveAuthException(L10n.str(R.string.no_drive_account))

    private fun loadConfig(): Config? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        return Config(id, prefs.getString(KEY_SECRET, null).orEmpty())
    }

    private fun refreshKey(email: String) = "refresh_$email"

    companion object {
        /** Fuori da backup_rules / data_extraction_rules, che includono solo asd_settings. */
        private const val PREFS = "asd_drive_own"
        private const val KEY_ID = "client_id"
        private const val KEY_SECRET = "client_secret"
        private const val LOOPBACK = "127.0.0.1"
        private const val LOGIN_TIMEOUT_MS = 5 * 60_000L
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        /** Gestito da MainActivity: riporta l'utente in ASD dalla pagina finale. */
        const val RETURN_URI = "asd://drive"

        /** I client OAuth di Google finiscono tutti così. */
        fun isValidClientId(id: String) = id.trim().endsWith(".apps.googleusercontent.com")

        private fun expiresAt(json: JSONObject) =
            System.currentTimeMillis() + (json.optLong("expires_in", 3600) - 300).coerceAtLeast(60) * 1000

        /** L'email sta nel payload dell'id_token, arrivato direttamente da Google via HTTPS. */
        private fun emailFrom(idToken: String): String {
            val payload = idToken.split(".").getOrNull(1) ?: throw DriveAuthException(L10n.str(R.string.drive_login_refused, "id_token"))
            val json = JSONObject(String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
            return json.optString("email").ifEmpty { throw DriveAuthException(L10n.str(R.string.drive_login_refused, "email")) }
        }

        private fun randomToken(bytes: Int): String =
            ByteArray(bytes).also(SecureRandom()::nextBytes).let(::base64Url)

        private fun sha256(text: String) = base64Url(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))

        private fun base64Url(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
