package com.asd.desktop

import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.w3c.dom.Element

/**
 * Le lingue di ASD Desktop. I testi sono gli strings.xml dell'app Android, copiati nel jar al
 * momento della build (task copyStrings): prima quelli di src/main, poi sopra quelli della minimal
 * (senza Drive), poi i pochi testi solo desktop (resources/i18n/desktop). Base italiana, come l'app.
 * Un testo solo desktop che manca in una lingua si vede in inglese.
 */
object I18n {

    /** Le stesse lingue dell'app, nello stesso ordine (L10n.SUPPORTED). */
    val SUPPORTED = listOf(
        "it", "en", "bg", "cs", "da", "de", "el", "es", "et", "fi", "fr", "ga", "hr", "hu", "lt", "lv", "mt",
        "nl", "pl", "pt", "ro", "sk", "sl", "sv", "nb", "uk", "zh-CN", "hi", "ar", "fa", "sw", "wo", "ha",
        "yo", "ig", "am", "ti", "so",
    )

    /** Nomi nella propria lingua, come L10n.NAMES dell'app e LANGUAGES del sito. */
    private val NAMES = mapOf(
        "it" to "Italiano", "en" to "English", "bg" to "Български", "cs" to "Čeština", "da" to "Dansk",
        "de" to "Deutsch", "el" to "Ελληνικά", "es" to "Español", "et" to "Eesti", "fi" to "Suomi",
        "fr" to "Français", "ga" to "Gaeilge", "hr" to "Hrvatski", "hu" to "Magyar", "lt" to "Lietuvių",
        "lv" to "Latviešu", "mt" to "Malti", "nl" to "Nederlands", "pl" to "Polski", "pt" to "Português",
        "ro" to "Română", "sk" to "Slovenčina", "sl" to "Slovenščina", "sv" to "Svenska", "nb" to "Norsk bokmål",
        "uk" to "Українська", "zh-CN" to "简体中文", "hi" to "हिन्दी", "ar" to "العربية", "fa" to "فارسی",
        "sw" to "Kiswahili", "wo" to "Wolof", "ha" to "Hausa", "yo" to "Yorùbá", "ig" to "Igbo", "am" to "አማርኛ",
        "ti" to "ትግርኛ", "so" to "Soomaali",
    )

    private val RTL = setOf("ar", "fa")

    private val _language = MutableStateFlow("it")
    /** La lingua in uso (tag come in [SUPPORTED]). */
    val language: StateFlow<String> = _language.asStateFlow()

    @Volatile private var strings: Map<String, String> = emptyMap()
    @Volatile private var locale: Locale = Locale.ITALIAN

    val isRtl: Boolean get() = _language.value in RTL

    fun nativeName(tag: String): String = NAMES[tag] ?: tag

    /** La lingua del sistema, se ASD ce l'ha; altrimenti l'inglese. */
    fun systemLanguage(): String {
        val l = Locale.getDefault()
        val tag = when (l.language) {
            "zh" -> "zh-CN"
            "no", "nn" -> "nb"
            else -> l.language
        }
        return tag.takeIf { it in SUPPORTED } ?: "en"
    }

    /** [choice] null = come il sistema. */
    fun use(choice: String?) {
        val tag = choice?.takeIf { it in SUPPORTED } ?: systemLanguage()
        val map = HashMap<String, String>()
        // Base italiana, poi la lingua; per ogni livello: app, minimal, desktop (inglese se manca).
        for (layer in listOf("main", "minimal")) {
            load("$layer/values", map)
            if (tag != "it") load("$layer/${folder(tag)}", map)
        }
        load("desktop/values", map)
        if (tag != "it") {
            load("desktop/values-en", map)
            if (tag != "en") load("desktop/${folder(tag)}", map)
        }
        strings = map
        locale = Locale.forLanguageTag(tag)
        _language.value = tag
    }

    /** Il testo [key], con gli argomenti alla Android (%1$s, %1$d). Chiave sconosciuta: la chiave stessa. */
    fun t(key: String, vararg args: Any): String {
        val s = strings[key] ?: return key
        return if (args.isEmpty()) s.replace("%%", "%") else runCatching { String.format(locale, s, *args) }.getOrDefault(s)
    }

    /** "zh-CN" → values-zh-rCN, "nb" → values-nb. */
    private fun folder(tag: String): String =
        "values-" + if ('-' in tag) tag.substringBefore('-') + "-r" + tag.substringAfter('-') else tag

    private fun load(path: String, into: MutableMap<String, String>) {
        val stream = I18n::class.java.getResourceAsStream("/i18n/$path/strings.xml") ?: return
        runCatching {
            val doc = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
            val nodes = doc.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as Element
                into[e.getAttribute("name")] = unescape(e.textContent)
            }
        }
    }

    /** Le sequenze di Android: \' \" \n \t \@ \?. */
    internal fun unescape(raw: String): String {
        val s = raw.trim().removeSurrounding("\"")
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    else -> out.append(n)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
