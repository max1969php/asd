package com.asd.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asd.player.domain.model.Song
import com.asd.player.domain.model.SongSource
import com.asd.player.ui.theme.Asd

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    index: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(if (isCurrent) Asd.Acid else Asd.Toner)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        val ink = if (isCurrent) Asd.Toner else Asd.Paper
        val faded = if (isCurrent) Asd.Toner else Asd.Smudge
        Text(
            text = "%03d".format(index + 1),
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrent) Asd.Toner else Asd.Spray,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(song.displayArtist, song.album).joinToString(" // "),
                style = MaterialTheme.typography.bodySmall,
                color = faded,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatDuration(song.durationMs), style = MaterialTheme.typography.labelSmall, color = ink)
            // ☁ = suona in streaming da Google Drive.
            Text(
                when (song.source) {
                    SongSource.LOCAL -> song.format
                    SongSource.DRIVE -> "☁ ${song.format}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = faded,
            )
        }
        trailing?.invoke()
    }
}
