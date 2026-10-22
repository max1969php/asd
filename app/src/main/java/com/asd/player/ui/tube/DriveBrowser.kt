package com.asd.player.ui.tube

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asd.player.R
import com.asd.player.ui.theme.Asd
import java.util.Locale

/** "Scarica dal Drive": si gira per il proprio Drive e si copia sul telefono un pezzo o una cartella intera. */
@Composable
fun DriveBrowser(state: DriveBrowserState, viewModel: TubeViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closeBrowser,
        containerColor = Asd.Asphalt,
        modifier = Modifier.border(2.dp, Asd.Acid),
        title = {
            Column {
                Text(stringResource(R.string.download_from_drive_caps), style = MaterialTheme.typography.headlineSmall)
                Text(state.email, style = MaterialTheme.typography.bodySmall, color = Asd.Smudge)
                Text(
                    "/" + state.path.drop(1).joinToString("/") { it.name },
                    style = MaterialTheme.typography.labelMedium,
                    color = Asd.Acid,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Asd.Acid, trackColor = Asd.Concrete)
                state.error?.let { Text(stringResource(R.string.error_colon, it), style = MaterialTheme.typography.bodySmall, color = Asd.Spray) }
                Text(
                    stringResource(R.string.browser_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Asd.Smudge,
                )
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    if (state.path.size > 1) {
                        item { Entry("↑ ..", color = Asd.Acid, onClick = viewModel::browseUp) }
                    }
                    items(state.entries, key = { it.id }) { entry ->
                        if (entry.isFolder) {
                            Entry("▸ ${entry.name}", onClick = { viewModel.browseInto(entry) })
                        } else {
                            Entry("↓ ${entry.name}", detail = megabytes(entry.size), onClick = { viewModel.downloadFile(entry) })
                        }
                    }
                    if (!state.loading && state.error == null && state.entries.isEmpty()) {
                        item { Text(stringResource(R.string.browser_no_audio), style = MaterialTheme.typography.bodySmall, color = Asd.Smudge) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::downloadFolder, enabled = !state.loading) {
                Text(stringResource(R.string.download_whole_folder), color = Asd.Acid)
            }
        },
        dismissButton = { TextButton(onClick = viewModel::closeBrowser) { Text(stringResource(R.string.close_caps), color = Asd.Smudge) } },
    )
}

@Composable
private fun Entry(text: String, detail: String? = null, color: androidx.compose.ui.graphics.Color = Asd.Paper, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Asd.Smudge) }
    }
}

private fun megabytes(bytes: Long): String? =
    bytes.takeIf { it > 0 }?.let { String.format(Locale.ROOT, "%.1f MB", it / 1_048_576.0) }
