package com.asd.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.asd.player.data.repository.ScanState
import com.asd.player.domain.model.Playlist
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SortField
import com.asd.player.ui.components.AnarchyLogo
import com.asd.player.ui.components.PunkButton
import com.asd.player.ui.components.SongRow
import com.asd.player.ui.components.Stamp
import com.asd.player.ui.components.TapeChip
import com.asd.player.ui.playlists.AddToPlaylistDialog
import com.asd.player.ui.theme.Asd

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    playlists: List<Playlist>,
    currentMediaId: String?,
    slogan: String,
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
) {
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    val listState = rememberLazyListState()

    Column(modifier.fillMaxSize()) {
        LibraryHeader(total = state.totalCount, slogan = slogan)
        SearchBox(text = state.query.text, onTextChange = viewModel::setText)
        FilterBar(state, viewModel)
        ScanBanner(state.scanState)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                "${state.songs.size} / ${state.totalCount} PEZZI",
                style = MaterialTheme.typography.labelMedium,
                color = Asd.Smudge,
                modifier = Modifier.weight(1f),
            )
            PunkButton("Tutto", onClick = { viewModel.play(state.songs, 0) }, icon = Icons.Filled.PlayArrow, enabled = state.songs.isNotEmpty())
            Spacer(Modifier.width(8.dp))
            PunkButton(
                "Caos",
                onClick = { viewModel.shuffle(state.songs) },
                icon = Icons.Filled.Shuffle,
                container = Asd.Spray,
                content = Asd.Paper,
                shadow = Asd.Paper,
                enabled = state.songs.isNotEmpty(),
            )
        }
        HorizontalDivider(thickness = 2.dp, color = Asd.Paper)

        if (state.loaded && state.songs.isEmpty()) {
            EmptyLibrary(hasSongs = state.totalCount > 0, scanning = state.scanState is ScanState.Scanning, onClear = viewModel::clearFilters)
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        index = index,
                        isCurrent = song.uri == currentMediaId,
                        onClick = { viewModel.play(state.songs, index) },
                        onLongClick = { songForPlaylist = song },
                    )
                    HorizontalDivider(color = Asd.Concrete)
                }
            }
        }
    }

    songForPlaylist?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onPick = { viewModel.addToPlaylist(it.id, song); songForPlaylist = null },
            onCreate = { viewModel.createPlaylistWith(it, song); songForPlaylist = null },
            onDismiss = { songForPlaylist = null },
        )
    }
}

@Composable
private fun LibraryHeader(total: Int, slogan: String) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnarchyLogo(size = 44.dp)
            Spacer(Modifier.width(10.dp))
            Text("ASD", style = MaterialTheme.typography.displayLarge, color = Asd.Paper)
            Spacer(Modifier.weight(1f))
            Stamp(if (total > 0) "$total pezzi · 0€" else "0€", color = Asd.Acid, rotation = 5f)
        }
        Text(
            "ANARCHIC STREAMING DEVICE",
            style = MaterialTheme.typography.labelMedium,
            color = Asd.Spray,
        )
        Text(
            "> $slogan",
            style = MaterialTheme.typography.bodySmall,
            color = Asd.Smudge,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SearchBox(text: String, onTextChange: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .border(2.dp, Asd.Paper)
            .padding(start = 12.dp),
    ) {
        Text("CERCA_", style = MaterialTheme.typography.labelLarge, color = Asd.Acid)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (text.isEmpty()) {
                Text("titolo, artista, album, file…", style = MaterialTheme.typography.bodyLarge, color = Asd.Smudge)
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Asd.Paper),
                cursorBrush = SolidColor(Asd.Acid),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (text.isNotEmpty()) {
            IconButton(onClick = { onTextChange("") }) {
                Icon(Icons.Filled.Clear, contentDescription = "Svuota", tint = Asd.Paper)
            }
        } else {
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun FilterBar(state: LibraryUiState, viewModel: LibraryViewModel) {
    var artistMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val sort = state.query.sort

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Box {
            TapeChip("Ordina: ${sort.field.label()} ${if (sort.ascending) "↑" else "↓"}", selected = false, onClick = { sortMenu = true })
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }, containerColor = Asd.Asphalt) {
                SortField.entries.forEach { field ->
                    DropdownMenuItem(
                        text = { Text(field.label(), color = if (field == sort.field) Asd.Acid else Asd.Paper) },
                        onClick = {
                            // Ritoccare lo stesso campo inverte la direzione.
                            viewModel.setSort(
                                if (field == sort.field) sort.copy(ascending = !sort.ascending) else sort.copy(field = field)
                            )
                            sortMenu = false
                        },
                    )
                }
            }
        }
        Box {
            TapeChip(
                text = state.query.artist?.let { "Artista: $it" } ?: "Artista: tutti",
                selected = state.query.artist != null,
                onClick = { artistMenu = true },
            )
            DropdownMenu(expanded = artistMenu, onDismissRequest = { artistMenu = false }, containerColor = Asd.Asphalt) {
                DropdownMenuItem(text = { Text("TUTTI", color = Asd.Acid) }, onClick = { viewModel.setArtist(null); artistMenu = false })
                state.artists.forEach { artist ->
                    DropdownMenuItem(text = { Text(artist) }, onClick = { viewModel.setArtist(artist); artistMenu = false })
                }
            }
        }
        state.formats.forEach { format ->
            TapeChip(format, selected = format in state.query.formats, onClick = { viewModel.toggleFormat(format) })
        }
    }
}

@Composable
private fun ScanBanner(scan: ScanState) {
    when (scan) {
        is ScanState.Scanning -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                if (scan.total == 0) "FRUGANDO NELLA CARTELLA…" else "INDICIZZO ${scan.processed}/${scan.total}…",
                style = MaterialTheme.typography.labelSmall,
                color = Asd.Acid,
            )
            Spacer(Modifier.height(4.dp))
            if (scan.total == 0) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Asd.Acid, trackColor = Asd.Concrete)
            } else {
                LinearProgressIndicator(
                    progress = { scan.processed.toFloat() / scan.total },
                    modifier = Modifier.fillMaxWidth(),
                    color = Asd.Acid,
                    trackColor = Asd.Concrete,
                )
            }
        }
        is ScanState.Failed -> Text(
            "ERRORE: ${scan.reason}",
            style = MaterialTheme.typography.labelSmall,
            color = Asd.Paper,
            modifier = Modifier.fillMaxWidth().background(Asd.Spray).padding(horizontal = 16.dp, vertical = 6.dp),
        )
        else -> Unit
    }
}

@Composable
private fun EmptyLibrary(hasSongs: Boolean, scanning: Boolean, onClear: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            scanning -> Text("Sto contando i tuoi pezzi.\nPazienza, non è un abbonamento.", style = MaterialTheme.typography.bodyLarge)
            hasSongs -> {
                Text("NIENTE.", style = MaterialTheme.typography.headlineLarge, color = Asd.Spray)
                Text("Nessun pezzo passa i tuoi filtri.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Text(
                    "[ AZZERA FILTRI ]",
                    style = MaterialTheme.typography.labelLarge,
                    color = Asd.Acid,
                    modifier = Modifier.clickable(onClick = onClear).padding(8.dp),
                )
            }
            else -> {
                Text("CARTELLA VUOTA.", style = MaterialTheme.typography.headlineLarge, color = Asd.Spray)
                Text(
                    "Buttaci dentro qualche MP3, WAV o FLAC e premi Riscansiona in SETUP.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

fun SortField.label(): String = when (this) {
    SortField.TITLE -> "Titolo"
    SortField.ARTIST -> "Artista"
    SortField.ALBUM -> "Album"
    SortField.DURATION -> "Durata"
    SortField.RECENT -> "Recenti"
}
