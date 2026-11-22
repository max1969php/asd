package com.asd.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asd.desktop.Desktop
import com.asd.desktop.Texts
import com.asd.player.ui.components.PunkButton
import com.asd.player.ui.components.SongRow
import com.asd.player.ui.components.Stamp
import com.asd.player.ui.components.formatDuration
import com.asd.player.ui.theme.Asd

/** MIXTAPE: i nastri a sinistra, quello aperto a destra (play, caos, ordine, rinomina, brucia). */
@Composable
fun PlaylistsScreen(desktop: Desktop) {
    val tapes by desktop.mixtapes.all.collectAsState()
    val songs by desktop.library.songs.collectAsState()
    val playing by desktop.player.state.collectAsState()
    var openId by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var burning by remember { mutableStateOf(false) }
    val open = tapes.firstOrNull { it.id == openId } ?: tapes.firstOrNull()

    Row(Modifier.fillMaxSize().background(Asd.Toner)) {
        Column(Modifier.width(300.dp).fillMaxHeight().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stamp(Texts.playlistsStamp(tapes.size))
                Spacer(Modifier.width(10.dp))
                Text(Texts.PLAYLISTS_SUBTITLE, style = MaterialTheme.typography.bodySmall, color = Asd.Smudge)
            }
            Spacer(Modifier.height(16.dp))
            PunkButton(Texts.NEW_PLAYLIST, onClick = { creating = true }, icon = Icons.Filled.Add)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(tapes, key = { it.id }) { m ->
                    val selected = m.id == open?.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().background(if (selected) Asd.Acid else Asd.Toner)
                            .clickable { openId = m.id }.padding(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Text(
                            m.name.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) Asd.Toner else Asd.Paper,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(m.uris.size.toString(), style = MaterialTheme.typography.labelSmall, color = if (selected) Asd.Toner else Asd.Smudge)
                    }
                }
            }
        }
        VerticalDivider(color = Asd.Concrete, thickness = 2.dp)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (open == null) {
                Column(Modifier.padding(32.dp)) {
                    Text(Texts.PLAYLISTS_NONE, style = MaterialTheme.typography.headlineLarge, color = Asd.Paper)
                    Spacer(Modifier.height(12.dp))
                    Text(Texts.PLAYLISTS_NONE_HINT, style = MaterialTheme.typography.bodyLarge, color = Asd.Smudge)
                }
                return@Column
            }
            val entries = desktop.songsOf(open, songs)
            val queue = entries.map { it.second }
            Column(Modifier.background(Asd.Asphalt).fillMaxWidth().padding(16.dp)) {
                Text(open.name.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Asd.Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Texts.sideA(queue.size) + " · " + formatDuration(queue.sumOf { it.durationMs }),
                    style = MaterialTheme.typography.labelSmall,
                    color = Asd.Smudge,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PunkButton(Texts.PLAY, onClick = { desktop.player.playQueue(queue) }, icon = Icons.Filled.PlayArrow, enabled = queue.isNotEmpty())
                    PunkButton(
                        Texts.CHAOS, onClick = { desktop.player.playQueue(queue, shuffle = true) }, icon = Icons.Filled.Shuffle,
                        container = Asd.Spray, content = Asd.Paper, shadow = Asd.Acid, enabled = queue.isNotEmpty(),
                    )
                    PunkButton(Texts.RENAME, onClick = { renaming = true }, container = Asd.Concrete, content = Asd.Paper, shadow = Asd.Toner)
                    PunkButton(Texts.DELETE_PLAYLIST, onClick = { burning = true }, container = Asd.Concrete, content = Asd.Spray, shadow = Asd.Toner)
                }
            }
            if (queue.isEmpty()) {
                Text(Texts.PLAYLIST_EMPTY, style = MaterialTheme.typography.bodyLarge, color = Asd.Smudge, modifier = Modifier.padding(32.dp))
            }
            LazyColumn(Modifier.weight(1f)) {
                items(entries.size, key = { open.uris[entries[it].first] + "#" + entries[it].first }) { i ->
                    val (tapeIndex, song) = entries[i]
                    val ink = if (song.uri == playing.current?.uri) Asd.Toner else Asd.Paper
                    SongRow(
                        song, i,
                        isCurrent = song.uri == playing.current?.uri,
                        onClick = { desktop.player.playQueue(queue, i) },
                        trailing = {
                            Row {
                                Small(Icons.Filled.KeyboardArrowUp, enabled = tapeIndex > 0, ink = ink) { desktop.mixtapes.move(open.id, tapeIndex, tapeIndex - 1) }
                                Small(Icons.Filled.KeyboardArrowDown, enabled = tapeIndex < open.uris.lastIndex, ink = ink) { desktop.mixtapes.move(open.id, tapeIndex, tapeIndex + 1) }
                                Small(Icons.Filled.Close, ink = ink) { desktop.mixtapes.remove(open.id, tapeIndex) }
                            }
                        },
                    )
                }
            }
        }
    }

    if (creating) {
        NameDialog(Texts.NEW_PLAYLIST, "", Texts.CREATE, onDismiss = { creating = false }) {
            openId = desktop.mixtapes.create(it)
            creating = false
        }
    }
    if (renaming && open != null) {
        NameDialog(Texts.RENAME, open.name, Texts.OK, onDismiss = { renaming = false }) {
            desktop.mixtapes.rename(open.id, it)
            renaming = false
        }
    }
    if (burning && open != null) {
        ConfirmDialog(Texts.BURN_TITLE, Texts.burnMessage(open.name), Texts.BURN, onDismiss = { burning = false }) {
            desktop.mixtapes.delete(open.id)
            openId = null
            burning = false
        }
    }
}

@Composable
private fun Small(icon: ImageVector, enabled: Boolean = true, ink: Color = Asd.Paper, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = null, tint = if (enabled) ink else ink.copy(alpha = 0.25f))
    }
}
