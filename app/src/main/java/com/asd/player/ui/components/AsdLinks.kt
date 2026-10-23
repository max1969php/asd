package com.asd.player.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.asd.player.L10n
import com.asd.player.ui.theme.Asd

private const val SITE_URL = "https://asd-eight-rust.vercel.app"
private const val GITHUB_URL = "https://github.com/max1969php/asd"

/** Sito (istruzioni, yt-dlp, APK nuovi) e sorgenti. Gli indirizzi non si traducono. */
@Composable
fun AsdLinks(modifier: Modifier = Modifier) {
    // Il sito si apre nella lingua dell'app; senza scelta decide il browser.
    val lang = L10n.current()
    Column(modifier) {
        Link(SITE_URL) { if (lang.isEmpty()) SITE_URL else "$SITE_URL/?lang=$lang" }
        Link(GITHUB_URL) { GITHUB_URL }
    }
}

@Composable
private fun Link(url: String, target: () -> String) {
    val context = LocalContext.current
    Text(
        "↗ " + url.removePrefix("https://"),
        style = MaterialTheme.typography.bodyMedium,
        color = Asd.Acid,
        modifier = Modifier
            .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target()))) } }
            .padding(vertical = 6.dp),
    )
}
