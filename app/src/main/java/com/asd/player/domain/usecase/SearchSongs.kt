package com.asd.player.domain.usecase

import com.asd.player.domain.model.LibraryQuery
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SortField
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * Applica ricerca, filtri e ordinamento al catalogo già indicizzato.
 * Lavora in memoria: per qualche migliaio di brani è più che sufficiente.
 */
class SearchSongs {

    private val collator: Collator = Collator.getInstance(Locale.ITALIAN).apply {
        strength = Collator.PRIMARY
    }

    operator fun invoke(songs: List<Song>, query: LibraryQuery): List<Song> {
        val terms = normalize(query.text).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val filtered = songs.filter { song ->
            (query.formats.isEmpty() || song.format in query.formats) &&
                (query.sources.isEmpty() || song.source in query.sources) &&
                // Il menu artisti accorpa "Ramones" e "RAMONES": il confronto deve fare lo stesso.
                (query.artist == null || song.displayArtist.equals(query.artist, ignoreCase = true)) &&
                (terms.isEmpty() || song.searchable().let { text -> terms.all { it in text } })
        }
        val comparator = comparatorFor(query.sort.field)
        return filtered.sortedWith(if (query.sort.ascending) comparator else comparator.reversed())
    }

    private fun Song.searchable(): String =
        normalize(listOfNotNull(title, fileName, artist, album).joinToString("\u0000"))

    /** Minuscolo e senza accenti: "canta" trova "Tanto pè cantà". */
    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

    private companion object {
        val DIACRITICS = Regex("\\p{Mn}+")
    }

    private fun comparatorFor(field: SortField): Comparator<Song> {
        val byTitle = Comparator<Song> { a, b -> collator.compare(a.title, b.title) }
        return when (field) {
            SortField.TITLE -> byTitle
            SortField.ARTIST -> Comparator<Song> { a, b -> collator.compare(a.displayArtist, b.displayArtist) }
                .then(byTitle)
            SortField.ALBUM -> Comparator<Song> { a, b -> collator.compare(a.album.orEmpty(), b.album.orEmpty()) }
                .then(byTitle)
            SortField.DURATION -> compareBy<Song> { it.durationMs }.then(byTitle)
            SortField.RECENT -> compareBy<Song> { it.modifiedAt }.then(byTitle)
        }
    }
}
