package com.asd.player.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asd.player.BuildConfig
import com.asd.player.L10n
import com.asd.player.R
import com.asd.player.data.repository.ScanState
import com.asd.player.data.scanner.AudioScanner
import com.asd.player.domain.model.SongSource
import com.asd.player.domain.model.SortField
import com.asd.player.ui.cloud.AccountSelect
import com.asd.player.ui.cloud.CloudViewModel
import com.asd.player.ui.cloud.DriveViewModel
import com.asd.player.ui.components.AsdLinks
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
        Text(stringResource(R.string.settings_subtitle), style = MaterialTheme.typography.bodySmall, color = Asd.Smudge)
        Spacer(Modifier.height(8.dp))
        AsdLinks()

        Section(stringResource(R.string.phone_folder)) {
            Text(
                state.directoryUri?.let(AudioScanner::readablePath) ?: stringResource(R.string.none_f),
                style = MaterialTheme.typography.bodyLarge,
                color = Asd.Acid,
            )
            Text(
                stringResource(R.string.settings_indexed, state.countBySource[SongSource.LOCAL] ?: 0) +
                    (state.lastScanAt.takeIf { it > 0 }?.let { stringResource(R.string.last_scan, formatDate(it)) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = Asd.Smudge,
            )
            (state.scanState as? ScanState.Done)?.takeIf { it.source == SongSource.LOCAL }?.let {
                Text(
                    stringResource(R.string.scan_summary, it.added, it.updated, it.removed),
                    style = MaterialTheme.typography.bodySmall,
                    color = Asd.Paper,
                )
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PunkButton(stringResource(R.string.change_folder), onClick = onPickDirectory, icon = Icons.Filled.Folder)
                PunkButton(
                    stringResource(R.string.rescan),
                    onClick = { viewModel.rescan() },
                    icon = Icons.Filled.Refresh,
                    container = Asd.Paper,
                    shadow = Asd.Acid,
                    enabled = state.directoryUri != null && state.scanState !is ScanState.Scanning,
                )
            }
        }

        if (BuildConfig.ONLINE) CloudSection(
            title = "Google Drive",
            name = stringResource(R.string.drive),
            source = SongSource.DRIVE,
            state = state,
            viewModel = driveViewModel,
            addLabel = stringResource(R.string.link_phone_account),
            onAddAccount = onAddGoogleAccount,
        )

        Section(stringResource(R.string.volume_title)) {
            val normalize by viewModel.normalizeVolume.collectAsStateWithLifecycle()
            val progress by viewModel.loudnessProgress.collectAsStateWithLifecycle()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TapeChip(stringResource(R.string.on), selected = normalize, onClick = { viewModel.setNormalizeVolume(true) })
                TapeChip(stringResource(R.string.off), selected = !normalize, onClick = { viewModel.setNormalizeVolume(false) })
            }
            Spacer(Modifier.height(8.dp))
            if (normalize) {
                Text(
                    when {
                        progress.pending == 0 -> stringResource(R.string.volume_all_measured)
                        progress.running -> stringResource(R.string.volume_measuring, progress.pending)
                        else -> stringResource(R.string.volume_waiting_wifi, progress.pending)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (progress.pending == 0) Asd.Acid else Asd.Paper,
                )
            }
            Text(
                stringResource(R.string.volume_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Asd.Smudge,
            )
        }

        Section(stringResource(R.string.default_sort)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SortField.entries.forEach { field ->
                    TapeChip(field.label(), selected = state.query.sort.field == field, onClick = {
                        viewModel.setSort(state.query.sort.copy(field = field))
                    })
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TapeChip(stringResource(R.string.ascending), selected = state.query.sort.ascending, onClick = {
                    viewModel.setSort(state.query.sort.copy(ascending = true))
                })
                TapeChip(stringResource(R.string.descending), selected = !state.query.sort.ascending, onClick = {
                    viewModel.setSort(state.query.sort.copy(ascending = false))
                })
            }
        }

        Section(stringResource(R.string.language_title)) {
            LanguageSelect()
        }

        Section(stringResource(R.string.formats)) {
            Text(
                AudioScanner.SUPPORTED_EXTENSIONS.joinToString(" · ") { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Section("Manifesto") {
            Text(
                stringResource(R.string.manifesto),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Stamp(stringResource(R.string.copy_and_spread), rotation = -2f)
        }
        Spacer(Modifier.height(16.dp))
        // L'indirizzo non si traduce: tocca e si apre l'app della posta.
        val context = LocalContext.current
        Text(
            stringResource(R.string.suggestions, SUGGESTIONS_EMAIL),
            style = MaterialTheme.typography.bodyMedium,
            color = Asd.Acid,
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUGGESTIONS_EMAIL")))
                }
            },
        )
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
        Text(stringResource(R.string.default_account), style = MaterialTheme.typography.labelMedium, color = Asd.Smudge)
        Spacer(Modifier.height(6.dp))
        AccountSelect(
            accounts = cloud.accounts,
            selected = cloud.defaultAccount,
            onSelect = viewModel::selectDefault,
            onAddAccount = onAddAccount,
            addLabel = addLabel,
        )
        if (busy) Text(stringResource(R.string.talking_to, name), style = MaterialTheme.typography.bodySmall, color = Asd.Acid)
        val email = cloud.defaultAccount
        if (email != null) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.folder), style = MaterialTheme.typography.labelMedium, color = Asd.Smudge)
            Text(
                cloud.folder?.name ?: stringResource(R.string.none_f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (cloud.folder != null) Asd.Acid else Asd.Smudge,
            )
            Text(
                stringResource(R.string.cloud_count, state.countBySource[source] ?: 0, name) +
                    (cloud.lastScanAt.takeIf { it > 0 }?.let { stringResource(R.string.last_scan, formatDate(it)) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = Asd.Smudge,
            )
            (state.scanState as? ScanState.Done)?.takeIf { it.source == source }?.let {
                Text(
                    stringResource(R.string.scan_summary, it.added, it.updated, it.removed),
                    style = MaterialTheme.typography.bodySmall,
                    color = Asd.Paper,
                )
            }
            (state.scanState as? ScanState.Failed)?.takeIf { it.source == source }?.let {
                Text(stringResource(R.string.error_colon, it.reason), style = MaterialTheme.typography.bodySmall, color = Asd.Spray)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PunkButton(stringResource(R.string.cloud_folder_button, name), onClick = viewModel::openPicker, icon = Icons.Filled.Cloud)
                PunkButton(
                    stringResource(R.string.rescan),
                    onClick = viewModel::refresh,
                    icon = Icons.Filled.Refresh,
                    container = Asd.Paper,
                    shadow = Asd.Acid,
                    enabled = cloud.folder != null && state.scanState !is ScanState.Scanning,
                )
                PunkButton(
                    stringResource(R.string.disconnect),
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
            stringResource(R.string.cloud_hint, name),
            style = MaterialTheme.typography.bodySmall,
            color = Asd.Smudge,
        )
    }
}

/** Select della lingua: ogni lingua scritta nella sua lingua, così la trova anche chi non legge l'italiano. */
@Composable
private fun LanguageSelect() {
    val activity = LocalContext.current as Activity
    val current = remember { L10n.current(activity) }
    var open by remember { mutableStateOf(false) }
    val system = stringResource(R.string.language_system)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().border(2.dp, Asd.Paper).clickable { open = true }.padding(12.dp),
        ) {
            Text(
                if (current.isEmpty()) system else L10n.nativeName(current),
                style = MaterialTheme.typography.bodyLarge,
                color = Asd.Acid,
                modifier = Modifier.weight(1f),
            )
            Text("▼", style = MaterialTheme.typography.labelLarge, color = Asd.Paper)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Asd.Asphalt) {
            (listOf("") + L10n.SUPPORTED).forEach { tag ->
                val active = tag == current
                DropdownMenuItem(
                    text = {
                        Text(
                            (if (active) "● " else "○ ") + (if (tag.isEmpty()) system else L10n.nativeName(tag)),
                            color = if (active) Asd.Acid else Asd.Paper,
                        )
                    },
                    onClick = { open = false; if (!active) L10n.set(activity, tag) },
                )
            }
        }
    }
}

private const val SUGGESTIONS_EMAIL = "deviceasd403@gmail.com"

private fun formatDate(ts: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ts))
