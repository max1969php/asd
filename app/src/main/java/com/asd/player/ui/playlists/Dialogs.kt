package com.asd.player.ui.playlists

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asd.player.domain.model.Playlist
import com.asd.player.domain.model.Song
import com.asd.player.ui.theme.Asd

@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    confirmLabel: String = "Fatto",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Asd.Asphalt,
        modifier = Modifier.border(2.dp, Asd.Acid),
        title = { Text(title.uppercase(), style = MaterialTheme.typography.headlineSmall) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("es. roba da corriera") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Asd.Acid,
                    unfocusedBorderColor = Asd.Paper,
                    cursorColor = Asd.Acid,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirmLabel.uppercase(), color = if (name.isNotBlank()) Asd.Acid else Asd.Smudge)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("LASCIA STARE", color = Asd.Smudge) } },
    )
}

@Composable
fun AddToPlaylistDialog(
    song: Song,
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(title = "Nuovo mixtape", confirmLabel = "Crea e aggiungi", onConfirm = onCreate, onDismiss = onDismiss)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Asd.Asphalt,
        modifier = Modifier.border(2.dp, Asd.Acid),
        title = {
            Column {
                Text("METTI IN UN MIXTAPE", style = MaterialTheme.typography.headlineSmall)
                Text(song.title, style = MaterialTheme.typography.bodySmall, color = Asd.Smudge)
            }
        },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                item {
                    Text(
                        "+ NUOVO MIXTAPE",
                        style = MaterialTheme.typography.labelLarge,
                        color = Asd.Acid,
                        modifier = Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                    )
                }
                items(playlists, key = { it.id }) { playlist ->
                    Text(
                        "${playlist.name.uppercase()}  (${playlist.songCount})",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(playlist) }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CHIUDI", color = Asd.Smudge) } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Asd.Asphalt,
        modifier = Modifier.border(2.dp, Asd.Spray),
        title = { Text(title.uppercase(), style = MaterialTheme.typography.headlineSmall) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel.uppercase(), color = Asd.Spray) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("NO", color = Asd.Smudge) } },
    )
}
