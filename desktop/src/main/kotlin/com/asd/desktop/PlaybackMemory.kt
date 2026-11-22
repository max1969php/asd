package com.asd.desktop

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** L'ultimo ascolto: coda (come URI dei file), pezzo, punto, shuffle e repeat. */
data class SavedPlayback(
    val uris: List<String>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeat: Repeat,
)

/** Come PlaybackMemory dell'app Android, in ~/.local/share/asd-desktop/playback.json. */
class PlaybackMemory(private val file: File = File(Paths.data, "playback.json")) {

    fun save(p: SavedPlayback) {
        runCatching {
            Paths.writeAtomically(
                file,
                JSONObject()
                    .put("uris", JSONArray(p.uris))
                    .put("index", p.index)
                    .put("positionMs", p.positionMs)
                    .put("shuffle", p.shuffle)
                    .put("repeat", p.repeat.name)
                    .toString(),
            )
        }
    }

    fun load(): SavedPlayback? = runCatching {
        val o = JSONObject(file.readText())
        val a = o.getJSONArray("uris")
        SavedPlayback(
            uris = List(a.length()) { a.getString(it) },
            index = o.optInt("index"),
            positionMs = o.optLong("positionMs"),
            shuffle = o.optBoolean("shuffle"),
            repeat = runCatching { Repeat.valueOf(o.optString("repeat")) }.getOrDefault(Repeat.OFF),
        ).takeIf { it.uris.isNotEmpty() }
    }.getOrNull()
}
