package com.asd.player.data.tube

import android.net.Uri
import com.asd.player.data.drive.DriveClient
import com.asd.player.data.repository.MusicLibraryRepository
import com.asd.player.data.scanner.AudioMetadata
import com.asd.player.data.scanner.AudioScanner
import com.asd.player.data.scanner.ScannedFile
import com.asd.player.data.settings.SettingsStore
import com.asd.player.data.settings.TubeDestination
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
import java.io.File
import java.util.concurrent.atomic.AtomicLong

enum class TubeStatus { QUEUED, DOWNLOADING, UPLOADING, DONE, FAILED }

/**
 * Dove finisce un download, fissato al momento dell'invio.
 * [folderId]: URI dell'albero SAF per il telefono, id della cartella Drive.
 */
data class TubeTarget(
    val destination: TubeDestination,
    val account: String?,
    val folderId: String,
    val label: String,
)

data class TubeJob(
    val id: Long,
    val url: String,
    val target: TubeTarget,
    val status: TubeStatus = TubeStatus.QUEUED,
    val progress: Float = 0f,
    val title: String? = null,
    val error: String? = null,
)

/**
 * Coda del TUBO: scarica l'audio con yt-dlp, lo salva nella destinazione scelta (cartella del
 * telefono o Drive) e lo aggiunge subito alla libreria. Un lavoro alla volta,
 * fuori dal ciclo di vita delle schermate.
 */
class TubeImporter(
    private val downloader: TubeDownloader,
    val cookies: TubeCookies,
    private val local: AudioScanner,
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

    /** Per Drive il token di [TubeTarget.account] deve essere già stato autorizzato dalla UI. */
    fun enqueue(url: String, target: TubeTarget) {
        val job = TubeJob(nextId.incrementAndGet(), url, target)
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
            val (source, saved) = save(job.target, track, file)

            // Se nel frattempo l'utente ha cambiato cartella o account, il brano è salvato ma non in questa libreria.
            if (isCurrent(job.target)) {
                library.add(saved, AudioMetadata(track.title, track.artist, null, track.durationMs), source)
            }
            edit(id) { it.copy(status = TubeStatus.DONE, progress = 1f) }
        } catch (e: SecurityException) {
            edit(id) {
                it.copy(status = TubeStatus.FAILED, error = "Il telefono non mi lascia scrivere: riscegli la cartella in SETUP")
            }
        } catch (e: Exception) {
            edit(id) { it.copy(status = TubeStatus.FAILED, error = e.message ?: e.javaClass.simpleName) }
        } finally {
            downloader.cleanup(id)
        }
    }

    private fun save(target: TubeTarget, track: TubeTrack, file: File): Pair<SongSource, ScannedFile> = when (target.destination) {
        TubeDestination.LOCAL ->
            SongSource.LOCAL to local.write(Uri.parse(target.folderId), track.fileName, track.mimeType, file)
        TubeDestination.DRIVE -> {
            val uploaded = drive.upload(target.account!!, target.folderId, track.fileName, track.mimeType, file)
            SongSource.DRIVE to ScannedFile(
                uri = DriveClient.uriFor(uploaded.id),
                fileName = uploaded.name,
                mimeType = uploaded.mimeType,
                size = uploaded.size.takeIf { it > 0 } ?: file.length(),
                lastModified = uploaded.modifiedAt,
            )
        }
    }

    private fun isCurrent(target: TubeTarget): Boolean = when (target.destination) {
        TubeDestination.LOCAL -> settings.directoryUri.value?.toString() == target.folderId
        TubeDestination.DRIVE -> settings.drive.state.value.let {
            it.defaultAccount == target.account && it.folder?.id == target.folderId
        }
    }

    private fun edit(id: Long, change: (TubeJob) -> TubeJob) =
        _jobs.update { list -> list.map { if (it.id == id) change(it) else it } }
}
