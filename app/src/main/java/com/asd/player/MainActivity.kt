package com.asd.player

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.asd.player.domain.model.Song
import com.asd.player.ui.AsdApp
import com.asd.player.ui.theme.AsdTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(L10n.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Song.unknownArtist = getString(R.string.unknown_artist)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        setContent {
            AsdTheme { AsdApp() }
        }
        if (savedInstanceState == null) playFromSearch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        playFromSearch(intent)
    }

    /** "Ok Google, metti … con ASD": la ricerca arriva qui e passa al servizio. */
    private fun playFromSearch(intent: Intent?) {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        (application as MusicApplication).container.playbackController
            .playFromSearch(intent.getStringExtra(SearchManager.QUERY).orEmpty(), intent.extras)
    }
}
