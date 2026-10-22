package com.asd.player.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asd.player.data.repository.ScanState
import com.asd.player.data.scanner.AudioScanner
import com.asd.player.domain.model.SongSource
import com.asd.player.domain.model.SortField
import com.asd.player.ui.cloud.AccountSelect
import com.asd.player.ui.cloud.CloudViewModel
import com.asd.player.ui.cloud.DriveViewModel
import com.asd.player.ui.components.PunkButton
import com.asd.player.ui.components.Stamp
import com.asd.player.ui.components.TapeChip
import com.asd.player.ui.library.LibraryUiState
import com.asd.player.ui.library.LibraryViewModel
import com.asd.player.ui.library.label
import com.asd.player.ui.theme.Asd
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
    driveViewModel: DriveViewModel,
    onPickDirectory: () -> Unit,
    onAddGoogleAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("SETUP", style = MaterialTheme.typography.displayLarge)
        Text("poche manopole, nessun contratto", style = MaterialTheme.typography.bodySmall, color = Asd.Smudge)

        Section("Cartella del telefono") {
            Text(
                state.directoryUri?.let(AudioScanner::readablePath) ?: "nessuna",
                style = MaterialTheme.typography.bodyLarge,
                color = Asd.Acid,
            )
            Text(
                "${state.countBySource[SongSource.LOCAL] ?: 0} pezzi indicizzati" +
                    (state.lastScanAt.takeIf { it > 0 }?.let { " · ultimo giro ${formatDate(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = Asd.Smudge,
            )
            (state.scanState as? ScanState.Done)?.takeIf { it.source == SongSource.LOCAL }?.let {
                Text(
                    "+${it.added} nuovi · ~${it.updated} cambiati · -${it.removed} spariti",
                    style = MaterialTheme.typography.bodySmall,
                    color = Asd.Paper,
                )
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PunkButton("Cambia cartella", onClick = onPickDirectory, icon = Icons.Filled.Folder)
                PunkButton(
                    "Riscansiona",
                    onClick = { viewModel.rescan() },
                    icon = Icons.Filled.Refresh,
                    container = Asd.Paper,
                    shadow = Asd.Acid,
                    enabled = state.directoryUri != null && state.scanState !is ScanState.Scanning,
                )
            }
        }

        CloudSection(
            title = "Google Drive",
            name = "Drive",
            source = SongSource.DRIVE,
            state = state,
            viewModel = driveViewModel,
            addLabel = "+ COLLEGA UN ACCOUNT DEL TELEFONO",
            onAddAccount = onAddGoogleAccount,
        )

        Section("Volume uniforme") {
            val normalize by viewModel.normalizeVolume.collectAsStateWithLifecycle()
            val progress by viewModel.loudnessProgress.collectAsStateWithLifecycle()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TapeChip("Acceso", selected = normalize, onClick = { viewModel.setNormalizeVolume(true) })
                TapeChip("Spento", selected = !normalize, onClick = { viewModel.setNormalizeVolume(false) })
            }
            Spacer(Modifier.height(8.dp))
            if (normalize) {
                Text(
                    when {
                        progress.pending == 0 -> "tutti i pezzi misurati"
                        progress.running -> "misuro il volume: ne mancano ${progress.pending}"
                        else -> "${progress.pending} pezzi cloud da misurare: aspetto il Wi-Fi"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (progress.pending == 0) Asd.Acid else Asd.Paper,
                )
            }
            Text(
                "Ogni pezzo viene misurato una volta (EBU R128, come ReplayGain 2.0) e suonato a -18 LUFS: " +
                    "niente più salti di volume tra un MP3 e l'altro. I pezzi più forti vengono abbassati, " +
                    "quindi alza un po' il volume del telefono. I file su Drive si misurano solo in Wi-Fi.",
                style = MaterialTheme.typography.bodySmall,
                color = Asd.Smudge,
            )
        }

        Section("Ordine di default") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SortField.entries.forEach { field ->
                    TapeChip(field.label(), selected = state.query.sort.field == field, onClick = {
                        viewModel.setSort(state.query.sort.copy(field = field))
                    })
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TapeChip("↑ crescente", selected = state.query.sort.ascending, onClick = {
                    viewModel.setSort(state.query.sort.copy(ascending = true))
                })
                TapeChip("↓ decrescente", selected = !state.query.sort.ascending, onClick = {
                    viewModel.setSort(state.query.sort.copy(ascending = false))
                })
            }
        }

        Section("Formati accettati") {
            Text(
                AudioScanner.SUPPORTED_EXTENSIONS.joinToString(" · ") { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Section("Manifesto") {
            Text(
                """
                ASD = Anarchic Streaming Device.
                Lo "streaming" è dalla tua memoria alle tue orecchie, e basta.

                · La rete serve solo a Google Drive e al TUBO, e solo se li usi tu.
                · Nessun account, nessuna pubblicità, nessun tracciamento.
                · Le playlist vivono sul telefono. Cancellarle non tocca i file.
                · Costo: 0€. Per sempre.
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Stamp("copia e diffondi", rotation = -2f)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(20.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .border(2.dp, Asd.Concrete)
            .padding(14.dp)
    ) {
        Text("// ${title.uppercase()}", style = MaterialTheme.typography.labelLarge, color = Asd.Spray)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** Account predefinito, cartella e bottoni di una fonte cloud: oggi Google Drive. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudSection(
    title: String,
    name: String,
    source: SongSource,
    state: LibraryUiState,
    viewModel: CloudViewModel,
    addLabel: String,
    onAddAccount: () -> Unit,
) {
    val cloud by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    Section(title) {
        Text("Account predefinito", style = MaterialTheme.typography.labelMedium, color = Asd.Smudge)
        Spacer(Modifier.height(6.dp))
        AccountSelect(
            accounts = cloud.accounts,
            selected = cloud.defaultAccount,
            onSelect = viewModel::selectDefault,
            onAddAccount = onAddAccount,
            addLabel = addLabel,
        )
        if (busy) Text("parlo con $name…", style = MaterialTheme.typography.bodySmall, color = Asd.Acid)
        val email = cloud.defaultAccount
        if (email != null) {
            Spacer(Modifier.height(12.dp))
            Text("Cartella", style = MaterialTheme.typography.labelMedium, color = Asd.Smudge)
            Text(
                cloud.folder?.name ?: "nessuna",
                style = MaterialTheme.typography.bodyLarge,
                color = if (cloud.folder != null) Asd.Acid else Asd.Smudge,
            )
            Text(
                "${state.countBySource[source] ?: 0} pezzi su $name" +
                    (cloud.lastScanAt.takeIf { it > 0 }?.let { " · ultimo giro ${formatDate(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = Asd.Smudge,
            )
            (state.scanState as? ScanState.Done)?.takeIf { it.source == source }?.let {
                Text(
                    "+${it.added} nuovi · ~${it.updated} cambiati · -${it.removed} spariti",
                    style = MaterialTheme.typography.bodySmall,
                    color = Asd.Paper,
                )
            }
            (state.scanState as? ScanState.Failed)?.takeIf { it.source == source }?.let {
                Text("ERRORE: ${it.reason}", style = MaterialTheme.typography.bodySmall, color = Asd.Spray)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PunkButton("Cartella $name", onClick = viewModel::openPicker, icon = Icons.Filled.Cloud)
                PunkButton(
                    "Riscansiona",
                    onClick = viewModel::refresh,
                    icon = Icons.Filled.Refresh,
                    container = Asd.Paper,
                    shadow = Asd.Acid,
                    enabled = cloud.folder != null && state.scanState !is ScanState.Scanning,
                )
                PunkButton(
                    "Scollega",
                    onClick = { viewModel.disconnect(email) },
                    icon = Icons.Filled.LinkOff,
                    container = Asd.Spray,
                    content = Asd.Paper,
                    shadow = Asd.Paper,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Scegli l'account dalla select quando vuoi: i pezzi $name in libreria diventano i suoi. " +
                "I file restano su $name e vengono suonati in streaming. " +
                "Quello che rubi dal TUBO può finire in questa cartella.",
            style = MaterialTheme.typography.bodySmall,
            color = Asd.Smudge,
        )
    }
}

private fun formatDate(ts: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ts))
