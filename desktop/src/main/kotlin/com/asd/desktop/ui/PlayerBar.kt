package com.asd.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.asd.desktop.Desktop
import com.asd.desktop.SleepState
import com.asd.player.ui.components.AnarchyLogo
import org.jetbrains.skia.Image as SkiaImage
import com.asd.desktop.Repeat
import com.asd.desktop.Texts
import com.asd.player.ui.components.formatDuration
import com.asd.player.ui.theme.Asd
import kotlinx.coroutines.delay

/** Il player in fondo alla finestra: pezzo, comandi, barra di avanzamento. */
@Composable
fun PlayerBar(desktop: Desktop) {
    val player = desktop.player
    val state by player.state.collectAsState()
    val sleep by desktop.sleep.state.collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }
    var sleepLeft by remember { mutableStateOf<Long?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    // Mentre si trascina la barra, il punto lo decide il mouse; al rilascio si salta lì.
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            position = player.positionMs
            sleepLeft = desktop.sleep.leftMs()
            delay(250)
        }
    }
    val song = state.current
    val duration = state.durationMs.coerceAtLeast(1)
    val cover by produceState<ImageBitmap?>(null, song?.uri) {
        value = song?.let { s -> desktop.details(s).cover?.let { runCatching { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() } }
    }
    when (dialog) {
        "info" -> song?.let { DetailsDialog(desktop, it, lyrics = false, onDismiss = { dialog = null }) }
        "lyrics" -> song?.let { DetailsDialog(desktop, it, lyrics = true, onDismiss = { dialog = null }) }
        "sleep" -> SleepDialog(desktop, onDismiss = { dialog = null })
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(Modifier.size(48.dp).background(Asd.Concrete).clickable(enabled = song != null) { dialog = "info" }, contentAlignment = Alignment.Center) {
            val c = cover
            if (c != null) Image(c, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else AnarchyLogo(size = 28.dp, color = Asd.Smudge)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.widthIn(min = 200.dp, max = 320.dp)) {
            Text(
                song?.title?.uppercase() ?: Texts.NOTHING_PLAYING,
                style = MaterialTheme.typography.titleMedium,
                color = Asd.Paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song?.let { listOfNotNull(it.displayArtist, it.album).joinToString(" // ") } ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Asd.Smudge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Control(Icons.Filled.Shuffle, active = state.shuffle) { player.toggleShuffle() }
        Control(Icons.Filled.SkipPrevious) { player.previous() }
        Control(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, big = true) { player.togglePlayPause() }
        Control(Icons.Filled.SkipNext) { player.next() }
        Control(if (state.repeat == Repeat.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, active = state.repeat != Repeat.OFF) {
            player.cycleRepeat()
        }
        Spacer(Modifier.width(12.dp))
        Text(formatDuration(if (dragging) (dragValue * duration).toLong() else position), style = MaterialTheme.typography.labelSmall, color = Asd.Paper)
        Slider(
            value = if (dragging) dragValue else (position.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                player.seekTo((dragValue * duration).toLong())
                dragging = false
            },
            enabled = song != null,
            colors = SliderDefaults.colors(thumbColor = Asd.Acid, activeTrackColor = Asd.Acid, inactiveTrackColor = Asd.Concrete),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelSmall, color = Asd.Paper)
        Spacer(Modifier.width(8.dp))
        Control(Icons.Filled.Info, active = song != null) { if (song != null) dialog = "info" }
        Control(Icons.Filled.Lyrics, active = song != null) { if (song != null) dialog = "lyrics" }
        Control(Icons.Filled.Bedtime, active = sleep != SleepState.Off) { dialog = "sleep" }
        when (sleep) {
            is SleepState.At -> sleepLeft?.let { Text(formatDuration(it), style = MaterialTheme.typography.labelSmall, color = Asd.Acid) }
            SleepState.EndOfSong -> Text("⏹", style = MaterialTheme.typography.labelSmall, color = Asd.Acid)
            SleepState.Off -> Unit
        }
    }
}

@Composable
private fun Control(icon: ImageVector, active: Boolean = true, big: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (big) Asd.Acid else if (active) Asd.Paper else Asd.Smudge,
            modifier = Modifier.padding(if (big) 0.dp else 4.dp),
        )
    }
}
