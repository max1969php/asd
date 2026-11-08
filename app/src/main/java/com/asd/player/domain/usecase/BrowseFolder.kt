package com.asd.player.domain.usecase

import com.asd.player.domain.model.LibraryQuery
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SongSource
import java.text.Collator
import java.util.Locale

/** Come si sfoglia ROBA: per cartelle del telefono/Drive, per artista o per album (dai tag). */
enum class LibraryView { FOLDERS, ARTISTS, ALBUMS }

/**
 * Dove si è in ROBA. In [LibraryView.FOLDERS]: la fonte (null = radice con tutte le fonti, o fonte
 * unica) e le sottocartelle. In [LibraryView.ARTISTS]: [artista] o [artista, album]. In
 * [LibraryView.ALBUMS]: [album].
 */
data class FolderPath(
    val source: SongSource? = null,
    val dirs: List<String> = emptyList(),
    val view: LibraryView = LibraryView.FOLDERS,
) {
    val isRoot: Boolean get() = source == null && dirs.isEmpty()

    fun up(): FolderPath = if (dirs.isNotEmpty()) copy(dirs = dirs.dropLast(1)) else FolderPath(view = view)
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
 * ROBA a livelli: si vedono le "cartelle" del livello corrente e i suoi file audio.
 * - Cartelle: le sottocartelle vere; con più fonti (telefono e Drive) alla radice ogni fonte è una cartella.
 * - Artisti: gli artisti, poi i loro album (i pezzi senza album stanno accanto agli album).
 * - Album: gli album (i pezzi senza album stanno alla radice).
 * Dentro un album l'ordine è quello delle tracce, se i tag lo dicono.
 * Con ricerca o filtri attivi niente cartelle: i risultati di tutto quello che c'è sotto il livello corrente.
 */
class BrowseFolder(private val search: SearchSongs) {

    private val collator: Collator = Collator.getInstance(Locale.ITALIAN).apply {
        strength = Collator.PRIMARY
    }

    operator fun invoke(all: List<Song>, path: FolderPath, query: LibraryQuery): FolderView = when (path.view) {
        LibraryView.FOLDERS -> folders(all, path, query)
        LibraryView.ARTISTS -> levels(all, path, query, listOf({ it.displayArtist }, { it.albumName() }))
        LibraryView.ALBUMS -> levels(all, path, query, listOf { it.albumName() })
    }

    private fun folders(all: List<Song>, path: FolderPath, query: LibraryQuery): FolderView {
        val sources = all.mapTo(sortedSetOf()) { it.source }
        val multiSource = sources.size > 1
        val source = if (multiSource) path.source?.takeIf { it in sources } else sources.firstOrNull()

        if (source == null) {
            val below = filtered(all, query)
            val folders = if (query.filtering) emptyList() else sources.map { s ->
                FolderEntry(s.name, all.count { it.source == s }, s)
            }
            return FolderView(FolderPath(), folders, if (query.filtering) below else emptyList(), below)
        }

        val inSource = all.filter { it.source == source }
        val level = levels(inSource, path.copy(source = null), query, keys = null)
        return level.copy(path = level.path.copy(source = source.takeIf { multiSource }))
    }

    /**
     * Un livello di un albero: con [keys] null l'albero sono le cartelle dei file, altrimenti ogni
     * livello è un tag (artista, album). Un pezzo senza il tag di un livello sta tra i file di quel livello.
     */
    private fun levels(all: List<Song>, path: FolderPath, query: LibraryQuery, keys: List<(Song) -> String?>?): FolderView {
        val depth = keys?.size ?: Int.MAX_VALUE
        fun branch(song: Song): List<String> = keys?.let { k ->
            k.map { it(song) }.takeWhile { it != null }.map { it!! }
        } ?: song.folder

        var dirs = path.dirs.take(depth)
        while (dirs.isNotEmpty() && all.none { branch(it).startsWith(dirs) }) dirs = dirs.dropLast(1)
        val scope = all.filter { branch(it).startsWith(dirs) }
        val below = filtered(scope, query)
        val folders = if (query.filtering) emptyList() else scope
            .mapNotNull { song -> branch(song).getOrNull(dirs.size) }
            .groupingBy { it.lowercase() to it }
            .eachCount()
            .let { counts ->
                // "Ramones" e "RAMONES" sono lo stesso artista: un nome solo, il primo trovato.
                counts.entries.groupBy({ it.key.first }, { it.key.second to it.value })
                    .map { (_, variants) -> FolderEntry(variants.first().first, variants.sumOf { it.second }) }
            }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
        val here = below.filter { branch(it).size == dirs.size }
        val songs = when {
            query.filtering -> below
            // Dentro un album: l'ordine dei tag, se c'è (disco, poi traccia).
            keys != null && dirs.size == keys.size && here.any { it.trackNumber != null } ->
                here.sortedWith(compareBy<Song>({ it.discNumber ?: 1 }, { it.trackNumber ?: Int.MAX_VALUE }))
            else -> here
        }
        return FolderView(path.copy(dirs = dirs), folders, songs, below)
    }

    /** Ricerca e filtri; con "doppioni" solo i gruppi di pezzi uguali, uno vicino all'altro. */
    private fun filtered(songs: List<Song>, query: LibraryQuery): List<Song> {
        val found = search(songs, query)
        if (!query.duplicates) return found
        val order = found.withIndex().associate { (i, song) -> song.id to i }
        return Duplicates.groups(found)
            .map { group -> group.sortedBy { order[it.id] } }
            .sortedBy { group -> order[group.first().id] }
            .flatten()
    }

    private fun List<String>.startsWith(prefix: List<String>) =
        size >= prefix.size && prefix.indices.all { this[it].equals(prefix[it], ignoreCase = true) }
}

/** L'album come livello della vista Album: null se il tag manca. */
private fun Song.albumName(): String? = album?.trim()?.takeIf { it.isNotEmpty() }
