package com.asd.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asd.desktop.Texts
import com.asd.player.domain.model.Song
import com.asd.player.domain.usecase.LibraryView
import com.asd.player.ui.theme.Asd
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un ramo dell'albero: il percorso è quello di ROBA (FolderPath.dirs). */
data class TreeNode(val name: String, val path: List<String>, val count: Int, val children: List<TreeNode>)

/**
 * L'albero delle cartelle (o artisti → album, o album) per vedere a colpo d'occhio cosa c'è sotto.
 * Stessi livelli di ROBA (BrowseFolder): nomi uguali a meno delle maiuscole sono lo stesso ramo.
 */
object FolderTrees {
    private val collator: Collator = Collator.getInstance(Locale.ITALIAN).apply { strength = Collator.PRIMARY }

    fun build(songs: List<Song>, view: LibraryView): List<TreeNode> {
        val branch: (Song) -> List<String> = when (view) {
            LibraryView.FOLDERS -> { s -> s.folder }
            LibraryView.ARTISTS -> { s -> listOfNotNull(s.displayArtist, s.album?.trim()?.takeIf { it.isNotEmpty() }) }
            LibraryView.ALBUMS -> { s -> listOfNotNull(s.album?.trim()?.takeIf { it.isNotEmpty() }) }
        }
        return level(songs.map { branch(it) }, emptyList())
    }

    private fun level(branches: List<List<String>>, path: List<String>): List<TreeNode> {
        val depth = path.size
        return branches.filter { it.size > depth }
            .groupBy { it[depth].lowercase() }
            .values
            .map { group ->
                val name = group.first()[depth]
                TreeNode(name, path + name, group.size, level(group, path + name))
            }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    /** Le righe visibili: i rami aperti in [open] (chiave = percorso in minuscolo). */
    fun visible(nodes: List<TreeNode>, open: Set<String>, depth: Int = 0): List<Pair<TreeNode, Int>> =
        nodes.flatMap { n -> listOf(n to depth) + if (key(n.path) in open) visible(n.children, open, depth + 1) else emptyList() }

    fun key(path: List<String>) = path.joinToString("\u0000") { it.lowercase() }
}

/** Il pannello: tocchi un nome e ROBA va lì; la freccia apre e chiude senza spostarsi. */
@Composable
fun FolderTree(songs: List<Song>, view: LibraryView, current: List<String>, onOpen: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    val roots by produceState(emptyList<TreeNode>(), songs, view) { value = withContext(Dispatchers.Default) { FolderTrees.build(songs, view) } }
    var open by remember(view) { mutableStateOf(emptySet<String>()) }
    // Il ramo della cartella aperta in ROBA resta sempre visibile.
    LaunchedEffect(current, view) {
        open = open + (1..current.size).map { FolderTrees.key(current.take(it)) }
    }
    val rows = remember(roots, open) { FolderTrees.visible(roots, open) }
    val currentKey = FolderTrees.key(current)
    val state = rememberLazyListState()

    Box(modifier.background(Asd.Toner)) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
            item {
                TreeRow(Texts.TAB_LIBRARY.uppercase(), depth = 0, arrow = null, selected = current.isEmpty(), count = songs.size, onArrow = {}) { onOpen(emptyList()) }
            }
            items(rows, key = { (n, _) -> FolderTrees.key(n.path) }) { (node, depth) ->
                val k = FolderTrees.key(node.path)
                val arrow = when {
                    node.children.isEmpty() -> null
                    k in open -> "▾"
                    else -> "▸"
                }
                TreeRow(
                    node.name, depth + 1, arrow, selected = k == currentKey, count = node.count,
                    onArrow = { open = if (k in open) open - k else open + k },
                ) {
                    open = open + k
                    onOpen(node.path)
                }
            }
        }
        ListScrollbar(state)
    }
}

@Composable
private fun TreeRow(name: String, depth: Int, arrow: String?, selected: Boolean, count: Int, onArrow: () -> Unit, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(if (selected) Asd.Acid else Asd.Toner).clickable(onClick = onClick)
            .padding(start = (8 + depth * 14).dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Box(Modifier.width(18.dp).clickable(enabled = arrow != null, onClick = onArrow), contentAlignment = Alignment.Center) {
            Text(arrow ?: "·", style = MaterialTheme.typography.labelLarge, color = if (selected) Asd.Toner else if (arrow != null) Asd.Spray else Asd.Concrete)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Asd.Toner else Asd.Paper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = if (selected) Asd.Toner else Asd.Smudge)
    }
}
