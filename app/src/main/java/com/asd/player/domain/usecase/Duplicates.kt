package com.asd.player.domain.usecase

import com.asd.player.domain.model.Song
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

/**
 * Pezzi probabilmente uguali: stesso artista e titolo (già puliti, senza accenti e maiuscole),
 * oppure stessa durata (±1 s) e dimensione quasi uguale (±2%). ASD li mostra soltanto: cancellarli
 * tocca all'utente, dal telefono.
 */
object Duplicates {

    /** I gruppi di doppioni, ognuno con almeno due pezzi. */
    fun groups(songs: List<Song>): List<List<Song>> {
        val parent = IntArray(songs.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) x = parent[x].also { parent[x] = parent[parent[x]] }
            return x
        }
        fun union(a: Int, b: Int) {
            parent[find(a)] = find(b)
        }

        songs.indices.groupBy { key(songs[it]) }.values.forEach { same -> same.drop(1).forEach { union(it, same[0]) } }

        val byDuration = songs.indices.filter { songs[it].durationMs > 0 && songs[it].fileSize > 0 }
            .sortedBy { songs[it].durationMs }
        for ((n, i) in byDuration.withIndex()) {
            for (j in byDuration.subList(n + 1, byDuration.size)) {
                if (songs[j].durationMs - songs[i].durationMs > MAX_DURATION_DIFF_MS) break
                val a = songs[i].fileSize
                val b = songs[j].fileSize
                if (abs(a - b).toDouble() / max(a, b) <= MAX_SIZE_DIFF) union(i, j)
            }
        }
        return songs.indices.groupBy(::find).values.filter { it.size > 1 }.map { group -> group.map { songs[it] } }
    }

    private fun key(song: Song): String =
        Normalizer.normalize("${song.displayArtist}\u0000${song.title}".lowercase().trim(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")

    private const val MAX_DURATION_DIFF_MS = 1_000L
    private const val MAX_SIZE_DIFF = 0.02
    private val DIACRITICS = Regex("\\p{Mn}+")
}
