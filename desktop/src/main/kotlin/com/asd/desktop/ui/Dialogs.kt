package com.asd.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.asd.desktop.Desktop
import com.asd.desktop.SleepTimer
import com.asd.desktop.SongDetails
import com.asd.desktop.Texts
import com.asd.player.data.loudness.VolumeLeveling
import com.asd.player.domain.model.Song
import com.asd.player.ui.components.PunkButton
import com.asd.player.ui.components.Stamp
import com.asd.player.ui.theme.Asd
import java.io.File
import java.net.URI
import java.util.Locale
import org.jetbrains.skia.Image as SkiaImage

/** Finestrella nello stile di ASD: fondo nero, bordo acido, titolo timbrato. */
@Composable
fun PunkDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(min = 360.dp, max = 560.dp).background(Asd.Toner).border(2.dp, Asd.Acid).padding(20.dp),
        ) {
            Stamp(title, color = Asd.Acid)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun Buttons(content: @Composable () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
fun NameField(text: String, onChange: (String) -> Unit, hint: String, onEnter: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxWidth().border(1.5.dp, Asd.Smudge).padding(horizontal = 10.dp, vertical = 8.dp)) {
        if (text.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = Asd.Smudge)
        BasicTextField(
            value = text,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Asd.Paper),
            cursorBrush = SolidColor(Asd.Acid),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).onKeyEvent {
                if (it.key == Key.Enter) {
                    onEnter()
                    true
                } else {
                    false
                }
            },
        )
    }
}

/** Nome di un mixtape (nuovo o da rinominare). */
@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val ok = { if (name.isNotBlank()) onConfirm(name.trim()) }
    PunkDialog(title, onDismiss) {
        NameField(name, { name = it }, Texts.PLAYLIST_NAME_HINT, ok)
        Buttons {
            PunkButton(Texts.CANCEL, onClick = onDismiss, container = Asd.Concrete, content = Asd.Paper, shadow = Asd.Toner)
            PunkButton(confirm, onClick = ok, enabled = name.isNotBlank())
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    PunkDialog(title, onDismiss) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Asd.Paper)
        Buttons {
            PunkButton(Texts.CANCEL, onClick = onDismiss, container = Asd.Concrete, content = Asd.Paper, shadow = Asd.Toner)
            PunkButton(confirm, onClick = onConfirm, container = Asd.Spray, content = Asd.Paper, shadow = Asd.Acid)
        }
    }
}

/** METTI IN UN MIXTAPE: uno di quelli che ci sono, o uno nuovo con questi pezzi dentro. */
@Composable
fun AddToMixtapeDialog(desktop: Desktop, songs: List<Song>, onDismiss: () -> Unit) {
    val tapes by desktop.mixtapes.all.collectAsState()
    var name by remember { mutableStateOf("") }
    val uris = songs.map { it.uri }
    val create = {
        if (name.isNotBlank()) {
            desktop.mixtapes.create(name, uris)
            onDismiss()
        }
    }
    PunkDialog(Texts.ADD_TO_PLAYLIST, onDismiss) {
        if (songs.size == 1) {
            Text(songs[0].title.uppercase(), style = MaterialTheme.typography.titleMedium, color = Asd.Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text(Texts.songs(songs.size), style = MaterialTheme.typography.titleMedium, color = Asd.Paper)
        }
        Spacer(Modifier.height(12.dp))
        val tapeScroll = rememberScrollState()
        Box(Modifier.heightIn(max = 280.dp)) {
        Column(Modifier.verticalScroll(tapeScroll).padding(end = 14.dp)) {
            tapes.forEach { m ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        desktop.mixtapes.add(m.id, uris)
                        onDismiss()
                    }.padding(vertical = 8.dp),
                ) {
                    Text("▸", color = Asd.Spray, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Text(m.name.uppercase(), style = MaterialTheme.typography.titleMedium, color = Asd.Paper, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Texts.songs(m.uris.size), style = MaterialTheme.typography.labelSmall, color = Asd.Smudge)
                }
            }
        }
        ColumnScrollbar(tapeScroll)
        }
        Spacer(Modifier.height(12.dp))
        Text(Texts.NEW_PLAYLIST_CAPS, style = MaterialTheme.typography.labelMedium, color = Asd.Acid)
        Spacer(Modifier.height(6.dp))
        NameField(name, { name = it }, Texts.PLAYLIST_NAME_HINT, create)
        Buttons {
            PunkButton(Texts.CANCEL, onClick = onDismiss, container = Asd.Concrete, content = Asd.Paper, shadow = Asd.Toner)
            PunkButton(Texts.CREATE_AND_ADD, onClick = create, enabled = name.isNotBlank())
        }
    }
}

/** INFO (tag, formato, volume misurato) oppure TESTO, del pezzo. */
@Composable
fun DetailsDialog(desktop: Desktop, song: Song, lyrics: Boolean, onDismiss: () -> Unit) {
    val details by produceState<SongDetails?>(null, song.uri) { value = desktop.details(song) }
    PunkDialog(if (lyrics) Texts.LYRICS else Texts.INFO, onDismiss) {
        Text(song.title.uppercase(), style = MaterialTheme.typography.titleLarge, color = Asd.Paper)
        Text(listOfNotNull(song.displayArtist, song.album).joinToString(" // "), style = MaterialTheme.typography.bodyMedium, color = Asd.Smudge)
        Spacer(Modifier.height(14.dp))
        val d = details
        val bodyScroll = rememberScrollState()
        Box(Modifier.heightIn(max = 460.dp)) {
        Column(Modifier.verticalScroll(bodyScroll).padding(end = 14.dp)) {
            when {
                d == null -> Text(Texts.INFO_READING, style = MaterialTheme.typography.bodyMedium, color = Asd.Smudge)
                lyrics -> Text(d.lyrics ?: Texts.LYRICS_MISSING, style = MaterialTheme.typography.bodyMedium, color = if (d.lyrics == null) Asd.Smudge else Asd.Paper)
                else -> InfoBody(song, d)
            }
        }
        ColumnScrollbar(bodyScroll)
        }
        Buttons { PunkButton(Texts.OK, onClick = onDismiss) }
    }
}

@Composable
private fun InfoBody(song: Song, d: SongDetails) {
    val cover = remember(d.cover) { d.cover?.let { runCatching { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() } }
    if (cover != null) {
        Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(220.dp).border(2.dp, Asd.Paper))
        Spacer(Modifier.height(14.dp))
    }
    val file = runCatching { File(URI(song.uri)) }.getOrNull()
    val gain = song.loudnessLufs?.let { VolumeLeveling.gainDb(it, null) }
    listOf(
        Texts.INFO_YEAR to d.year,
        Texts.INFO_GENRE to d.genre,
        Texts.INFO_TRACK to d.track,
        Texts.INFO_FORMAT to (d.format ?: song.format),
        Texts.INFO_BITRATE to d.bitrateKbps?.let { "$it kbps" },
        Texts.INFO_SAMPLE_RATE to d.sampleRateHz?.let { String.format(Locale.ROOT, "%.1f kHz", it / 1000.0) },
        Texts.INFO_SIZE to String.format(Locale.ROOT, "%.1f MB", song.fileSize / 1_048_576.0),
        Texts.INFO_LOUDNESS to (song.loudnessLufs?.let { String.format(Locale.ROOT, "%.1f LUFS", it) } ?: Texts.INFO_NOT_MEASURED),
        Texts.INFO_GAIN to gain?.let { String.format(Locale.ROOT, "%+.1f dB", it) },
        Texts.INFO_PATH to file?.path,
    ).forEach { (label, value) ->
        if (value != null) {
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Asd.Spray, modifier = Modifier.width(150.dp))
                Text(value, style = MaterialTheme.typography.bodySmall, color = Asd.Paper)
            }
        }
    }
}

/** Timer di spegnimento: 15/30/60 minuti, a fine pezzo, spento. */
@Composable
fun SleepDialog(desktop: Desktop, onDismiss: () -> Unit) {
    PunkDialog(Texts.SLEEP_TIMER, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SleepTimer.MINUTES.forEach { m ->
                SleepChoice(Texts.sleepMinutes(m)) {
                    desktop.sleep.inMinutes(m)
                    onDismiss()
                }
            }
            SleepChoice(Texts.SLEEP_END) {
                desktop.sleep.atEndOfSong()
                onDismiss()
            }
            SleepChoice(Texts.SLEEP_OFF) {
                desktop.sleep.cancel()
                onDismiss()
            }
        }
    }
}

@Composable
private fun SleepChoice(text: String, onClick: () -> Unit) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.titleMedium,
        color = Asd.Paper,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}
