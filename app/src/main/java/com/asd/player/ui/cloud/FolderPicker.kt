package com.asd.player.ui.cloud

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asd.player.ui.theme.Asd

/** Navigazione tra le cartelle di Drive o Dropbox per scegliere quella da indicizzare. */
@Composable
fun FolderPicker(title: String, state: FolderPickerState, account: String?, viewModel: CloudViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closePicker,
        containerColor = Asd.Asphalt,
        modifier = Modifier.border(2.dp, Asd.Acid),
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                account?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Asd.Smudge) }
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
                state.error?.let { Text("ERRORE: $it", style = MaterialTheme.typography.bodySmall, color = Asd.Spray) }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    if (state.path.size > 1) {
                        item {
                            PickerRow("↑ ..", color = Asd.Acid, onClick = viewModel::goUp)
                        }
                    }
                    items(state.folders, key = { it.id }) { folder ->
                        PickerRow("▸ ${folder.name}", onClick = { viewModel.enterFolder(folder) })
                    }
                    if (!state.loading && state.error == null && state.folders.isEmpty()) {
                        item {
                            Text("(nessuna sottocartella)", style = MaterialTheme.typography.bodySmall, color = Asd.Smudge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::confirmFolder, enabled = !state.loading) {
                Text("USA QUESTA", color = Asd.Acid)
            }
        },
        dismissButton = { TextButton(onClick = viewModel::closePicker) { Text("LASCIA STARE", color = Asd.Smudge) } },
    )
}

@Composable
private fun PickerRow(text: String, color: Color = Asd.Paper, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}
