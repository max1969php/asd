package com.asd.player.domain.usecase

import com.asd.player.domain.model.LibraryQuery
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SongSource
import java.text.Collator
import java.util.Locale

/** Dove si è in ROBA: la fonte (null = radice con tutte le fonti, o fonte unica) e le sottocartelle. */
data class FolderPath(
    val source: SongSource? = null,
    val dirs: List<String> = emptyList(),
) {
    val isRoot: Boolean get() = source == null && dirs.isEmpty()

    fun up(): FolderPath = if (dirs.isNotEmpty()) copy(dirs = dirs.dropLast(1)) else FolderPath()
}

/** Una riga cartella: [source] valorizzata solo per le fonti mostrate come cartelle alla radice. */
data class FolderEntry(val name: String, val songCount: Int, val source: SongSource? = null)

data class FolderView(
    /** Il percorso davvero mostrato: se una cartella è sparita si risale alla più vicina che c'è. */
    val path: FolderPath,
    val folders: List<FolderEntry>,
    /** I file di questa cartella; con ricerca o filtri attivi, tutti quelli trovati da qui in giù. */
    val songs: List<Song>,
    /** Tutti i brani da qui in giù che passano i filtri: per "suona tutto" e "shuffle". */
    val below: List<Song>,
)

/**
 * ROBA come cartelle: si vedono le sottocartelle della cartella corrente e i suoi file audio.
 * Con più fonti (telefono e Drive) alla radice ogni fonte è una cartella.
 * Con ricerca o filtri attivi niente cartelle: i risultati di tutto l'albero sotto la cartella corrente.
 */
class BrowseFolder(private val search: SearchSongs) {

    private val collator: Collator = Collator.getInstance(Locale.ITALIAN).apply {
        strength = Collator.PRIMARY
    }

    operator fun invoke(all: List<Song>, path: FolderPath, query: LibraryQuery): FolderView {
        val filtering = query.text.isNotBlank() || query.formats.isNotEmpty() ||
            query.sources.isNotEmpty() || query.artist != null
        val sources = all.mapTo(sortedSetOf()) { it.source }
        val multiSource = sources.size > 1
        val source = if (multiSource) path.source?.takeIf { it in sources } else sources.firstOrNull()

        if (source == null) {
            val below = search(all, query)
            val folders = if (filtering) emptyList() else sources.map { s ->
                FolderEntry(s.name, all.count { it.source == s }, s)
            }
            return FolderView(FolderPath(), folders, if (filtering) below else emptyList(), below)
        }

        val inSource = all.filter { it.source == source }
        var dirs = path.dirs
        while (dirs.isNotEmpty() && inSource.none { it.folder.startsWith(dirs) }) dirs = dirs.dropLast(1)
        val scope = inSource.filter { it.folder.startsWith(dirs) }
        val below = search(scope, query)
        val folders = if (filtering) emptyList() else scope
            .filter { it.folder.size > dirs.size }
            .groupingBy { it.folder[dirs.size] }
            .eachCount()
            .map { (name, count) -> FolderEntry(name, count) }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
        val songs = if (filtering) below else below.filter { it.folder.size == dirs.size }
        return FolderView(FolderPath(source.takeIf { multiSource }, dirs), folders, songs, below)
    }

    private fun List<String>.startsWith(prefix: List<String>) =
        size >= prefix.size && subList(0, prefix.size) == prefix
}
