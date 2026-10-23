package com.asd.player.data.drive

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.asd.player.L10n
import com.asd.player.R
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class DriveAuthException(message: String) : IOException(message)

/**
 * Token OAuth per Drive, sempre legati a un account preciso del telefono.
 * Il consenso si chiede una volta (dalla UI), poi i token si rinnovano in silenzio.
 */
class DriveAuth(context: Context) {

    sealed interface Outcome {
        data class Granted(val token: String) : Outcome
        data class NeedsConsent(val intent: PendingIntent) : Outcome
    }

    private val client = Identity.getAuthorizationClient(context)
    private val cache = ConcurrentHashMap<String, Pair<String, Long>>()

    /** Dalla UI: se Google vuole il consenso dell'utente, restituisce l'intent da lanciare. */
    suspend fun authorize(email: String): Outcome = suspendCancellableCoroutine { cont ->
        client.authorize(request(email))
            .addOnSuccessListener { cont.resume(it.toOutcome(email)) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    fun onConsentResult(email: String, data: Intent?): String {
        val result = client.getAuthorizationResultFromIntent(data)
        return (result.toOutcome(email) as? Outcome.Granted)?.token
            ?: throw DriveAuthException(L10n.str(R.string.consent_denied))
    }

    /** Bloccante, da chiamare solo fuori dal main thread (scansione, player). */
    fun token(email: String): String {
        cache[email]?.let { (token, expiresAt) -> if (System.currentTimeMillis() < expiresAt) return token }
        val result = try {
            Tasks.await(client.authorize(request(email)), 30, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw DriveAuthException(L10n.str(R.string.google_not_answering, e.message.orEmpty()))
        }
        return when (val outcome = result.toOutcome(email)) {
            is Outcome.Granted -> outcome.token
            is Outcome.NeedsConsent -> throw DriveAuthException(L10n.str(R.string.consent_again, email))
        }
    }

    /** Dopo un 401: il prossimo token() ne chiede uno nuovo. */
    fun invalidate(email: String) {
        cache.remove(email)
    }

    private fun request(email: String) = AuthorizationRequest.Builder()
        .setRequestedScopes(listOf(Scope(DRIVE)))
        .setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
        .build()

    private fun AuthorizationResult.toOutcome(email: String): Outcome {
        if (hasResolution()) return Outcome.NeedsConsent(pendingIntent!!)
        val token = accessToken ?: throw DriveAuthException(L10n.str(R.string.no_token, email))
        // I token durano un'ora: li rinnoviamo un po' prima.
        cache[email] = token to System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(45)
        return Outcome.Granted(token)
    }

    companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        /**
         * Accesso completo: serve per caricare i brani dal Tubo nella cartella scelta
         * (drive.file non permette di scrivere in cartelle create da altri). ASD non cancella né modifica niente.
         */
        const val DRIVE = "https://www.googleapis.com/auth/drive"
    }
}
