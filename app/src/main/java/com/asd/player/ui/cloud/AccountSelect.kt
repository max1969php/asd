package com.asd.player.ui.cloud

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asd.player.ui.theme.Asd

/**
 * La "select" dell'account predefinito di una fonte cloud. Elenca gli account già collegati;
 * l'ultima voce ne collega un altro (selettore di sistema di Android).
 */
@Composable
fun AccountSelect(
    accounts: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onAddAccount: () -> Unit,
    addLabel: String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, Asd.Paper)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text(
                selected ?: "nessun account",
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected != null) Asd.Acid else Asd.Smudge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("▼", style = MaterialTheme.typography.labelLarge, color = Asd.Paper)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Asd.Asphalt) {
            accounts.forEach { email ->
                DropdownMenuItem(
                    text = {
                        Text(
                            (if (email == selected) "● " else "○ ") + email,
                            color = if (email == selected) Asd.Acid else Asd.Paper,
                        )
                    },
                    onClick = { open = false; onSelect(email) },
                )
            }
            if (accounts.isNotEmpty()) HorizontalDivider(color = Asd.Concrete)
            DropdownMenuItem(
                text = { Text(addLabel, color = Asd.Spray) },
                onClick = { open = false; onAddAccount() },
            )
        }
    }
}
