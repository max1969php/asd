package com.asd.player.ui.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asd.player.domain.model.Playlist
import com.asd.player.domain.model.PlaylistWithSongs
import com.asd.player.ui.components.PunkButton
import com.asd.player.ui.components.SongRow
import com.asd.player.ui.components.Stamp
import com.asd.player.ui.components.formatDuration
import com.asd.player.ui.theme.Asd

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    opened: PlaylistWithSongs?,
    currentMediaId: String?,
    viewModel: PlaylistsViewModel,
    modifier: Modifier = Modifier,
) {
    if (opened != null) {
        BackHandler { viewModel.open(null) }
        PlaylistDetail(opened, currentMediaId, viewModel, modifier)
    } else {
        PlaylistList(playlists, viewModel, modifier)
    }
}

@Composable
private fun PlaylistList(playlists: List<Playlist>, viewModel: PlaylistsViewModel, modifier: Modifier) {
    var creating by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text("MIXTAPE", style = MaterialTheme.typography.displayLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("fatti a mano, come una volta", style = MaterialTheme.typography.bodySmall, color = Asd.Smudge)
                Spacer(Modifier.weight(1f))
                Stamp("${playlists.size} nastri", color = Asd.Acid, rotation = -3f)
            }
            Spacer(Modifier.height(12.dp))
            PunkButton("Nuovo mixtape", onClick = { creating = true }, icon = Icons.Filled.Add)
        }
        HorizontalDivider(thickness = 2.dp, color = Asd.Paper)

        if (playlists.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
                Text("ZERO NASTRI.", style = MaterialTheme.typography.headlineLarge, color = Asd.Spray)
                Text(
                    "Creane uno qui, oppure tieni premuto un pezzo nella lista per buttarlo in un mixtape.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn {
                itemsIndexed(playlists, key = { _, p -> p.id }) { index, playlist ->
                    CassetteRow(playlist, index, onClick = { viewModel.open(playlist.id) })
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "Nuovo mixtape",
            confirmLabel = "Crea",
            onConfirm = { viewModel.create(it); creating = false },
            onDismiss = { creating = false },
        )
    }
}

/** Ogni playlist è una cassetta con l'etichetta scritta a pennarello, leggermente storta. */
@Composable
private fun CassetteRow(playlist: Playlist, index: Int, onClick: () -> Unit) {
    val tilt = if (index % 2 == 0) -0.8f else 0.8f
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .rotate(tilt)
            .background(Asd.Asphalt)
            .border(2.dp, Asd.Paper)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.background(Asd.Paper).padding(horizontal = 6.dp),
                    color = Asd.Toner,
                )
                Spacer(Modifier.height(6.dp))
                Text("LATO A · ${playlist.songCount} PEZZI", style = MaterialTheme.typography.labelSmall, color = Asd.Smudge)
            }
            // Le due bobine.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) {
                    Box(Modifier.size(22.dp).border(3.dp, Asd.Acid, shape = CircleShape))
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetail(
    data: PlaylistWithSongs,
    currentMediaId: String?,
    viewModel: PlaylistsViewModel,
    modifier: Modifier,
) {
    val playlist = data.playlist
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
            IconButton(onClick = { viewModel.open(null) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Asd.Paper)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = "Rinomina", tint = Asd.Paper) }
            IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, contentDescription = "Elimina", tint = Asd.Spray) }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(playlist.name.uppercase(), style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${data.songs.size} PEZZI · ${formatDuration(data.songs.sumOf { it.durationMs })}",
                style = MaterialTheme.typography.labelMedium,
                color = Asd.Smudge,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                PunkButton("Play", onClick = { viewModel.play(data.songs) }, icon = Icons.Filled.PlayArrow, enabled = data.songs.isNotEmpty())
                Spacer(Modifier.width(8.dp))
                PunkButton(
                    "Caos",
                    onClick = { viewModel.play(data.songs, data.songs.indices.random(), shuffle = true) },
                    icon = Icons.Filled.Shuffle,
                    container = Asd.Spray,
                    content = Asd.Paper,
                    shadow = Asd.Paper,
                    enabled = data.songs.isNotEmpty(),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        HorizontalDivider(thickness = 2.dp, color = Asd.Paper)

        if (data.songs.isEmpty()) {
            Text(
                "Nastro vergine. Vai in ROBA e tieni premuto un pezzo per aggiungerlo.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(32.dp),
            )
        } else {
            LazyColumn {
                // Le chiavi includono l'indice: lo stesso brano può comparire più volte.
                itemsIndexed(data.songs, key = { index, song -> "$index-${song.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        index = index,
                        isCurrent = song.uri == currentMediaId,
                        onClick = { viewModel.play(data.songs, index) },
                        trailing = {
                            Column {
                                SmallIcon(Icons.Filled.KeyboardArrowUp, "Su", enabled = index > 0) {
                                    viewModel.move(playlist.id, index, index - 1)
                                }
                                SmallIcon(Icons.Filled.KeyboardArrowDown, "Giù", enabled = index < data.songs.lastIndex) {
                                    viewModel.move(playlist.id, index, index + 1)
                                }
                            }
                            SmallIcon(Icons.Filled.Close, "Togli") { viewModel.removeAt(playlist.id, index) }
                        },
                    )
                    HorizontalDivider(color = Asd.Concrete)
                }
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = "Rinomina",
            initial = playlist.name,
            onConfirm = { viewModel.rename(playlist.id, it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "Bruciare il nastro?",
            message = "Il mixtape \"${playlist.name}\" sparisce. I file audio restano dove sono.",
            confirmLabel = "Brucia",
            onConfirm = { viewModel.delete(playlist.id); deleting = false },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun SmallIcon(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = description, tint = if (enabled) Asd.Paper else Asd.Concrete, modifier = Modifier.size(20.dp))
    }
}
