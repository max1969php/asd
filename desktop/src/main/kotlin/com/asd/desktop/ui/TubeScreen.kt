package com.asd.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asd.desktop.Desktop
import com.asd.desktop.Texts
import com.asd.desktop.Tube
import com.asd.desktop.TubeJob
import com.asd.player.ui.components.PunkButton
import com.asd.player.ui.components.Stamp
import com.asd.player.ui.components.TapeChip
import com.asd.player.ui.theme.Asd
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** TUBO: incolla un link YouTube, invio, l'audio finisce in <cartella>/TUBO. */
@Composable
fun TubeScreen(desktop: Desktop) {
    val tube = desktop.tube
    val ytdlp by tube.ytdlp.collectAsState()
    val jobs by tube.jobs.collectAsState()
    val settings by desktop.settings.state.collectAsState()
    var link by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val steal = {
        if (link.isNotBlank()) {
            if (tube.add(link)) {
                link = ""
                error = null
            } else {
                error = Texts.NOT_YOUTUBE_LINK
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Asd.Toner).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stamp(Texts.TUBE_STAMP)
            Spacer(Modifier.width(12.dp))
            Text(Texts.TUBE_SUBTITLE, style = MaterialTheme.typography.bodyMedium, color = Asd.Smudge)
        }
        Spacer(Modifier.height(20.dp))

        if (ytdlp == null) {
            Text(Texts.YTDLP_MISSING, style = MaterialTheme.typography.titleMedium, color = Asd.Spray)
            Spacer(Modifier.height(8.dp))
            SelectionContainer { Text(Texts.YTDLP_INSTALL, style = MaterialTheme.typography.bodyMedium, color = Asd.Paper) }
            Spacer(Modifier.height(16.dp))
            PunkButton(Texts.RETRY, onClick = tube::recheck, icon = Icons.Filled.Refresh)
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).border(2.dp, Asd.Acid).padding(horizontal = 12.dp, vertical = 12.dp)) {
                if (link.isEmpty()) Text("https://youtu.be/…", style = MaterialTheme.typography.bodyLarge, color = Asd.Smudge)
                BasicTextField(
                    value = link,
                    onValueChange = {
                        link = it
                        error = null
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Asd.Paper),
                    cursorBrush = SolidColor(Asd.Acid),
                    modifier = Modifier.fillMaxWidth().onKeyEvent {
                        if (it.key == Key.Enter) {
                            steal()
                            true
                        } else {
                            false
                        }
                    },
                )
            }
            Spacer(Modifier.width(12.dp))
            PunkButton(Texts.TUBE_STEAL, onClick = steal, icon = Icons.Filled.Download, enabled = link.isNotBlank() && settings.folder != null)
        }
        Spacer(Modifier.height(6.dp))
        val folder = settings.folder
        Text(
            error ?: if (folder == null) Texts.NO_FOLDER else Texts.tubeSavesTo(File(folder, Tube.TUBE_DIR).path),
            style = MaterialTheme.typography.labelSmall,
            color = if (error != null || folder == null) Asd.Spray else Asd.Smudge,
        )

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Texts.TUBE_LOOT, style = MaterialTheme.typography.titleMedium, color = Asd.Acid)
            Spacer(Modifier.weight(1f))
            if (jobs.any { it.state is TubeJob.State.Done || it.state is TubeJob.State.Failed }) {
                TapeChip(Texts.CLEAN_UP, selected = false, onClick = tube::cleanUp)
            }
        }
        Spacer(Modifier.height(8.dp))
        val jobList = rememberLazyListState()
        Box(Modifier.weight(1f)) {
            LazyColumn(state = jobList, modifier = Modifier.fillMaxSize().padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(jobs, key = { it.id }) { job -> JobRow(job) { tube.retry(job.id) } }
            }
            ListScrollbar(jobList)
        }
    }
}

@Composable
private fun JobRow(job: TubeJob, onRetry: () -> Unit) {
    val (label, color) = when (val s = job.state) {
        TubeJob.State.Queued -> Texts.JOB_QUEUED to Asd.Smudge
        is TubeJob.State.Downloading -> Texts.jobDownloading(s.percent) to Asd.Acid
        TubeJob.State.Saving -> Texts.JOB_SAVING to Asd.Acid
        is TubeJob.State.Done -> "✓" to Asd.Acid
        is TubeJob.State.Failed -> s.message to Asd.Spray
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(Asd.Asphalt).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                (job.state as? TubeJob.State.Done)?.fileName?.uppercase() ?: job.url,
                style = MaterialTheme.typography.titleSmall,
                color = Asd.Paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (job.state is TubeJob.State.Failed) {
            Spacer(Modifier.width(12.dp))
            TapeChip(Texts.RETRY, selected = false, onClick = onRetry)
        }
    }
}

/** In SETUP: yt-dlp (versione, aggiorna) e il browser per i cookie di YouTube. */
@Composable
fun TubeSettings(desktop: Desktop) {
    val tube = desktop.tube
    val ytdlp by tube.ytdlp.collectAsState()
    val settings by desktop.settings.state.collectAsState()
    var version by remember(ytdlp) { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(ytdlp) {
        version = withContext(Dispatchers.IO) { tube.version() }
    }
    val scope = rememberCoroutineScope()

    Stamp(Texts.TAB_TUBE, color = Asd.Paper)
    Spacer(Modifier.height(10.dp))
    if (ytdlp == null) {
        Text(Texts.YTDLP_MISSING, style = MaterialTheme.typography.bodyMedium, color = Asd.Spray)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("yt-dlp ${version ?: "…"}", style = MaterialTheme.typography.bodyMedium, color = Asd.Paper)
        Spacer(Modifier.width(16.dp))
        PunkButton(if (busy) Texts.YTDLP_UPDATING else Texts.YTDLP_UPDATE, enabled = !busy, icon = Icons.Filled.Refresh, onClick = {
            busy = true
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { tube.update() } }
                status = r.fold({ Texts.ytdlpUpdated(it) to true }, { Texts.ytdlpUpdateFailed(it.message ?: "") to false })
                version = withContext(Dispatchers.IO) { tube.version() }
                busy = false
            }
        })
    }
    status?.let { (msg, ok) ->
        Text(msg, style = MaterialTheme.typography.labelSmall, color = if (ok) Asd.Acid else Asd.Spray, modifier = Modifier.widthIn(max = 640.dp))
    }
    Spacer(Modifier.height(16.dp))
    Text(Texts.YT_COOKIES.uppercase(), style = MaterialTheme.typography.labelMedium, color = Asd.Paper)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TapeChip(Texts.NONE, selected = settings.ytBrowser == null, onClick = { desktop.setYtBrowser(null) })
        Tube.BROWSERS.forEach { b ->
            TapeChip(b.replaceFirstChar { it.uppercase() }, selected = settings.ytBrowser == b, onClick = { desktop.setYtBrowser(b) })
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(Texts.YT_COOKIES_HINT, style = MaterialTheme.typography.bodySmall, color = Asd.Smudge, modifier = Modifier.widthIn(max = 640.dp))
}
