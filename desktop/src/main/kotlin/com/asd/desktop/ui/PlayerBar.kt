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
import com.asd.desktop.Player
import com.asd.desktop.Repeat
import com.asd.desktop.Texts
import com.asd.player.ui.components.formatDuration
import com.asd.player.ui.theme.Asd
import kotlinx.coroutines.delay

/** Il player in fondo alla finestra: pezzo, comandi, barra di avanzamento. */
@Composable
fun PlayerBar(player: Player) {
    val state by player.state.collectAsState()
    var position by remember { mutableLongStateOf(0L) }
    // Mentre si trascina la barra, il punto lo decide il mouse; al rilascio si salta lì.
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            position = player.positionMs
            delay(250)
        }
    }
    val song = state.current
    val duration = state.durationMs.coerceAtLeast(1)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
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
