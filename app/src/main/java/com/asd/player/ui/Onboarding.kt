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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asd.player.R
import com.asd.player.ui.components.AnarchyLogo
import com.asd.player.ui.components.PunkButton
import com.asd.player.ui.components.Stamp
import com.asd.player.ui.theme.Asd

@Composable
fun Onboarding(
    onPickDirectory: () -> Unit,
    onConnectDrive: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Stamp(stringResource(R.string.onboarding_stamp_free), rotation = -6f)
            Spacer(Modifier.width(12.dp))
            Stamp(stringResource(R.string.onboarding_stamp_no_account), color = Asd.Paper, rotation = 4f)
            Spacer(Modifier.width(12.dp))
            Stamp(stringResource(R.string.onboarding_stamp_no_cloud), color = Asd.Acid, rotation = -2f)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_intro),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        PunkButton(stringResource(R.string.phone_folder), onClick = onPickDirectory, icon = Icons.Filled.Folder)
        Spacer(Modifier.height(12.dp))
        PunkButton(
            "Google Drive",
            onClick = onConnectDrive,
            icon = Icons.Filled.Cloud,
            container = Asd.Paper,
            shadow = Asd.Acid,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_other_source), style = MaterialTheme.typography.bodySmall, color = Asd.Smudge)
    }
}
