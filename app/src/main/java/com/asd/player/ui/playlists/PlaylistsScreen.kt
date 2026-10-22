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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asd.player.R
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
                Text(stringResource(R.string.playlists_subtitle), style = MaterialTheme.typography.bodySmall, color = Asd.Smudge)
                Spacer(Modifier.weight(1f))
                Stamp(stringResource(R.string.playlists_stamp, playlists.size), color = Asd.Acid, rotation = -3f)
            }
            Spacer(Modifier.height(12.dp))
            PunkButton(stringResource(R.string.new_playlist), onClick = { creating = true }, icon = Icons.Filled.Add)
        }
        HorizontalDivider(thickness = 2.dp, color = Asd.Paper)

        if (playlists.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.playlists_none), style = MaterialTheme.typography.headlineLarge, color = Asd.Spray)
                Text(
                    stringResource(R.string.playlists_none_hint),
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
            title = stringResource(R.string.new_playlist),
            confirmLabel = stringResource(R.string.create),
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
                Text(stringResource(R.string.playlist_side_a, playlist.songCount), style = MaterialTheme.typography.labelSmall, color = Asd.Smudge)
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Asd.Paper)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename), tint = Asd.Paper) }
            IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_playlist), tint = Asd.Spray) }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(playlist.name.uppercase(), style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.playlist_summary, data.songs.size, formatDuration(data.songs.sumOf { it.durationMs })),
                style = MaterialTheme.typography.labelMedium,
                color = Asd.Smudge,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                PunkButton(stringResource(R.string.play), onClick = { viewModel.play(data.songs) }, icon = Icons.Filled.PlayArrow, enabled = data.songs.isNotEmpty())
                Spacer(Modifier.width(8.dp))
                PunkButton(
                    stringResource(R.string.shuffle_button),
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
                stringResource(R.string.playlist_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(32.dp),
            )
        } else {
            // Riordino a frecce, niente trascinamento: tieni premuto → "Sposta" → ↑ ↓ solo su quel pezzo.
            var moving by remember(playlist.id) { mutableStateOf<Int?>(null) }
            var menuFor by remember(playlist.id) { mutableStateOf<Int?>(null) }
            LazyColumn {
                // Le chiavi includono l'indice: lo stesso brano può comparire più volte.
                itemsIndexed(data.songs, key = { index, song -> "$index-${song.id}" }) { index, song ->
                    val isMoving = moving == index
                    Box {
                        SongRow(
                            song = song,
                            index = index,
                            isCurrent = song.uri == currentMediaId,
                            onClick = { if (moving == null) viewModel.play(data.songs, index) },
                            onLongClick = { if (moving == null) menuFor = index },
                            modifier = if (isMoving) Modifier.border(2.dp, Asd.Acid) else Modifier,
                            trailing = if (!isMoving) null else {
                                {
                                    SmallIcon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up), enabled = index > 0) {
                                        viewModel.move(playlist.id, index, index - 1)
                                        moving = index - 1
                                    }
                                    SmallIcon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down), enabled = index < data.songs.lastIndex) {
                                        viewModel.move(playlist.id, index, index + 1)
                                        moving = index + 1
                                    }
                                    TextButton(onClick = { moving = null }) { Text(stringResource(R.string.ok), color = Asd.Acid) }
                                }
                            },
                        )
                        DropdownMenu(
                            expanded = menuFor == index,
                            onDismissRequest = { menuFor = null },
                            containerColor = Asd.Asphalt,
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.move), color = Asd.Paper) },
                                onClick = { menuFor = null; moving = index },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove_from_playlist), color = Asd.Spray) },
                                onClick = { menuFor = null; viewModel.removeAt(playlist.id, index) },
                            )
                        }
                    }
                    HorizontalDivider(color = Asd.Concrete)
                }
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = stringResource(R.string.rename),
            initial = playlist.name,
            onConfirm = { viewModel.rename(playlist.id, it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = stringResource(R.string.burn_title),
            message = stringResource(R.string.burn_message, playlist.name),
            confirmLabel = stringResource(R.string.burn),
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
