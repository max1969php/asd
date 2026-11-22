package com.asd.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asd.desktop.Desktop
import com.asd.desktop.Texts
import com.asd.player.ui.components.AnarchyLogo
import com.asd.player.ui.theme.Asd

private enum class Tab(val label: String) { LIBRARY(Texts.TAB_LIBRARY), SETTINGS(Texts.TAB_SETTINGS) }

/** La finestra: schede in alto, contenuto, player sempre visibile in basso. */
@Composable
fun App(desktop: Desktop) {
    var tab by rememberSaveable { mutableStateOf(Tab.LIBRARY) }
    Column(Modifier.fillMaxSize().background(Asd.Toner)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(Asd.Toner).padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            AnarchyLogo(size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Text("ASD", style = MaterialTheme.typography.headlineSmall, color = Asd.Paper)
            Spacer(Modifier.width(28.dp))
            Tab.entries.forEach { t ->
                val selected = t == tab
                Text(
                    t.label.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) Asd.Toner else Asd.Paper,
                    modifier = Modifier
                        .background(if (selected) Asd.Acid else Asd.Toner)
                        .clickable { tab = t }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        HorizontalDivider(color = Asd.Concrete, thickness = 2.dp)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.LIBRARY -> LibraryScreen(desktop, onOpenSettings = { tab = Tab.SETTINGS })
                Tab.SETTINGS -> SettingsScreen(desktop)
            }
        }
        HorizontalDivider(color = Asd.Spray, thickness = 2.dp)
        PlayerBar(desktop.player)
        Spacer(Modifier.height(2.dp))
    }
}
