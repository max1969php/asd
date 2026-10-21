package com.asd.player.ui.tube

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asd.player.data.settings.DriveSettings
import com.asd.player.data.tube.TubeJob
import com.asd.player.data.tube.TubeStatus
import com.asd.player.ui.components.PunkButton
import com.asd.player.ui.components.Stamp
import com.asd.player.ui.theme.Asd

@Composable
fun TubeScreen(
    drive: DriveSettings,
    jobs: List<TubeJob>,
    ytDlpVersion: String?,
    updating: Boolean,
    viewModel: TubeViewModel,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { viewModel.loadVersion() }
    var url by rememberSaveable { mutableStateOf("") }
    val ready = drive.defaultAccount != null && drive.folder != null
    val submit = { if (viewModel.submit(url)) url = "" }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("TUBO", style = MaterialTheme.typography.displayLarge)
                Text(
                    "incolla un link YouTube, premi invio: l'audio finisce nel tuo Drive",
                    style = MaterialTheme.typography.bodySmall,
                    color = Asd.Smudge,
                )
                Spacer(Modifier.height(16.dp))
                UrlBox(url, onChange = { url = it }, onSubmit = submit, enabled = ready)
                Spacer(Modifier.height(10.dp))
                if (ready) {
                    Text(
                        "→ ${drive.defaultAccount} · ${drive.folder?.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Asd.Acid,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    PunkButton("Ruba l'audio", onClick = submit, icon = Icons.Filled.Download, enabled = url.isNotBlank())
                } else {
                    Text(
                        if (drive.defaultAccount == null) "Serve un account Google Drive collegato."
                        else "Serve una cartella Drive dove mettere i pezzi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Asd.Spray,
                    )
                    Spacer(Modifier.height(10.dp))
                    PunkButton("Vai al setup", onClick = onOpenSetup, container = Asd.Paper, shadow = Asd.Acid)
                }
            }
        }

        if (jobs.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("// BOTTINO", style = MaterialTheme.typography.labelLarge, color = Asd.Spray, modifier = Modifier.weight(1f))
                    if (jobs.any { it.status == TubeStatus.DONE || it.status == TubeStatus.FAILED }) {
                        Text(
                            "PULISCI",
                            style = MaterialTheme.typography.labelMedium,
                            color = Asd.Smudge,
                            modifier = Modifier.clickable(onClick = viewModel::clearFinished).padding(6.dp),
                        )
                    }
                }
            }
            items(jobs, key = { it.id }) { job -> JobRow(job, onRetry = { viewModel.retry(job) }) }
        }

        item {
            Column(Modifier.padding(16.dp)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "yt-dlp: ${ytDlpVersion ?: "…"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Asd.Smudge,
                )
                Text(
                    "Si aggiorna da solo una volta al giorno. Se YouTube fa i capricci, aggiornalo a mano.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Asd.Smudge,
                )
                Spacer(Modifier.height(8.dp))
                PunkButton(
                    if (updating) "Aggiorno…" else "Aggiorna yt-dlp",
                    onClick = viewModel::updateYtDlp,
                    container = Asd.Paper,
                    shadow = Asd.Acid,
                    enabled = !updating,
                )
                Spacer(Modifier.height(16.dp))
                Stamp("solo per roba tua o libera", rotation = -2f)
            }
        }
    }
}

@Composable
private fun UrlBox(text: String, onChange: (String) -> Unit, onSubmit: () -> Unit, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, if (enabled) Asd.Paper else Asd.Concrete)
            .padding(start = 12.dp, end = 12.dp),
    ) {
        Text("URL_", style = MaterialTheme.typography.labelLarge, color = Asd.Acid)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (text.isEmpty()) {
                Text("https://youtube.com/watch?v=…", style = MaterialTheme.typography.bodyLarge, color = Asd.Smudge)
            }
            BasicTextField(
                value = text,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Asd.Paper),
                cursorBrush = SolidColor(Asd.Acid),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun JobRow(job: TubeJob, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .border(1.5.dp, if (job.status == TubeStatus.FAILED) Asd.Spray else Asd.Concrete)
            .padding(12.dp)
    ) {
        Text(
            job.title ?: job.url,
            style = MaterialTheme.typography.bodyMedium,
            color = Asd.Paper,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        val (label, color) = when (job.status) {
            TubeStatus.QUEUED -> "in coda" to Asd.Smudge
            TubeStatus.DOWNLOADING ->
                (if (job.progress > 0f) "scarico ${(job.progress * 100).toInt()}%" else "scaldo yt-dlp…") to Asd.Acid
            TubeStatus.UPLOADING -> "carico su Drive…" to Asd.Acid
            TubeStatus.DONE -> "✓ nel Drive e in ROBA" to Asd.Acid
            TubeStatus.FAILED -> "✕ ${job.error}" to Asd.Spray
        }
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 3)
        if (job.status == TubeStatus.DOWNLOADING && job.progress > 0f) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).background(Asd.Concrete)) {
                Box(Modifier.fillMaxWidth(job.progress).height(4.dp).background(Asd.Acid))
            }
        }
        if (job.status == TubeStatus.FAILED) {
            Spacer(Modifier.height(6.dp))
            Text(
                "RIPROVA",
                style = MaterialTheme.typography.labelMedium,
                color = Asd.Paper,
                modifier = Modifier.clickable(onClick = onRetry).padding(vertical = 4.dp),
            )
        }
    }
}
