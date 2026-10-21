package com.asd.player.data.loudness

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Process
import com.asd.player.data.drive.DriveAuth
import com.asd.player.data.drive.DriveClient
import com.asd.player.data.dropbox.DropboxClient
import com.asd.player.data.repository.MusicLibraryRepository
import com.asd.player.data.settings.SettingsStore
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SongSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class LoudnessProgress(val pending: Int = 0, val running: Boolean = false)

/**
 * Misura in sottofondo, un brano alla volta, il volume dei pezzi nuovi o cambiati.
 * I file locali subito; quelli su Drive e Dropbox vanno scaricati interi, quindi solo su
 * rete non a consumo (Wi-Fi). Il brano in riproduzione passa davanti agli altri.
 */
class LoudnessQueue(
    context: Context,
    private val analyzer: LoudnessAnalyzer,
    private val library: MusicLibraryRepository,
    private val settings: SettingsStore,
    private val driveAuth: DriveAuth,
    private val dropbox: DropboxClient,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    /** URI del brano che il player sta suonando: va misurato per primo. */
    @Volatile private var urgent: String? = null

    private val _progress = MutableStateFlow(LoudnessProgress())
    val progress: StateFlow<LoudnessProgress> = _progress.asStateFlow()

    /** Brani remoti falliti in questa sessione (rete, consenso scaduto): si riprovano al prossimo avvio. */
    private val skipped = HashSet<Long>()

    fun start() {
        // Ogni cambio di catalogo (scansione, TUBO) o dell'interruttore sveglia la coda.
        scope.launch { combine(library.songs, settings.normalizeVolume) { _, _ -> }.collect { wakeUp.trySend(Unit) } }
        scope.launch { for (signal in wakeUp) drain() }
    }

    fun prioritize(uri: String?) {
        urgent = uri
        wakeUp.trySend(Unit)
    }

    private suspend fun drain() {
        while (settings.normalizeVolume.value) {
            val pending = library.unmeasured()
                .filter { it.id !in skipped && (it.source == SongSource.LOCAL || unmetered()) }
            _progress.value = LoudnessProgress(pending.size, running = pending.isNotEmpty())
            val next = pending.find { it.uri == urgent } ?: pending.firstOrNull() ?: break
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            val loudness = runCatching { measure(next) }.getOrNull()
            when {
                loudness != null ->
                    library.setLoudness(next.id, loudness.integratedLufs.takeIf { it.isFinite() } ?: SILENCE, loudness.samplePeakDb)
                next.source == SongSource.LOCAL -> library.setLoudness(next.id, null, null)
                else -> skipped += next.id
            }
        }
        _progress.value = LoudnessProgress(library.unmeasured().count { it.id !in skipped }, running = false)
    }

    private fun measure(song: Song): Loudness = when (song.source) {
        SongSource.LOCAL -> analyzer.analyzeLocal(Uri.parse(song.uri))
        SongSource.DRIVE -> {
            val email = settings.drive.state.value.defaultAccount ?: error("Nessun account Drive")
            val fileId = DriveClient.fileIdOf(Uri.parse(song.uri)) ?: error("URI Drive non valido")
            analyzer.analyzeRemote(DriveClient.mediaUrl(fileId), mapOf("Authorization" to "Bearer ${driveAuth.token(email)}"))
        }
        SongSource.DROPBOX -> {
            val email = settings.dropbox.state.value.defaultAccount ?: error("Nessun account Dropbox")
            val fileId = DropboxClient.fileIdOf(Uri.parse(song.uri)) ?: error("URI Dropbox non valido")
            analyzer.analyzeRemote(dropbox.temporaryLink(email, fileId), emptyMap())
        }
    }

    private companion object {
        /** Un file di solo silenzio: lo trattiamo come molto basso, il player non lo alza comunque. */
        const val SILENCE = -70.0
    }

    private fun unmetered(): Boolean = connectivity?.let { !it.isActiveNetworkMetered && it.activeNetwork != null } ?: false
}
