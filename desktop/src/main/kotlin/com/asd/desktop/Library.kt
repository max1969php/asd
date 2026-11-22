package com.asd.desktop

import com.asd.player.domain.model.Song
import com.asd.player.domain.usecase.SongNames
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val done: Int, val total: Int) : ScanState
    data class Done(val added: Int, val updated: Int, val removed: Int) : ScanState
    data class Failed(val message: String) : ScanState
}

/**
 * L'analisi audio di un pezzo (volume e silenzio). [lufs] null = file illeggibile, da non riprovare.
 * [startMs]/[endMs]: da dove a dove suonare saltando il silenzio ai lati (null = niente da saltare).
 */
data class Analysis(val lufs: Double?, val peakDb: Double?, val startMs: Long?, val endMs: Long?)

/** Un file del catalogo con i nomi grezzi (tag o nome file), come nel database dell'app Android. */
private data class Entry(
    val id: Long,
    val path: String,
    val size: Long,
    val modified: Long,
    val folder: List<String>,
    val tags: Tags,
    /** null = non ancora analizzato; si perde se il file cambia. */
    val analysis: Analysis? = null,
) {
    fun toSong(): Song {
        val file = File(path)
        return Song(
            id = id,
            uri = file.toURI().toString(),
            fileName = file.name,
            title = tags.title ?: file.name.substringBeforeLast('.'),
            artist = tags.artist,
            album = tags.album,
            durationMs = tags.durationMs,
            mimeType = null,
            fileSize = size,
            modifiedAt = modified,
            folder = folder,
            trackNumber = tags.trackNumber,
            discNumber = tags.discNumber,
            loudnessLufs = analysis?.lufs,
            soundStartMs = analysis?.startMs,
            soundEndMs = analysis?.endMs,
        )
    }
}

/**
 * Il catalogo della cartella scelta. Si salva in ~/.local/share/asd-desktop/library.json e la
 * riscansione è incrementale: i tag si rileggono solo per i file nuovi o cambiati (dimensione e
 * data di modifica). ASD non scrive né cancella mai nella cartella della musica.
 */
class Library(private val cache: File = File(Paths.data, "library.json")) {

    private val mutex = Mutex()
    private var root: String? = null
    @Volatile private var entries: List<Entry> = emptyList()
    private var nextId = 1L

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    /** I pezzi con i nomi da mostrare (SongNames: puliti, artista e titolo dal nome file). */
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    /** Misure non ancora scritte su disco: si salvano a gruppi, non a ogni pezzo. */
    @Volatile private var dirty = false
    private var lastSave = 0L

    /** I pezzi ancora da analizzare (volume e silenzio). */
    fun unanalyzed(): List<Song> {
        val pending = entries.filter { it.analysis == null }.mapTo(HashSet()) { it.id }
        return _songs.value.filter { it.id in pending }
    }

    /** Salva l'analisi di un pezzo, se nel frattempo il file non è cambiato (false = non salvata). */
    suspend fun setAnalysis(uri: String, size: Long, modified: Long, analysis: Analysis): Boolean = mutex.withLock {
        val path = File(java.net.URI(uri)).path
        val i = entries.indexOfFirst { it.path == path }
        if (i < 0 || entries[i].size != size || entries[i].modified != modified) return@withLock false
        val e = entries[i].copy(analysis = analysis)
        entries = entries.toMutableList().also { it[i] = e }
        // Solo i campi dell'analisi: i nomi puliti non cambiano, niente SongNames su tutta la libreria.
        _songs.value = _songs.value.map {
            if (it.id == e.id) it.copy(loudnessLufs = analysis.lufs, soundStartMs = analysis.startMs, soundEndMs = analysis.endMs) else it
        }
        dirty = true
        if (System.currentTimeMillis() - lastSave > SAVE_EVERY_MS) withContext(Dispatchers.IO) { save() }
        true
    }

    /** All'uscita: scrive le misure rimaste in sospeso. */
    fun flush() {
        if (dirty) save()
    }

    /** Carica il catalogo salvato, se è della stessa cartella: la lista c'è subito, prima della scansione. */
    suspend fun load(folder: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val o = JSONObject(cache.readText())
                if (o.optString("root") == folder) {
                    val a = o.getJSONArray("songs")
                    entries = List(a.length()) { entryOf(a.getJSONObject(it)) }
                    root = folder
                    nextId = (entries.maxOfOrNull { it.id } ?: 0L) + 1
                }
            }
            publish()
        }
    }

    suspend fun rescan(folder: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val dir = File(folder)
                if (!dir.isDirectory) {
                    _scan.value = ScanState.Failed(Texts.folderMissing(folder))
                    return@withContext
                }
                if (root != folder) {
                    entries = emptyList()
                    root = folder
                }
                _scan.value = ScanState.Scanning(0, 0)
                val files = listAudio(dir.toPath())
                val old = entries.associateBy { it.path }
                val changed = files.filter { f -> old[f.path]?.let { it.size != f.size || it.modified != f.modified } ?: true }
                val done = AtomicInteger(files.size - changed.size)
                _scan.value = ScanState.Scanning(done.get(), files.size)
                val semaphore = Semaphore(4)
                val read = coroutineScope {
                    changed.map { f ->
                        async {
                            semaphore.withPermit {
                                val tags = TagReader.read(File(f.path))
                                f.copy(tags = tags).also {
                                    val n = done.incrementAndGet()
                                    if (n % 20 == 0) _scan.value = ScanState.Scanning(n, files.size)
                                }
                            }
                        }
                    }.awaitAll()
                }.associateBy { it.path }
                val result = files.map { f ->
                    read[f.path]?.let { it.copy(id = old[f.path]?.id ?: nextId++) }
                        ?: old.getValue(f.path).copy(folder = f.folder)
                }
                val added = result.count { it.path !in old }
                val updated = read.size - added
                val removed = old.size - (result.size - added)
                entries = result
                save()
                publish()
                _scan.value = ScanState.Done(added, updated, removed)
            } catch (e: Exception) {
                _scan.value = ScanState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun publish() {
        _songs.value = SongNames.resolve(entries.map { it.toSong() })
    }

    /** Tutti i file audio sotto [root], saltando file e cartelle nascosti. */
    private fun listAudio(root: Path): List<Entry> {
        val found = mutableListOf<Entry>()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                if (dir != root && dir.fileName.toString().startsWith(".")) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val name = file.fileName.toString()
                if (attrs.isRegularFile && isSupported(name)) {
                    val rel = root.relativize(file.parent)
                    val folder = if (rel.toString().isEmpty()) emptyList() else rel.map { it.toString() }
                    found += Entry(0, file.toString(), attrs.size(), attrs.lastModifiedTime().toMillis(), folder, Tags())
                }
                return FileVisitResult.CONTINUE
            }

            // Una cartella illeggibile non ferma la scansione.
            override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        return found
    }

    private fun save() {
        dirty = false
        lastSave = System.currentTimeMillis()
        val a = JSONArray()
        entries.forEach { e ->
            a.put(
                JSONObject()
                    .put("id", e.id).put("path", e.path).put("size", e.size).put("modified", e.modified)
                    .put("folder", JSONArray(e.folder))
                    .putOpt("title", e.tags.title).putOpt("artist", e.tags.artist).putOpt("album", e.tags.album)
                    .put("durationMs", e.tags.durationMs)
                    .putOpt("track", e.tags.trackNumber).putOpt("disc", e.tags.discNumber)
                    .putOpt("analysis", e.analysis?.let { an ->
                        JSONObject().putOpt("lufs", an.lufs).putOpt("peakDb", an.peakDb)
                            .putOpt("startMs", an.startMs).putOpt("endMs", an.endMs)
                    })
            )
        }
        runCatching { Paths.writeAtomically(cache, JSONObject().put("version", 1).put("root", root).put("songs", a).toString()) }
    }

    private fun entryOf(o: JSONObject): Entry {
        val folder = o.optJSONArray("folder")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
        fun str(k: String) = if (o.has(k) && !o.isNull(k)) o.getString(k) else null
        fun int(k: String) = if (o.has(k) && !o.isNull(k)) o.getInt(k) else null
        return Entry(
            id = o.getLong("id"), path = o.getString("path"), size = o.getLong("size"), modified = o.getLong("modified"),
            folder = folder,
            tags = Tags(str("title"), str("artist"), str("album"), o.optLong("durationMs"), int("track"), int("disc")),
            analysis = o.optJSONObject("analysis")?.let { an ->
                fun dbl(k: String) = if (an.has(k) && !an.isNull(k)) an.getDouble(k) else null
                fun lng(k: String) = if (an.has(k) && !an.isNull(k)) an.getLong(k) else null
                Analysis(dbl("lufs"), dbl("peakDb"), lng("startMs"), lng("endMs"))
            },
        )
    }

    companion object {
        /** Gli stessi formati dell'app Android. */
        private const val SAVE_EVERY_MS = 10_000L

        val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "flac", "m4a", "aac", "ogg", "opus")

        fun isSupported(name: String): Boolean =
            !name.startsWith(".") && name.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS
    }
}
