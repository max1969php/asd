package com.asd.player.data.tube

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * I cookie di YouTube per yt-dlp. Quando YouTube chiede "conferma di non essere un bot"
 * vuole una sessione con un account: o si accede dentro ASD (WebView) o si importa un cookies.txt.
 * Il file resta nella memoria privata dell'app.
 */
class TubeCookies(context: Context) {

    enum class Origin { NONE, LOGIN, FILE }

    val file = File(context.filesDir, "youtube_cookies.txt")
    private val prefs = context.getSharedPreferences("asd_tube", Context.MODE_PRIVATE)

    private val _origin = MutableStateFlow(
        if (file.exists()) prefs.getString(KEY_ORIGIN, null)?.let { runCatching { Origin.valueOf(it) }.getOrNull() } ?: Origin.FILE
        else Origin.NONE
    )
    val origin: StateFlow<Origin> = _origin.asStateFlow()

    /** Dopo il login nella WebView: true se YouTube ci considera davvero dentro un account. */
    fun saveFromLogin(): Boolean {
        val header = CookieManager.getInstance().getCookie(YOUTUBE) ?: return false
        if (!isLoggedIn(header)) return false
        file.writeText(toNetscape(header))
        setOrigin(Origin.LOGIN)
        return true
    }

    /**
     * Prima di ogni download: YouTube ruota i cookie di sessione, quindi rileggiamo quelli
     * freschi della WebView. Un file importato invece lo aggiorna yt-dlp stesso.
     */
    fun refresh() {
        if (_origin.value == Origin.LOGIN) runCatching { saveFromLogin() }
    }

    fun import(input: InputStream) {
        val text = input.bufferedReader().use { it.readText() }
        if (!isNetscapeFile(text)) throw IOException("Non è un cookies.txt in formato Netscape")
        if (!isLoggedIn(text)) throw IOException("Nel file non c'è un accesso a YouTube")
        file.writeText(text)
        setOrigin(Origin.FILE)
    }

    fun clear() {
        file.delete()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        setOrigin(Origin.NONE)
    }

    private fun setOrigin(origin: Origin) {
        prefs.edit().putString(KEY_ORIGIN, origin.name).apply()
        _origin.value = origin
    }

    companion object {
        const val YOUTUBE = "https://www.youtube.com"
        const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true" +
            "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3D%252F"
        private const val KEY_ORIGIN = "cookies_origin"

        /** I cookie che YouTube imposta solo a chi ha fatto l'accesso. */
        private val LOGIN_COOKIES = listOf("SAPISID", "__Secure-3PAPISID", "__Secure-3PSID", "LOGIN_INFO")

        fun isLoggedIn(cookies: String): Boolean = LOGIN_COOKIES.any { name ->
            Regex("""(^|[\s;\t])${Regex.escape(name)}(=|\t)""").containsMatchIn(cookies)
        }

        fun isNetscapeFile(text: String): Boolean {
            val first = text.trimStart('﻿').lineSequence().firstOrNull()?.trim().orEmpty()
            return first.startsWith("# Netscape HTTP Cookie File") || first.startsWith("# HTTP Cookie File")
        }

        /**
         * "a=1; b=2" (come lo dà la WebView, senza domini né scadenze) → cookies.txt per yt-dlp.
         * Tutti su .youtube.com, solo HTTPS, scadenza tra un anno.
         */
        fun toNetscape(cookieHeader: String, now: Long = System.currentTimeMillis()): String {
            val expiry = (now + TimeUnit.DAYS.toMillis(365)) / 1000
            return buildString {
                append("# Netscape HTTP Cookie File\n")
                cookieHeader.split(";").map { it.trim() }.filter { '=' in it }.forEach { pair ->
                    val name = pair.substringBefore('=')
                    val value = pair.substringAfter('=')
                    append(".youtube.com\tTRUE\t/\tTRUE\t$expiry\t$name\t$value\n")
                }
            }
        }
    }
}
