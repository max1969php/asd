package com.asd.player.domain.usecase

import com.asd.player.domain.model.LibraryQuery
import com.asd.player.domain.model.Song
import java.text.Normalizer

/**
 * "Metti … con ASD" detto all'assistente (Android Auto, Google): dal testo ai pezzi da suonare.
 * Prova nell'ordine un mixtape, un artista, un album, titolo + artista (se l'assistente li ha
 * separati), poi la ricerca di ROBA (tutte le parole, anche senza "di", "dei", "by"…).
 * Lista vuota = niente trovato; testo vuoto lo gestisce chi chiama (riprende l'ultimo ascolto).
 */
class VoiceSearch(private val search: SearchSongs = SearchSongs()) {

    /** Un mixtape come lo vede la ricerca vocale: nome e pezzi nell'ordine del nastro. */
    data class Tape(val name: String, val songs: List<Song>)

    fun find(
        query: String,
        songs: List<Song>,
        tapes: List<Tape> = emptyList(),
        artist: String? = null,
        album: String? = null,
        title: String? = null,
    ): List<Song> {
        val q = normalize(query)
        val a = artist?.let(::normalize)?.takeIf { it.isNotEmpty() }
        val b = album?.let(::normalize)?.takeIf { it.isNotEmpty() }
        val t = title?.let(::normalize)?.takeIf { it.isNotEmpty() }

        // Mixtape: "mixtape roba da corriera", "playlist palestra", o solo il nome.
        val tapeName = q.replace(TAPE_WORDS, "").trim()
        if (tapeName.isNotEmpty()) {
            tapes.firstOrNull { normalize(it.name) == tapeName }?.let { if (it.songs.isNotEmpty()) return it.songs }
        }

        if (t != null) {
            val byTitle = songs.filter { normalize(it.title) == t && (a == null || normalize(it.displayArtist) == a) }
            if (byTitle.isNotEmpty()) return byTitle
        }
        (a ?: q.takeIf { t == null && b == null })?.let { name ->
            val byArtist = songs.filter { normalize(it.displayArtist) == name }
            if (byArtist.isNotEmpty() && t == null) return inAlbumOrder(byArtist)
        }
        (b ?: q.takeIf { t == null })?.let { name ->
            val byAlbum = songs.filter { it.album != null && normalize(it.album) == name && (a == null || normalize(it.displayArtist) == a) }
            if (byAlbum.isNotEmpty()) return inAlbumOrder(byAlbum)
        }

        val text = listOfNotNull(t, a, b).joinToString(" ").ifEmpty { q }
        if (text.isEmpty()) return emptyList()
        search(songs, LibraryQuery(text = text)).let { if (it.isNotEmpty()) return it }
        val loose = text.split(' ').filter { it.isNotEmpty() && it !in CONNECTORS }.joinToString(" ")
        return if (loose.isNotEmpty() && loose != text) search(songs, LibraryQuery(text = loose)) else emptyList()
    }

    private fun inAlbumOrder(songs: List<Song>): List<Song> =
        songs.sortedWith(compareBy<Song>({ normalize(it.album ?: "") }, { it.discNumber ?: 1 }, { it.trackNumber ?: Int.MAX_VALUE }, { normalize(it.title) }))

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")
            .replace(PUNCTUATION, " ").replace(SPACES, " ").trim()

    private companion object {
        val DIACRITICS = Regex("\\p{Mn}+")
        val PUNCTUATION = Regex("[^\\p{L}\\p{N}]+")
        val SPACES = Regex("\\s+")
        val TAPE_WORDS = Regex("^(il |la |lo |the )?(mixtape|playlist|nastro|tape)\\s+")
        /** Parole che l'assistente lascia tra titolo e artista ("Blitzkrieg Bop dei Ramones"). */
        val CONNECTORS = setOf("di", "dei", "degli", "delle", "del", "della", "dello", "by", "von", "de", "des", "du")
    }
}
