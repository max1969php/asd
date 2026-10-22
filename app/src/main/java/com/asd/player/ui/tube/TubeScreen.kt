package com.asd.player.ui.tube

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asd.player.data.settings.TubeDestination
import com.asd.player.data.tube.TubeCookies
import com.asd.player.data.tube.TubeJob
import com.asd.player.data.tube.TubeStatus
import com.asd.player.ui.components.PunkButton
import com.asd.player.ui.components.Stamp
import com.asd.player.ui.theme.Asd

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TubeScreen(
    options: List<DestinationOption>,
    destination: TubeDestination,
    jobs: List<TubeJob>,
    ytDlpVersion: String?,
    updating: Boolean,
    cookies: TubeCookies.Origin,
    viewModel: TubeViewModel,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { viewModel.loadVersion() }
    var url by rememberSaveable { mutableStateOf("") }
    var loginOpen by rememberSaveable { mutableStateOf(false) }
    val importCookies = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importCookies)
    }
    if (loginOpen) {
        YoutubeLogin(onDone = {
            loginOpen = false
            viewModel.onLoginClosed()
        })
    }
    val selected = options.find { it.destination == destination }
    val ready = selected?.target != null
    val submit = { if (viewModel.submit(url)) url = "" }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("TUBO", style = MaterialTheme.typography.displayLarge)
                Text(
                    "incolla un link YouTube, premi invio: l'audio è tuo",
                    style = MaterialTheme.typography.bodySmall,
                    color = Asd.Smudge,
                )
                Spacer(Modifier.height(16.dp))
                UrlBox(url, onChange = { url = it }, onSubmit = submit, enabled = ready)
                Spacer(Modifier.height(12.dp))
                Text("SALVA IN", style = MaterialTheme.typography.labelMedium, color = Asd.Smudge)
                Spacer(Modifier.height(6.dp))
                DestinationSelect(options, destination, onSelect = viewModel::setDestination)
                Spacer(Modifier.height(10.dp))
                if (ready) {
                    PunkButton("Ruba l'audio", onClick = submit, icon = Icons.Filled.Download, enabled = url.isNotBlank())
                } else {
                    Text(
                        "Non si può: ${selected?.problem ?: "destinazione sconosciuta"}.",
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
                Text("// YOUTUBE", style = MaterialTheme.typography.labelLarge, color = Asd.Spray)
                Spacer(Modifier.height(6.dp))
                Text(
                    when (cookies) {
                        TubeCookies.Origin.NONE -> "anonimo"
                        TubeCookies.Origin.LOGIN -> "dentro con un account"
                        TubeCookies.Origin.FILE -> "dentro con un cookies.txt"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (cookies == TubeCookies.Origin.NONE) Asd.Smudge else Asd.Acid,
                )
                Text(
                    "Se YouTube chiede di confermare che non sei un bot, yt-dlp deve entrare con un account. " +
                        "Meglio un account secondario: YouTube può prendersela con quello che usi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Asd.Smudge,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PunkButton("Accedi a YouTube", onClick = { loginOpen = true })
                    PunkButton(
                        "Importa cookies.txt",
                        onClick = { importCookies.launch(arrayOf("text/*", "application/octet-stream")) },
                        container = Asd.Paper,
                        shadow = Asd.Acid,
                    )
                    if (cookies != TubeCookies.Origin.NONE) {
                        PunkButton(
                            "Esci",
                            onClick = viewModel::logoutYoutube,
                            container = Asd.Spray,
                            content = Asd.Paper,
                            shadow = Asd.Paper,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
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

/** La select della destinazione: telefono o Drive. Quelle non pronte dicono cosa manca. */
@Composable
private fun DestinationSelect(
    options: List<DestinationOption>,
    selected: TubeDestination,
    onSelect: (TubeDestination) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.find { it.destination == selected }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, Asd.Paper)
                .clickable { open = true }
                .padding(12.dp),
        ) {
            Text(
                current?.target?.label ?: selected.label(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (current?.target != null) Asd.Acid else Asd.Smudge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("▼", style = MaterialTheme.typography.labelLarge, color = Asd.Paper)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Asd.Asphalt) {
            options.forEach { option ->
                val active = option.destination == selected
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                (if (active) "● " else "○ ") + (option.target?.label ?: option.destination.label()),
                                color = if (active) Asd.Acid else Asd.Paper,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            option.problem?.let {
                                Text("   $it", style = MaterialTheme.typography.bodySmall, color = Asd.Smudge)
                            }
                        }
                    },
                    onClick = { open = false; onSelect(option.destination) },
                )
            }
        }
    }
}

private fun TubeDestination.label() = when (this) {
    TubeDestination.LOCAL -> "Telefono"
    TubeDestination.DRIVE -> "Google Drive"
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
            TubeStatus.UPLOADING -> (if (job.target.destination == TubeDestination.LOCAL) "salvo…" else "carico…") to Asd.Acid
            TubeStatus.DONE -> "✓ ${job.target.label}" to Asd.Acid
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
