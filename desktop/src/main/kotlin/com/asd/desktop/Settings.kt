package com.asd.desktop

import com.asd.player.domain.model.SortField
import com.asd.player.domain.model.SortOrder
import com.asd.player.domain.usecase.LibraryView
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Quello che ASD Desktop ricorda tra un avvio e l'altro, in ~/.config/asd-desktop/settings.json. */
data class DesktopSettings(
    /** La cartella della musica; null = non ancora scelta. */
    val folder: String? = null,
    val sort: SortOrder = SortOrder(),
    val view: LibraryView = LibraryView.FOLDERS,
    /** Le sottocartelle (o artista/album) aperte in ROBA. */
    val path: List<String> = emptyList(),
    /** Il primo pezzo visibile della lista, per tornare allo stesso punto. */
    val scroll: Int = 0,
    /** Volume uniforme a -14 LUFS, come nell'app: acceso di default. */
    val normalizeVolume: Boolean = true,
    /** Silenzio a inizio e fine saltato: acceso di default. */
    val skipSilence: Boolean = true,
    /** Lingua scelta (tag di I18n.SUPPORTED); null = come il sistema. */
    val language: String? = null,
    /** Browser da cui yt-dlp prende i cookie di YouTube (contro "non sei un bot"); null = nessuno. */
    val ytBrowser: String? = null,
    /** L'albero delle cartelle a sinistra in ROBA: acceso di default. */
    val showTree: Boolean = true,
)

class SettingsStore(private val file: File = File(Paths.config, "settings.json")) {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<DesktopSettings> = _state.asStateFlow()

    fun update(change: (DesktopSettings) -> DesktopSettings) {
        val next = change(_state.value)
        if (next == _state.value) return
        _state.value = next
        runCatching { Paths.writeAtomically(file, toJson(next).toString(2)) }
    }

    private fun load(): DesktopSettings = runCatching {
        val o = JSONObject(file.readText())
        DesktopSettings(
            folder = o.optString("folder").takeIf { it.isNotEmpty() },
            sort = SortOrder(
                field = runCatching { SortField.valueOf(o.optString("sortField")) }.getOrDefault(SortField.TITLE),
                ascending = o.optBoolean("sortAscending", true),
            ),
            view = runCatching { LibraryView.valueOf(o.optString("view")) }.getOrDefault(LibraryView.FOLDERS),
            path = o.optJSONArray("path")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty(),
            scroll = o.optInt("scroll", 0),
            normalizeVolume = o.optBoolean("normalizeVolume", true),
            skipSilence = o.optBoolean("skipSilence", true),
            language = o.optString("language").takeIf { it.isNotEmpty() },
            ytBrowser = o.optString("ytBrowser").takeIf { it.isNotEmpty() },
            showTree = o.optBoolean("showTree", true),
        )
    }.getOrDefault(DesktopSettings())

    private fun toJson(s: DesktopSettings) = JSONObject()
        .put("folder", s.folder ?: "")
        .put("sortField", s.sort.field.name)
        .put("sortAscending", s.sort.ascending)
        .put("view", s.view.name)
        .put("path", JSONArray(s.path))
        .put("scroll", s.scroll)
        .put("normalizeVolume", s.normalizeVolume)
        .put("skipSilence", s.skipSilence)
        .put("language", s.language ?: "")
        .put("ytBrowser", s.ytBrowser ?: "")
        .put("showTree", s.showTree)
}
