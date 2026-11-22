package com.asd.desktop

import com.asd.player.data.tube.TubeLink
import com.asd.player.data.tube.TubeTrack
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Un link incollato nel TUBO e dove è arrivato. */
data class TubeJob(val id: Long, val url: String, val state: State) {
    sealed interface State {
        data object Queued : State
        data class Downloading(val percent: Int) : State
        data object Saving : State
        data class Done(val fileName: String) : State
        data class Failed(val message: String) : State
    }
}

/**
 * Il TUBO di ASD Desktop: lo yt-dlp installato nel sistema (apt o pipx), con le stesse scelte
 * dell'app: solo la traccia audio già pronta (m4a, altrimenti mp3), niente ffmpeg, un pezzo alla
 * volta. Il file finisce in <cartella della musica>/TUBO come "Artista - Titolo.m4a" (mai sopra un
 * file che c'è già) e la libreria si riscansiona. Solo per roba tua o libera.
 */
class Tube(
    private val settings: SettingsStore,
    private val onSaved: () -> Unit,
    private val cache: File = File(Paths.cache, "tube"),
    private val locate: () -> File? = ::find,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Long>(Channel.UNLIMITED)
    private var nextId = 1L

    private val _jobs = MutableStateFlow<List<TubeJob>>(emptyList())
    /** Dal più recente al più vecchio. */
    val jobs: StateFlow<List<TubeJob>> = _jobs.asStateFlow()

    private val _ytdlp = MutableStateFlow(locate())
    /** Dove sta yt-dlp; null = non installato (il TUBO spiega come installarlo). */
    val ytdlp: StateFlow<File?> = _ytdlp.asStateFlow()

    init {
        // Un pezzo alla volta: yt-dlp in parallelo farebbe solo arrabbiare YouTube.
        scope.launch { for (id in queue) run(id) }
    }

    /** Il link incollato; false se non è YouTube. */
    @Synchronized
    fun add(input: String): Boolean {
        val url = TubeLink.clean(input) ?: return false
        val job = TubeJob(nextId++, url, TubeJob.State.Queued)
        _jobs.update { listOf(job) + it }
        queue.trySend(job.id)
        return true
    }

    fun retry(id: Long) {
        set(id, TubeJob.State.Queued)
        queue.trySend(id)
    }

    /** PULISCI: via i finiti e i falliti dalla lista (i file restano). */
    fun cleanUp() = _jobs.update { list -> list.filter { it.state is TubeJob.State.Queued || it.state is TubeJob.State.Downloading || it.state is TubeJob.State.Saving } }

    /** Di nuovo: se nel frattempo è stato installato. */
    fun recheck() {
        _ytdlp.value = locate()
    }

    fun version(): String? = _ytdlp.value?.let { exe -> runCatching { exec(listOf(exe.path, "--version"), 20).second.trim().lines().first() }.getOrNull() }

    /**
     * Aggiorna yt-dlp: con pipx "pipx upgrade", altrimenti "yt-dlp -U" (funziona per il file
     * scaricato dal sito; quello di apt si aggiorna con apt e lo dice lui). Restituisce la versione.
     */
    fun update(): String {
        val exe = _ytdlp.value ?: throw IOException(Texts.YTDLP_MISSING)
        val real = exe.canonicalPath
        val cmd = if ("pipx" in real) listOf(findOnPath("pipx")?.path ?: "pipx", "upgrade", "yt-dlp") else listOf(exe.path, "-U")
        val (code, out) = exec(cmd, 300)
        if (code != 0) throw IOException(lastLine(out))
        return version() ?: ""
    }

    private suspend fun run(id: Long) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        if (job.state !is TubeJob.State.Queued) return
        val result = runCatching { download(job) }
        set(id, result.fold({ TubeJob.State.Done(it) }, { TubeJob.State.Failed(it.message ?: Texts.YTDLP_FAILED) }))
        if (result.isSuccess) onSaved()
    }

    private fun download(job: TubeJob): String {
        val exe = _ytdlp.value ?: throw IOException(Texts.YTDLP_MISSING)
        val folder = settings.state.value.folder?.let(::File) ?: throw IOException(Texts.NO_FOLDER)
        val dir = File(cache, job.id.toString()).apply { deleteRecursively(); mkdirs() }
        try {
            val meta = File(dir, "meta.json")
            val cmd = mutableListOf(
                exe.path, "--no-playlist", "--no-mtime", "--newline",
                "-f", "bestaudio[ext=m4a]/bestaudio[ext=mp3]",
                "-o", File(dir, "%(id)s.%(ext)s").path,
                "--progress-template", "download:ASD %(progress._percent_str)s",
                "--print-to-file", "after_move:%(.{id,title,track,artist,uploader,duration,ext})j", meta.path,
            )
            settings.state.value.ytBrowser?.let { cmd += listOf("--cookies-from-browser", it) }
            cmd += job.url
            set(job.id, TubeJob.State.Downloading(0))
            val process = processOf(cmd).start()
            val output = StringBuilder()
            process.inputStream.bufferedReader().forEachLine { line ->
                output.appendLine(line)
                if (line.startsWith("ASD ")) {
                    line.removePrefix("ASD ").trim().removeSuffix("%").trim().toDoubleOrNull()?.let { set(job.id, TubeJob.State.Downloading(it.toInt().coerceIn(0, 100))) }
                }
            }
            if (process.waitFor() != 0) throw IOException(errorLine(output.toString()))
            set(job.id, TubeJob.State.Saving)
            val track = meta.takeIf { it.exists() }?.readLines()?.firstOrNull { it.isNotBlank() }?.let(TubeTrack::parse)
                ?: throw IOException(Texts.YTDLP_NOTHING)
            val file = File(dir, "${track.id}.${track.ext}")
            if (!file.exists()) throw IOException(Texts.YTDLP_NOTHING)
            val target = free(File(folder, TUBE_DIR).apply { mkdirs() }, track.fileName)
            // Prima una copia accanto, poi il nome giusto: in ROBA non compare mai un file a metà.
            val part = File(target.parentFile, ".${target.name}.part")
            file.copyTo(part, overwrite = true)
            if (!part.renameTo(target)) {
                part.delete()
                throw IOException(target.path)
            }
            return target.name
        } finally {
            dir.deleteRecursively()
        }
    }

    /** "Nome.m4a", e se c'è già "Nome (2).m4a": ASD non scrive mai sopra un file. */
    private fun free(dir: File, name: String): File {
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var f = File(dir, name)
        var n = 2
        while (f.exists()) f = File(dir, "$base ($n).$ext").also { n++ }
        return f
    }

    private fun set(id: Long, state: TubeJob.State) = _jobs.update { list -> list.map { if (it.id == id) it.copy(state = state) else it } }

    /** Di tutto l'output di yt-dlp conta la riga "ERROR: …", come nell'app. */
    private fun errorLine(output: String): String {
        val error = output.lines().map { it.trim() }.lastOrNull { it.startsWith("ERROR:") }?.removePrefix("ERROR:")?.trim()
        return when {
            error == null -> lastLine(output)
            "Requested format is not available" in error -> Texts.YTDLP_NO_M4A
            "not a bot" in error || "Sign in to confirm" in error -> Texts.YT_BOT
            else -> error
        }
    }

    private fun lastLine(output: String) = output.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: Texts.YTDLP_FAILED

    private fun exec(cmd: List<String>, timeoutSec: Long): Pair<Int, String> {
        val p = processOf(cmd).start()
        val out = StringBuilder()
        val reader = Thread { p.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }.apply { start() }
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            throw IOException(Texts.YTDLP_FAILED)
        }
        reader.join(2_000)
        return p.exitValue() to out.toString()
    }

    /**
     * Dal menu di GNOME il PATH di ASD spesso non ha ~/.local/bin e ~/.deno/bin: yt-dlp non
     * troverebbe Deno (gli serve per YouTube). Si aggiungono qui, per yt-dlp e per pipx.
     */
    private fun processOf(cmd: List<String>): ProcessBuilder = ProcessBuilder(cmd).redirectErrorStream(true).apply {
        environment()["PATH"] = (extraDirs() + environment()["PATH"].orEmpty().split(File.pathSeparator))
            .filter { it.isNotEmpty() }.distinct().joinToString(File.pathSeparator)
    }

    companion object {
        const val TUBE_DIR = "TUBO"

        private fun extraDirs(): List<String> {
            val home = System.getProperty("user.home")
            return listOf("$home/.local/bin", "$home/.deno/bin")
        }
        val BROWSERS = listOf("firefox", "chrome", "chromium", "brave", "edge", "opera", "vivaldi")

        /** yt-dlp nel PATH, o nei posti di pipx e pip --user (dal menu di GNOME ~/.local/bin può mancare dal PATH). */
        fun find(): File? = findOnPath("yt-dlp")

        private fun findOnPath(name: String): File? {
            val home = System.getProperty("user.home")
            val dirs = System.getenv("PATH").orEmpty().split(File.pathSeparator) + extraDirs() + listOf("/usr/local/bin", "/usr/bin", "/snap/bin")
            return dirs.filter { it.isNotEmpty() }.map { File(it, name) }.firstOrNull { it.isFile && it.canExecute() }
        }
    }
}
