package com.asd.player.data.tube

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * yt-dlp sul telefono, tramite youtubedl-android (Python + yt-dlp impacchettati nell'APK).
 * Scarica solo la traccia audio già pronta (m4a), senza ffmpeg né riconversioni.
 * Tutti i metodi sono bloccanti: vanno chiamati fuori dal main thread.
 */
class TubeDownloader(private val context: Context, private val cookies: TubeCookies) {

    private val ytdl = YoutubeDL.getInstance()
    private val prefs = context.getSharedPreferences("asd_tube", Context.MODE_PRIVATE)
    private var ready = false

    /** La prima volta estrae Python e yt-dlp (qualche secondo), poi è immediato. */
    @Synchronized
    fun prepare() {
        if (!ready) {
            ytdl.init(context)
            ready = true
        }
        // YouTube cambia spesso e lo yt-dlp dentro l'APK invecchia: lo aggiorniamo al massimo una volta al giorno.
        if (System.currentTimeMillis() - prefs.getLong(KEY_UPDATED_AT, 0L) > TimeUnit.DAYS.toMillis(1)) {
            runCatching { update() }
        }
    }

    /** Scarica l'ultima versione stabile di yt-dlp. Restituisce la versione installata. */
    @Synchronized
    fun update(): String {
        if (!ready) {
            ytdl.init(context)
            ready = true
        }
        ytdl.updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
        prefs.edit().putLong(KEY_UPDATED_AT, System.currentTimeMillis()).apply()
        return version() ?: "ultima"
    }

    /** null finché non è mai stato aggiornato: c'è quello impacchettato nell'APK. */
    fun version(): String? = runCatching { ytdl.versionName(context) ?: ytdl.version(context) }.getOrNull()

    /** Scarica l'audio di un video in una cartella temporanea. [onProgress] riceve 0..100. */
    fun download(url: String, jobId: Long, onProgress: (Float) -> Unit): Pair<File, TubeTrack> {
        prepare()
        val dir = File(context.cacheDir, "tube/$jobId").apply { deleteRecursively(); mkdirs() }
        val meta = File(dir, "meta.json")
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("-f", "bestaudio[ext=m4a]/bestaudio[ext=mp3]")
            addOption("-o", File(dir, "%(id)s.%(ext)s").absolutePath)
            addOption("--no-mtime")
            addCommands(listOf("--print-to-file", "after_move:%(.{id,title,track,artist,uploader,duration,ext})j", meta.absolutePath))
            cookies.refresh()
            if (cookies.file.exists()) addOption("--cookies", cookies.file.absolutePath)
        }
        try {
            ytdl.execute(request, "tube-$jobId") { progress, _, _ -> onProgress(progress) }
        } catch (e: Exception) {
            throw IOException(errorLine(e.message))
        }
        val track = meta.takeIf { it.exists() }?.readLines()?.firstOrNull { it.isNotBlank() }?.let(TubeTrack::parse)
            ?: throw IOException("yt-dlp non ha scaricato niente")
        val file = File(dir, "${track.id}.${track.ext}")
        if (!file.exists()) throw IOException("File audio sparito: ${file.name}")
        return file to track
    }

    fun cleanup(jobId: Long) {
        File(context.cacheDir, "tube/$jobId").deleteRecursively()
    }

    /** Di tutto l'output di yt-dlp ci interessa la riga "ERROR: …". */
    private fun errorLine(output: String?): String {
        val lines = output.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val error = lines.lastOrNull { it.startsWith("ERROR:") }?.removePrefix("ERROR:")?.trim()
        return when {
            error == null -> lines.lastOrNull() ?: "yt-dlp è fallito"
            "Requested format is not available" in error -> "Nessuna traccia audio m4a per questo video"
            "not a bot" in error || "Sign in to confirm" in error ->
                if (cookies.file.exists()) "YouTube non si fida nemmeno con l'accesso: rifai ACCEDI A YOUTUBE o cambia rete"
                else "YouTube vuole sapere che non sei un bot: premi ACCEDI A YOUTUBE qui sotto"
            else -> error
        }
    }

    private companion object {
        const val KEY_UPDATED_AT = "ytdlp_updated_at"
    }
}
