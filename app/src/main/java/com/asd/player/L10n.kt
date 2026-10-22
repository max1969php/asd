package com.asd.player

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Lingua dell'app, senza appcompat. Da Android 13 la gestisce il sistema (LocaleManager, e la
 * lingua compare anche nelle impostazioni di Android); prima la ricordiamo noi e la applichiamo
 * al contesto dell'activity. "" = quella del telefono.
 */
object L10n {
    /**
     * Lingue con le traduzioni in res/values-* (l'italiano è quella di base). Una lingua entra
     * qui solo quando il suo strings.xml c'è: altrimenti chi la sceglie si ritrova l'italiano.
     * Va tenuta allineata con res/xml/locales_config.xml.
     */
    val SUPPORTED = listOf(
        "it", "en", "cs", "de", "es", "fr", "hr", "hu", "nl", "pl", "pt", "ro", "sk", "sl",
    )

    private const val PREFS = "asd_settings"
    private const val KEY = "language"

    private lateinit var app: Context
    private var cached: Pair<String, Context>? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** Lingua scelta ("" = di sistema). */
    fun current(context: Context = app): String =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.let(::tagOf).orEmpty()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        }

    fun set(activity: Activity, tag: String) {
        cached = null
        if (Build.VERSION.SDK_INT >= 33) {
            // Il sistema ricrea da solo l'activity.
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
            activity.recreate()
        }
    }

    /** Per Activity.attachBaseContext: sotto Android 13 applica la lingua scelta. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = current(base)
        if (tag.isEmpty()) return base
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
    }

    /** "zh-Hans-CN" o "zh-CN" → "zh-CN"; "nb-NO" → "nb": la forma usata in [SUPPORTED]. */
    private fun tagOf(locale: Locale): String =
        if (locale.language == "zh") "zh-CN" else locale.language

    /** Il nome di una lingua scritto in quella lingua ("Deutsch", "العربية"). */
    fun nativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /** Testi per chi non ha un contesto di UI sotto mano (ViewModel, download, errori). */
    fun str(@StringRes id: Int, vararg args: Any): String = localized().getString(id, *args)

    private fun localized(): Context {
        val tag = current()
        cached?.let { (t, ctx) -> if (t == tag) return ctx }
        val ctx = if (tag.isEmpty()) app else {
            app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
        }
        cached = tag to ctx
        return ctx
    }
}
