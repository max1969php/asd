package com.asd.player.ui.settings

import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asd.player.data.repository.ScanState
import com.asd.player.data.scanner.AudioScanner
import com.asd.player.data.settings.DriveSettings
import com.asd.player.domain.model.SongSource
import com.asd.player.domain.model.SortField
import com.asd.player.ui.drive.AccountSelect
import com.asd.player.ui.drive.DriveViewModel
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
    drive: DriveSettings,
    driveBusy: Boolean,
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
                state.directoryUri?.let(::readablePath) ?: "nessuna",
                style = MaterialTheme.typography.bodyLarge,
                color = Asd.Acid,
            )
            Text(
                "${state.totalCount - state.driveCount} pezzi indicizzati" +
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

        Section("Google Drive") {
            Text("Account predefinito", style = MaterialTheme.typography.labelMedium, color = Asd.Smudge)
            Spacer(Modifier.height(6.dp))
            AccountSelect(
                accounts = drive.accounts,
                selected = drive.defaultAccount,
                onSelect = driveViewModel::selectDefault,
                onAddAccount = onAddGoogleAccount,
            )
            if (driveBusy) Text("parlo con Google…", style = MaterialTheme.typography.bodySmall, color = Asd.Acid)
            if (drive.defaultAccount != null) {
                Spacer(Modifier.height(12.dp))
                Text("Cartella", style = MaterialTheme.typography.labelMedium, color = Asd.Smudge)
                Text(
                    drive.folder?.name ?: "nessuna",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (drive.folder != null) Asd.Acid else Asd.Smudge,
                )
                Text(
                    "${state.driveCount} pezzi su Drive" +
                        (drive.lastScanAt.takeIf { it > 0 }?.let { " · ultimo giro ${formatDate(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = Asd.Smudge,
                )
                (state.scanState as? ScanState.Done)?.takeIf { it.source == SongSource.DRIVE }?.let {
                    Text(
                        "+${it.added} nuovi · ~${it.updated} cambiati · -${it.removed} spariti",
                        style = MaterialTheme.typography.bodySmall,
                        color = Asd.Paper,
                    )
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PunkButton("Cartella Drive", onClick = driveViewModel::openPicker, icon = Icons.Filled.Cloud)
                    PunkButton(
                        "Riscansiona",
                        onClick = driveViewModel::refresh,
                        icon = Icons.Filled.Refresh,
                        container = Asd.Paper,
                        shadow = Asd.Acid,
                        enabled = drive.folder != null && state.scanState !is ScanState.Scanning,
                    )
                    PunkButton(
                        "Scollega",
                        onClick = { driveViewModel.disconnect(drive.defaultAccount) },
                        icon = Icons.Filled.LinkOff,
                        container = Asd.Spray,
                        content = Asd.Paper,
                        shadow = Asd.Paper,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Scegli l'account dalla select quando vuoi: i pezzi Drive in libreria diventano i suoi. " +
                    "I file restano su Drive e vengono suonati in streaming.",
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

                · La rete serve solo a Google Drive, e solo se lo colleghi tu.
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

/** "primary:Music/Punk" → "Music/Punk", più leggibile di un content:// URI. */
private fun readablePath(uri: Uri): String =
    runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfter(':').ifEmpty { "/" } }
        .getOrDefault(uri.toString())

private fun formatDate(ts: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ts))
