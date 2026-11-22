package com.asd.player.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.asd.player.AppContainer
import com.asd.player.L10n
import com.asd.player.R
import com.asd.player.domain.model.LibraryQuery
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SongSource
import com.asd.player.domain.usecase.FolderPath
import com.asd.player.domain.usecase.LibraryView
import com.asd.player.domain.usecase.VoiceSearch
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.flow.first

/**
 * ASD visto da Android Auto (e da ogni altro "browser" di media): quattro schede, ROBA per
 * cartelle, Artisti, Album e Mixtape, con dentro le stesse cartelle di ROBA. Toccando un pezzo
 * suona tutta la sua cartella (o mixtape, o ricerca) da lì; in cima a ogni elenco ci sono Tutto e
 * Caos. Le richieste vocali passano da [VoiceSearch].
 *
 * Id dei nodi: "root"; "v|VISTA|FONTE|dir/dir" per una cartella (nomi codificati); "p" per i
 * mixtape e "p|id" per uno; "q|testo" per una ricerca. Id dei pezzi: "s␟nodo␟uri", e
 * "all␟nodo" / "shuffle␟nodo" per Tutto e Caos.
 */
class CarLibrary(private val container: AppContainer) {

    private val voice = VoiceSearch(container.searchSongs)

    fun root(): MediaItem = folder(ROOT, "ASD")

    /** I figli di un nodo; null se il nodo non esiste (più). */
    suspend fun children(parentId: String): List<MediaItem>? {
        if (parentId == ROOT) {
            return listOf(
                folder(node(FolderPath(view = LibraryView.FOLDERS)), L10n.str(R.string.tab_library)),
                folder(node(FolderPath(view = LibraryView.ARTISTS)), L10n.str(R.string.view_artists)),
                folder(node(FolderPath(view = LibraryView.ALBUMS)), L10n.str(R.string.view_albums)),
                folder(TAPES, L10n.str(R.string.tab_playlists)),
            )
        }
        if (parentId == TAPES) {
            return container.playlistRepository.playlists.first().map { folder("$TAPES|${it.id}", it.name, subtitle = count(it.songCount)) }
        }
        val path = pathOf(parentId)
        if (path != null) {
            val view = container.browseFolder(songs(), path, query())
            val folders = view.folders.map { f ->
                val child = if (f.source != null) FolderPath(f.source, view = path.view) else view.path.copy(dirs = view.path.dirs + f.name)
                folder(node(child), f.name, subtitle = count(f.songCount))
            }
            return playAllItems(parentId, view.below) + folders + view.songs.map { song(parentId, it) }
        }
        val queue = queueOf(parentId, below = false) ?: return null
        return playAllItems(parentId, queue) + queue.map { song(parentId, it) }
    }

    /** Ricerca dalla tastiera o dalla voce dentro Android Auto. */
    suspend fun search(query: String): List<MediaItem> {
        val found = find(query, null)
        val node = "$SEARCH|$query"
        return found.take(MAX_RESULTS).map { song(node, it) }
    }

    /**
     * Quello che Auto o l'assistente chiedono di suonare, trasformato in coda e punto di partenza.
     * null = non è roba di Android Auto (lo gestisce il percorso normale dell'app).
     */
    suspend fun resolve(items: List<MediaItem>): Pair<List<Song>, Int>? {
        val first = items.singleOrNull() ?: return null
        val id = first.mediaId
        val search = first.requestMetadata.searchQuery
        return when {
            id.startsWith("$SONG$SEP") -> {
                val (_, node, uri) = id.split(SEP, limit = 3)
                val queue = queueOf(node, below = false) ?: return emptyList<Song>() to 0
                queue to queue.indexOfFirst { it.uri == uri }.coerceAtLeast(0)
            }
            id.startsWith("$ALL$SEP") -> (queueOf(id.substringAfter(SEP), below = true) ?: emptyList()) to 0
            id.startsWith("$SHUFFLE$SEP") -> (queueOf(id.substringAfter(SEP), below = true) ?: emptyList()).shuffled() to 0
            id.isEmpty() && search != null -> find(search, first.requestMetadata.extras) to 0
            else -> null
        }
    }

    /** I pezzi di un nodo, nell'ordine in cui si vedono; [below] = anche quelli delle sottocartelle (Tutto, Caos). */
    private suspend fun queueOf(node: String, below: Boolean): List<Song>? {
        pathOf(node)?.let { return container.browseFolder(songs(), it, query()).let { v -> if (below) v.below else v.songs } }
        if (node.startsWith("$TAPES|")) {
            val id = node.substringAfter('|').toLongOrNull() ?: return null
            return container.playlistRepository.observePlaylist(id).first()?.songs
        }
        if (node.startsWith("$SEARCH|")) return find(node.substringAfter('|'), null)
        return null
    }

    private suspend fun find(query: String, extras: Bundle?): List<Song> {
        val tapes = container.playlistRepository.playlists.first().mapNotNull { p ->
            container.playlistRepository.observePlaylist(p.id).first()?.let { VoiceSearch.Tape(p.name, it.songs) }
        }
        return voice.find(
            query, songs(), tapes,
            artist = extras?.getString(EXTRA_ARTIST),
            album = extras?.getString(EXTRA_ALBUM),
            title = extras?.getString(EXTRA_TITLE),
        )
    }

    private suspend fun songs(): List<Song> = container.libraryRepository.songs.first()
    private fun query() = LibraryQuery(sort = container.settings.sortOrder.value)

    private fun playAllItems(node: String, songs: List<Song>): List<MediaItem> =
        if (songs.size < 2) emptyList() else listOf(
            action("$ALL$SEP$node", "▶ " + L10n.str(R.string.play_all), count(songs.size)),
            action("$SHUFFLE$SEP$node", "⤮ " + L10n.str(R.string.shuffle_button), null),
        )

    private fun count(n: Int) = "$n ♪"

    private fun song(node: String, s: Song): MediaItem = MediaItem.Builder()
        .setMediaId("$SONG$SEP$node$SEP${s.uri}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.title)
                .setArtist(s.displayArtist)
                .setAlbumTitle(s.album)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()

    private fun action(id: String, title: String, subtitle: String?): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle)
                .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST).build(),
        )
        .build()

    private fun folder(id: String, title: String, subtitle: String? = null): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle)
                .setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build(),
        )
        .build()

    companion object {
        const val ROOT = "root"
        private const val TAPES = "p"
        private const val SEARCH = "q"
        private const val SONG = "s"
        private const val ALL = "all"
        private const val SHUFFLE = "shuffle"
        private const val SEP = "\u001F"
        private const val MAX_RESULTS = 100

        // Gli extra che l'assistente mette nella ricerca (MediaStore.EXTRA_MEDIA_*).
        private const val EXTRA_ARTIST = "android.intent.extra.artist"
        private const val EXTRA_ALBUM = "android.intent.extra.album"
        private const val EXTRA_TITLE = "android.intent.extra.title"

        fun node(path: FolderPath): String =
            "v|${path.view.name}|${path.source?.name.orEmpty()}|" + path.dirs.joinToString("/") { URLEncoder.encode(it, "UTF-8") }

        fun pathOf(node: String): FolderPath? {
            val parts = node.split('|')
            if (parts.size != 4 || parts[0] != "v") return null
            val view = runCatching { LibraryView.valueOf(parts[1]) }.getOrNull() ?: return null
            val source = parts[2].takeIf { it.isNotEmpty() }?.let { s -> runCatching { SongSource.valueOf(s) }.getOrNull() }
            val dirs = parts[3].takeIf { it.isNotEmpty() }?.split('/')?.map { URLDecoder.decode(it, "UTF-8") }.orEmpty()
            return FolderPath(source, dirs, view)
        }
    }
}
