package com.asd.desktop

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Un mixtape: nome e pezzi in ordine, per URI (un file sparito si salta, non si perde). */
data class Mixtape(
    val id: Long,
    val name: String,
    val uris: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * I mixtape di ASD Desktop, in ~/.local/share/asd-desktop/playlists.json. Come nell'app vivono
 * solo qui: cancellarne uno non tocca i file audio.
 */
class Mixtapes(private val file: File = File(Paths.data, "playlists.json")) {

    private val _all = MutableStateFlow(load())
    /** Dal più recente (ultima modifica) al più vecchio, come nell'app. */
    val all: StateFlow<List<Mixtape>> = _all.asStateFlow()

    @Synchronized
    fun create(name: String, uris: List<String> = emptyList()): Long {
        val now = System.currentTimeMillis()
        val id = (_all.value.maxOfOrNull { it.id } ?: 0) + 1
        save(listOf(Mixtape(id, name.trim(), uris.distinct(), now, now)) + _all.value)
        return id
    }

    fun rename(id: Long, name: String) = change(id) { it.copy(name = name.trim()) }

    @Synchronized
    fun delete(id: Long) = save(_all.value.filterNot { it.id == id })

    /** I pezzi già dentro non si duplicano. */
    fun add(id: Long, uris: List<String>) = change(id) { m -> m.copy(uris = m.uris + uris.filterNot { it in m.uris }.distinct()) }

    fun remove(id: Long, index: Int) = change(id) { m -> m.copy(uris = m.uris.filterIndexed { i, _ -> i != index }) }

    fun move(id: Long, from: Int, to: Int) = change(id) { m ->
        if (from !in m.uris.indices || to !in m.uris.indices) return@change m
        val list = m.uris.toMutableList()
        list.add(to, list.removeAt(from))
        m.copy(uris = list)
    }

    @Synchronized
    private fun change(id: Long, edit: (Mixtape) -> Mixtape) {
        val old = _all.value.firstOrNull { it.id == id } ?: return
        val new = edit(old)
        if (new == old) return
        val touched = new.copy(updatedAt = System.currentTimeMillis())
        save(listOf(touched) + _all.value.filterNot { it.id == id })
    }

    private fun save(list: List<Mixtape>) {
        _all.value = list
        val a = JSONArray()
        list.forEach { m ->
            a.put(
                JSONObject().put("id", m.id).put("name", m.name).put("uris", JSONArray(m.uris))
                    .put("createdAt", m.createdAt).put("updatedAt", m.updatedAt),
            )
        }
        runCatching { Paths.writeAtomically(file, JSONObject().put("version", 1).put("playlists", a).toString(2)) }
    }

    private fun load(): List<Mixtape> = runCatching {
        val a = JSONObject(file.readText()).getJSONArray("playlists")
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            val u = o.getJSONArray("uris")
            Mixtape(o.getLong("id"), o.getString("name"), List(u.length()) { u.getString(it) }, o.optLong("createdAt"), o.optLong("updatedAt"))
        }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())
}
