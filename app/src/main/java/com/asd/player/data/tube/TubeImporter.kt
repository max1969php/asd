package com.asd.player.data.tube

import com.asd.player.data.drive.DriveClient
import com.asd.player.data.repository.MusicLibraryRepository
import com.asd.player.data.scanner.AudioMetadata
import com.asd.player.data.scanner.ScannedFile
import com.asd.player.data.settings.DriveFolder
import com.asd.player.data.settings.SettingsStore
import com.asd.player.domain.model.SongSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

enum class TubeStatus { QUEUED, DOWNLOADING, UPLOADING, DONE, FAILED }

data class TubeJob(
    val id: Long,
    val url: String,
    val email: String,
    val folder: DriveFolder,
    val status: TubeStatus = TubeStatus.QUEUED,
    val progress: Float = 0f,
    val title: String? = null,
    val error: String? = null,
)

/**
 * Coda "dal Tubo a Drive": scarica l'audio con yt-dlp, lo carica nella cartella Drive
 * scelta e lo aggiunge subito alla libreria. Un lavoro alla volta, fuori dal ciclo di vita delle schermate.
 */
class TubeImporter(
    private val downloader: TubeDownloader,
    private val drive: DriveClient,
    private val library: MusicLibraryRepository,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Long>(Channel.UNLIMITED)
    private val nextId = AtomicLong(System.currentTimeMillis())

    /** yt-dlp non va usato da due parti insieme (download e aggiornamento). */
    private val ytdlLock = Mutex()

    private val _jobs = MutableStateFlow<List<TubeJob>>(emptyList())
    val jobs: StateFlow<List<TubeJob>> = _jobs.asStateFlow()

    init {
        scope.launch { for (id in queue) run(id) }
    }

    /** Il token Drive per [email] deve essere già stato autorizzato dalla UI. */
    fun enqueue(url: String, email: String, folder: DriveFolder) {
        val job = TubeJob(nextId.incrementAndGet(), url, email, folder)
        _jobs.update { listOf(job) + it }
        queue.trySend(job.id)
    }

    fun retry(id: Long) {
        val job = _jobs.value.find { it.id == id && it.status == TubeStatus.FAILED } ?: return
        edit(id) { it.copy(status = TubeStatus.QUEUED, progress = 0f, error = null) }
        queue.trySend(job.id)
    }

    fun clearFinished() = _jobs.update { list -> list.filter { it.status != TubeStatus.DONE && it.status != TubeStatus.FAILED } }

    /** Aggiorna yt-dlp a mano (quando YouTube cambia e i download falliscono). */
    suspend fun updateYtDlp(): String = ytdlLock.withLock { downloader.update() }

    fun ytDlpVersion(): String? = downloader.version()

    private suspend fun run(id: Long) {
        val job = _jobs.value.find { it.id == id } ?: return
        try {
            edit(id) { it.copy(status = TubeStatus.DOWNLOADING) }
            val (file, track) = ytdlLock.withLock {
                downloader.download(job.url, id) { p -> edit(id) { it.copy(progress = p.coerceIn(0f, 100f) / 100f) } }
            }
            edit(id) { it.copy(status = TubeStatus.UPLOADING, title = listOfNotNull(track.artist, track.title).joinToString(" - ")) }
            val uploaded = drive.upload(job.email, job.folder.id, track.fileName, track.mimeType, file)

            // Se nel frattempo l'utente ha cambiato account o cartella, il brano è su Drive ma non in questa libreria.
            val current = settings.drive.value
            if (current.defaultAccount == job.email && current.folder?.id == job.folder.id) {
                library.add(
                    ScannedFile(
                        uri = DriveClient.uriFor(uploaded.id),
                        fileName = uploaded.name,
                        mimeType = uploaded.mimeType,
                        size = uploaded.size.takeIf { it > 0 } ?: file.length(),
                        lastModified = uploaded.modifiedAt,
                    ),
                    AudioMetadata(track.title, track.artist, null, track.durationMs),
                    SongSource.DRIVE,
                )
            }
            edit(id) { it.copy(status = TubeStatus.DONE, progress = 1f) }
        } catch (e: Exception) {
            edit(id) { it.copy(status = TubeStatus.FAILED, error = e.message ?: e.javaClass.simpleName) }
        } finally {
            downloader.cleanup(id)
        }
    }

    private fun edit(id: Long, change: (TubeJob) -> TubeJob) =
        _jobs.update { list -> list.map { if (it.id == id) change(it) else it } }
}
