package com.asd.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asd.player.ui.components.AnarchyLogo
import com.asd.player.ui.components.PunkButton
import com.asd.player.ui.components.Stamp
import com.asd.player.ui.theme.Asd

@Composable
fun Onboarding(onPickDirectory: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize().padding(24.dp),
    ) {
        AnarchyLogo(size = 120.dp)
        Spacer(Modifier.height(24.dp))
        Text("ASD", style = MaterialTheme.typography.displayLarge)
        Text("ANARCHIC\nSTREAMING\nDEVICE", style = MaterialTheme.typography.headlineLarge, color = Asd.Acid)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stamp("0€/mese", rotation = -6f)
            Spacer(Modifier.width(12.dp))
            Stamp("no account", color = Asd.Paper, rotation = 4f)
            Spacer(Modifier.width(12.dp))
            Stamp("no cloud", color = Asd.Acid, rotation = -2f)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "I tuoi MP3 sono già tuoi. Non devi pagare nessuno per ascoltarli.\n\n" +
                "Scegli UNA cartella del telefono: ASD la setaccia, mette in fila i pezzi e ti lascia fare mixtape. Fine.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        PunkButton("Scegli la cartella", onClick = onPickDirectory, icon = Icons.Filled.Folder)
    }
}
