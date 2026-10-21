package com.asd.player.ui.tube

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.asd.player.data.tube.TubeCookies
import com.asd.player.ui.theme.Asd

/**
 * Pagina di accesso di Google/YouTube a tutto schermo. Quando l'utente preme FATTO,
 * i cookie della WebView diventano il cookies.txt di yt-dlp.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubeLogin(onDone: () -> Unit) {
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Asd.Toner).statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(Asd.Acid).padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    "Accedi, poi premi FATTO",
                    style = MaterialTheme.typography.labelMedium,
                    color = Asd.Toner,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "[FATTO]",
                    style = MaterialTheme.typography.labelLarge,
                    color = Asd.Toner,
                    modifier = Modifier.clickable(onClick = onDone).padding(6.dp),
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Google rifiuta l'accesso dalle WebView che si dichiarano tali ("; wv"):
                        // ci presentiamo come il Chrome del telefono.
                        settings.userAgentString = settings.userAgentString
                            .replace("; wv", "")
                            .replace(Regex("""Version/[\d.]+ """), "")
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        loadUrl(TubeCookies.LOGIN_URL)
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
