package com.asd.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asd.desktop.Desktop
import com.asd.desktop.I18n
import com.asd.desktop.Texts
import com.asd.desktop.Version
import com.asd.player.ui.components.PunkButton
import com.asd.player.ui.components.Stamp
import com.asd.player.ui.components.TapeChip
import com.asd.player.ui.theme.Asd

/** SETUP: cartella, riscansione, volume e silenzio, lingua, link. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(desktop: Desktop) {
    val settings by desktop.settings.state.collectAsState()
    val scan by desktop.library.scan.collectAsState()
    val progress by desktop.analysis.progress.collectAsState()

    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 24.dp, top = 24.dp, bottom = 24.dp, end = 36.dp)) {
        Stamp(Texts.FOLDER)
        Spacer(Modifier.height(12.dp))
        Text(settings.folder ?: Texts.NO_FOLDER, style = MaterialTheme.typography.bodyLarge, color = Asd.Paper)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PunkButton(Texts.CHANGE_FOLDER, onClick = desktop::chooseFolder, icon = Icons.Filled.Folder)
            PunkButton(Texts.RESCAN, onClick = desktop::rescan, icon = Icons.Filled.Refresh, enabled = settings.folder != null)
        }
        Spacer(Modifier.height(8.dp))
        ScanLine(scan)

        Spacer(Modifier.height(32.dp))
        Toggle(Texts.VOLUME_TITLE, Texts.VOLUME_HINT, settings.normalizeVolume, desktop::setNormalizeVolume)
        if (settings.normalizeVolume || settings.skipSilence) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (progress.pending > 0) Texts.measuring(progress.pending) else Texts.ALL_MEASURED,
                style = MaterialTheme.typography.labelSmall,
                color = if (progress.pending > 0) Asd.Acid else Asd.Smudge,
            )
        }
        Spacer(Modifier.height(24.dp))
        Toggle(Texts.SILENCE_TITLE, Texts.SILENCE_HINT, settings.skipSilence, desktop::setSkipSilence)

        if (!desktop.player.canPlayAac) {
            Spacer(Modifier.height(24.dp))
            Text(Texts.MISSING_AAC, style = MaterialTheme.typography.bodyMedium, color = Asd.Spray)
        }

        Spacer(Modifier.height(32.dp))
        TubeSettings(desktop)

        Spacer(Modifier.height(32.dp))
        Stamp(Texts.LANGUAGE, color = Asd.Paper)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.widthIn(max = 900.dp)) {
            TapeChip(Texts.LANGUAGE_SYSTEM, selected = settings.language == null, onClick = { desktop.setLanguage(null) })
            I18n.SUPPORTED.forEach { tag ->
                TapeChip(I18n.nativeName(tag), selected = settings.language == tag, onClick = { desktop.setLanguage(tag) })
            }
        }

        Spacer(Modifier.height(32.dp))
        Stamp(Texts.SITE, color = Asd.Acid)
        Spacer(Modifier.height(12.dp))
        Link(SITE_URL)
        Link(REPO_URL)
        Spacer(Modifier.height(24.dp))
        Text("${Texts.APP} · ${Texts.VERSION} ${Version.NAME}", style = MaterialTheme.typography.bodySmall, color = Asd.Smudge)
    }
    ColumnScrollbar(scroll)
    }
}

@Composable
private fun Toggle(title: String, hint: String, on: Boolean, set: (Boolean) -> Unit) {
    Stamp(title, color = Asd.Paper)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TapeChip(Texts.ON, selected = on, onClick = { set(true) })
        TapeChip(Texts.OFF, selected = !on, onClick = { set(false) })
    }
    Spacer(Modifier.height(8.dp))
    Text(hint, style = MaterialTheme.typography.bodySmall, color = Asd.Smudge, modifier = Modifier.widthIn(max = 640.dp))
}

@Composable
private fun Link(url: String) {
    Text(
        url,
        style = MaterialTheme.typography.bodyMedium,
        color = Asd.Acid,
        modifier = Modifier.clickable { runCatching { ProcessBuilder("xdg-open", url).start() } }.padding(vertical = 4.dp),
    )
}

private const val SITE_URL = "https://asd-eight-rust.vercel.app"
private const val REPO_URL = "https://github.com/max1969php/asd"
