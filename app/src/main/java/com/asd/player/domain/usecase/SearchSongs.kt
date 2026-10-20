package com.asd.player.domain.usecase

import com.asd.player.domain.model.LibraryQuery
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SortField
import java.text.Collator
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
        val terms = query.text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val filtered = songs.filter { song ->
            (query.formats.isEmpty() || song.format in query.formats) &&
                (query.sources.isEmpty() || song.source in query.sources) &&
                (query.artist == null || song.displayArtist == query.artist) &&
                terms.all { term -> song.matches(term) }
        }
        val comparator = comparatorFor(query.sort.field)
        return filtered.sortedWith(if (query.sort.ascending) comparator else comparator.reversed())
    }

    private fun Song.matches(term: String): Boolean =
        title.lowercase().contains(term) ||
            fileName.lowercase().contains(term) ||
            artist?.lowercase()?.contains(term) == true ||
            album?.lowercase()?.contains(term) == true

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
