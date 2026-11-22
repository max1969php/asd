package com.asd.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class AnalysisProgress(val pending: Int = 0, val running: Boolean = false)

/**
 * Analizza in sottofondo, un pezzo alla volta, quelli nuovi o cambiati: volume e silenzio con una
 * sola decodifica. Il pezzo che suona passa davanti agli altri. Gira se è acceso almeno uno dei due.
 */
class AnalysisQueue(private val library: Library, private val settings: SettingsStore) {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    /** Pezzi cambiati durante l'analisi: si rifanno dopo la prossima riscansione. */
    private val skipped = HashSet<Long>()

    /** URI del pezzo che suona: va analizzato per primo. */
    @Volatile private var urgent: String? = null

    private val _progress = MutableStateFlow(AnalysisProgress())
    val progress: StateFlow<AnalysisProgress> = _progress.asStateFlow()

    private val enabled: Boolean get() = settings.state.value.let { it.normalizeVolume || it.skipSilence }

    fun start() {
        scope.launch {
            combine(
                library.songs.map { it.size },
                settings.state.map { it.normalizeVolume || it.skipSilence }.distinctUntilChanged(),
            ) { _, _ -> }.collect { wakeUp.trySend(Unit) }
        }
        scope.launch { for (signal in wakeUp) drain() }
    }

    fun prioritize(uri: String?) {
        urgent = uri
        wakeUp.trySend(Unit)
    }

    private suspend fun drain() {
        Thread.currentThread().priority = Thread.MIN_PRIORITY
        while (enabled) {
            val pending = library.unanalyzed().filter { it.id !in skipped }
            _progress.value = AnalysisProgress(pending.size, running = pending.isNotEmpty())
            val next = pending.find { it.uri == urgent } ?: pending.firstOrNull() ?: break
            // Un file illeggibile si segna come tale: non si riprova a ogni giro.
            val analysis = runCatching { Analyzer.analyze(next.uri) }.getOrElse { Analysis(null, null, null, null) }
            if (!library.setAnalysis(next.uri, next.fileSize, next.modifiedAt, analysis)) skipped += next.id
        }
        _progress.value = AnalysisProgress(library.unanalyzed().count { it.id !in skipped }, running = false)
    }
}
