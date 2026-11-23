package com.asd.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asd.desktop.Desktop
import com.asd.desktop.ScanState
import com.asd.desktop.Texts
import com.asd.player.domain.model.LibraryQuery
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SortField
import com.asd.player.domain.usecase.FolderPath
import com.asd.player.domain.usecase.FolderView
import com.asd.player.domain.usecase.LibraryView
import com.asd.player.ui.components.PunkButton
import com.asd.player.ui.components.SongRow
import com.asd.player.ui.components.TapeChip
import com.asd.player.ui.theme.Asd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

/** ROBA: la cartella come cartelle (o per artisti/album), ricerca, ordinamento, Tutto e Caos. */
@Composable
fun LibraryScreen(desktop: Desktop, onOpenSettings: () -> Unit) {
    val settings by desktop.settings.state.collectAsState()
    val songs by desktop.library.songs.collectAsState()
    val scan by desktop.library.scan.collectAsState()
    val playing by desktop.player.state.collectAsState()
    var text by remember { mutableStateOf("") }
    var duplicates by remember { mutableStateOf(false) }
    var formats by remember { mutableStateOf(emptySet<String>()) }
    var forMixtape by remember { mutableStateOf<List<Song>?>(null) }
    forMixtape?.let { AddToMixtapeDialog(desktop, it, onDismiss = { forMixtape = null }) }

    if (settings.folder == null) {
        Empty(Texts.NO_FOLDER, Texts.NO_FOLDER_HINT) { PunkButton(Texts.CHOOSE_FOLDER, onClick = desktop::chooseFolder) }
        return
    }

    val path = FolderPath(dirs = settings.path, view = settings.view)
    val query = LibraryQuery(text = text, formats = formats, duplicates = duplicates, sort = settings.sort)
    val allFormats = remember(songs) { songs.map { it.format }.filter { it.isNotEmpty() }.distinct().sorted() }
    val view by produceState<FolderView?>(null, songs, path, query) {
        value = withContext(Dispatchers.Default) { desktop.browse(songs, path, query) }
    }

    Row(Modifier.fillMaxSize()) {
        if (settings.showTree) {
            FolderTree(
                songs, settings.view, current = view?.path?.dirs ?: settings.path,
                onOpen = { p -> desktop.settings.update { it.copy(path = p, scroll = 0) } },
                modifier = Modifier.width(280.dp).fillMaxHeight(),
            )
            VerticalDivider(color = Asd.Concrete, thickness = 2.dp)
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = { desktop.settings.update { it.copy(showTree = !it.showTree) } }) {
                        Icon(Icons.Filled.AccountTree, contentDescription = null, tint = if (settings.showTree) Asd.Acid else Asd.Smudge)
                    }
                    LibraryView.entries.forEach { v ->
                        TapeChip(v.label(), selected = v == settings.view, onClick = {
                            desktop.settings.update { it.copy(view = v, path = emptyList(), scroll = 0) }
                        })
                    }
                    Spacer(Modifier.width(16.dp))
                    SearchField(text, onChange = { text = it }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SortField.entries.forEach { f ->
                        val selected = settings.sort.field == f
                        TapeChip(
                            f.label() + if (selected) (if (settings.sort.ascending) " ↑" else " ↓") else "",
                            selected = selected,
                            onClick = {
                                desktop.settings.update {
                                    it.copy(sort = if (selected) it.sort.copy(ascending = !it.sort.ascending) else it.sort.copy(field = f, ascending = true))
                                }
                            },
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    TapeChip(Texts.DUPLICATES, selected = duplicates, onClick = { duplicates = !duplicates })
                    if (allFormats.size > 1) {
                        allFormats.forEach { f ->
                            TapeChip(f, selected = f in formats, onClick = { formats = if (f in formats) formats - f else formats + f })
                        }
                    }
                    if (query.filtering) TapeChip(Texts.FILTERS_RESET, selected = false, onClick = {
                        text = ""
                        duplicates = false
                        formats = emptySet()
                    })
                    Spacer(Modifier.weight(1f))
                    ScanLine(scan)
                }
            }

            val v = view ?: return@Column
            // La barra del percorso: toccando un pezzo si risale fin lì.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(Asd.Asphalt).padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                val crumbs = listOf(Texts.TAB_LIBRARY.uppercase()) + v.path.dirs
                crumbs.forEachIndexed { i, name ->
                    Text(
                        (if (i > 0) " / " else "") + name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (i == crumbs.lastIndex) Asd.Acid else Asd.Paper,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { desktop.settings.update { it.copy(path = v.path.dirs.take(i), scroll = 0) } },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(Texts.songs(v.below.size), style = MaterialTheme.typography.labelSmall, color = Asd.Smudge)
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = { forMixtape = v.below }, enabled = v.below.isNotEmpty()) {
                    Icon(Icons.Filled.PlaylistAdd, contentDescription = Texts.ADD_TO_PLAYLIST, tint = Asd.Paper)
                }
                Spacer(Modifier.width(6.dp))
                PunkButton(Texts.PLAY_ALL, onClick = { desktop.player.playQueue(v.below) }, icon = Icons.Filled.PlayArrow, enabled = v.below.isNotEmpty())
                Spacer(Modifier.width(6.dp))
                PunkButton(
                    Texts.CHAOS, onClick = { desktop.player.playQueue(v.below, shuffle = true) },
                    icon = Icons.Filled.Shuffle, container = Asd.Spray, content = Asd.Paper, shadow = Asd.Acid, enabled = v.below.isNotEmpty(),
                )
            }

            if (songs.isEmpty() && scan !is ScanState.Scanning) {
                Empty(Texts.LIBRARY_EMPTY, Texts.LIBRARY_EMPTY_HINT) { PunkButton(Texts.RESCAN, onClick = desktop::rescan) }
                return@Column
            }
            if (query.filtering && v.songs.isEmpty() && v.folders.isEmpty()) {
                Empty(Texts.LIBRARY_NOTHING, Texts.LIBRARY_NO_MATCH) {
                    PunkButton(Texts.RESET_FILTERS, onClick = {
                        text = ""
                        duplicates = false
                        formats = emptySet()
                    })
                }
                return@Column
            }

            // Una lista per cartella: si ricorda il punto, anche dopo la riapertura.
            key(v.path) {
                val list = rememberLazyListState(initialFirstVisibleItemIndex = if (v.path.dirs == settings.path) settings.scroll else 0)
                LaunchedEffect(list) {
                    snapshotFlow { list.firstVisibleItemIndex }.debounce(500).collect { i -> desktop.settings.update { it.copy(scroll = i) } }
                }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
                        if (!v.path.isRoot && !query.filtering) {
                            item { FolderRow("..", null) { desktop.settings.update { it.copy(path = v.path.up().dirs, scroll = 0) } } }
                        }
                        items(v.folders, key = { "d:" + it.name }) { f ->
                            FolderRow(f.name, f.songCount) { desktop.settings.update { it.copy(path = v.path.dirs + f.name, scroll = 0) } }
                        }
                        itemsIndexed(v.songs, key = { _, s -> s.id }) { i, s ->
                            SongRow(
                                s, i,
                                isCurrent = s.uri == playing.current?.uri,
                                onClick = { desktop.player.playQueue(v.songs, i) },
                                onLongClick = { forMixtape = listOf(s) },
                                // Con i doppioni si vede dove sta ogni copia.
                                subtitle = if (duplicates) (s.folder + s.fileName).joinToString("/") else null,
                                trailing = {
                                    IconButton(onClick = { forMixtape = listOf(s) }) {
                                        Icon(Icons.Filled.Add, contentDescription = Texts.ADD_TO_PLAYLIST, tint = if (s.uri == playing.current?.uri) Asd.Toner else Asd.Smudge)
                                    }
                                },
                            )
                        }
                    }
                    ListScrollbar(list)
                }
            }
        }
    }
}

@Composable
private fun FolderRow(name: String, count: Int?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("▸", style = MaterialTheme.typography.titleMedium, color = Asd.Spray)
        Spacer(Modifier.width(12.dp))
        Text(name.uppercase(), style = MaterialTheme.typography.titleMedium, color = Asd.Paper, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count != null) Text(Texts.songs(count), style = MaterialTheme.typography.labelSmall, color = Asd.Smudge)
    }
}

@Composable
private fun SearchField(text: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.border(1.5.dp, Asd.Smudge).padding(horizontal = 10.dp, vertical = 7.dp)) {
        if (text.isEmpty()) Text(Texts.SEARCH_HINT, style = MaterialTheme.typography.bodyMedium, color = Asd.Smudge)
        BasicTextField(
            value = text,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Asd.Paper),
            cursorBrush = SolidColor(Asd.Acid),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ScanLine(scan: ScanState) {
    val (msg, color) = when (scan) {
        ScanState.Idle -> return
        is ScanState.Scanning -> (if (scan.total == 0) Texts.SCAN_LISTING else Texts.scanIndexing(scan.done, scan.total)) to Asd.Acid
        is ScanState.Done -> Texts.scanSummary(scan.added, scan.updated, scan.removed) to Asd.Smudge
        is ScanState.Failed -> Texts.scanError(scan.message) to Asd.Spray
    }
    Text(msg, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun Empty(title: String, hint: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = Asd.Paper)
        Spacer(Modifier.height(12.dp))
        Text(hint, style = MaterialTheme.typography.bodyLarge, color = Asd.Smudge)
        Spacer(Modifier.height(24.dp))
        action()
    }
}

private fun LibraryView.label() = when (this) {
    LibraryView.FOLDERS -> Texts.VIEW_FOLDERS
    LibraryView.ARTISTS -> Texts.VIEW_ARTISTS
    LibraryView.ALBUMS -> Texts.VIEW_ALBUMS
}

private fun SortField.label() = when (this) {
    SortField.TITLE -> Texts.SORT_TITLE
    SortField.ARTIST -> Texts.SORT_ARTIST
    SortField.ALBUM -> Texts.SORT_ALBUM
    SortField.DURATION -> Texts.SORT_DURATION
    SortField.RECENT -> Texts.SORT_RECENT
}
