package com.asd.player.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.asd.player.R
import com.asd.player.playback.PlaybackState
import com.asd.player.ui.components.AnarchyLogo
import com.asd.player.ui.components.Stamp
import com.asd.player.ui.components.formatDuration
import com.asd.player.ui.theme.Asd

@Composable
fun PlayerScreen(
    state: PlaybackState,
    positionMs: Long,
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val now = state.nowPlaying

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Asd.Toner)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.close), tint = Asd.Paper, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.weight(1f))
            Stamp(if (state.isPlaying) stringResource(R.string.on_air) else stringResource(R.string.paused), color = if (state.isPlaying) Asd.Spray else Asd.Smudge, rotation = 3f)
        }

        Spacer(Modifier.weight(0.5f))
        SpinningRecord(spinning = state.isPlaying, modifier = Modifier.fillMaxWidth(0.75f))
        Spacer(Modifier.weight(0.5f))

        Text(
            (now?.title ?: stringResource(R.string.silence)).uppercase(),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            listOfNotNull(now?.artist ?: stringResource(R.string.unknown_artist), now?.album).joinToString(" // "),
            style = MaterialTheme.typography.bodyMedium,
            color = Asd.Acid,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(24.dp))
        SeekBar(positionMs, state.durationMs, viewModel::seekTo)

        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ToggleIcon(Icons.Filled.Shuffle, stringResource(R.string.shuffle), active = state.shuffle, onClick = viewModel::toggleShuffle)
            IconButton(onClick = viewModel::previous, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.previous), tint = Asd.Paper, modifier = Modifier.size(40.dp))
            }
            BigPlayButton(isPlaying = state.isPlaying, onClick = viewModel::togglePlayPause)
            IconButton(onClick = viewModel::next, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next), tint = Asd.Paper, modifier = Modifier.size(40.dp))
            }
            ToggleIcon(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                stringResource(R.string.repeat),
                active = state.repeatMode != Player.REPEAT_MODE_OFF,
                onClick = viewModel::cycleRepeat,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Un vinile nero con la A cerchiata come etichetta: gira finché suona. */
@Composable
private fun SpinningRecord(spinning: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "record")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .rotate(if (spinning) angle else 0f)
            .background(Asd.Asphalt, shape = CircleShape)
            .border(3.dp, Asd.Paper, CircleShape),
    ) {
        Box(
            Modifier
                .fillMaxSize(0.82f)
                .border(1.dp, Asd.Concrete, CircleShape)
        )
        Box(
            Modifier
                .fillMaxSize(0.62f)
                .border(1.dp, Asd.Concrete, CircleShape)
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize(0.38f)
                .background(Asd.Acid, CircleShape),
        ) {
            AnarchyLogo(color = Asd.Toner, size = 72.dp)
        }
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = dragging ?: fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * durationMs).toLong()) }
                dragging = null
            },
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = Asd.Spray,
                activeTrackColor = Asd.Acid,
                inactiveTrackColor = Asd.Concrete,
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            val shown = dragging?.let { (it * durationMs).toLong() } ?: positionMs
            Text(formatDuration(shown), style = MaterialTheme.typography.labelSmall, color = Asd.Paper)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(durationMs), style = MaterialTheme.typography.labelSmall, color = Asd.Smudge)
        }
    }
}

@Composable
private fun BigPlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(Modifier.padding(end = 6.dp, bottom = 6.dp)) {
        Box(Modifier.matchParentSize().padding(start = 6.dp, top = 6.dp).background(Asd.Spray))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .background(Asd.Acid)
                .border(3.dp, Asd.Toner)
                .clickable(onClick = onClick),
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                tint = Asd.Toner,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = if (active) Asd.Acid else Asd.Smudge)
    }
}

@Composable
fun MiniPlayer(
    state: PlaybackState,
    positionMs: Long,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
) {
    val now = state.nowPlaying ?: return
    val progress = if (state.durationMs > 0) (positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().background(Asd.Acid).clickable(onClick = onExpand)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Asd.Toner)) {
            Box(Modifier.fillMaxWidth(progress).height(3.dp).background(Asd.Spray))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    now.title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Asd.Toner,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    now.artist ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.bodySmall,
                    color = Asd.Toner,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                    tint = Asd.Toner,
                )
            }
            IconButton(onClick = onNext, enabled = state.hasNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next), tint = if (state.hasNext) Asd.Toner else Color.DarkGray)
            }
        }
    }
}
