package com.asd.player.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.asd.player.data.drive.DriveAuth
import com.asd.player.data.drive.DriveClient
import com.asd.player.data.settings.SettingsStore
import java.io.IOException

/**
 * Trasforma gli URI interni dei brani cloud in richieste HTTPS vere, un attimo prima che
 * ExoPlayer apra lo stream: `gdrive://` diventa HTTPS con il token OAuth.
 * Gli URI locali passano intatti.
 */
@OptIn(UnstableApi::class)
class CloudStreamResolver(
    private val driveAuth: DriveAuth,
    private val settings: SettingsStore,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        DriveClient.fileIdOf(dataSpec.uri)?.let { fileId ->
            val email = settings.drive.state.value.defaultAccount
                ?: throw IOException("Nessun account Google Drive selezionato")
            return dataSpec.buildUpon()
                .setUri(DriveClient.mediaUrl(fileId))
                .setHttpRequestHeaders(dataSpec.httpRequestHeaders + ("Authorization" to "Bearer ${driveAuth.token(email)}"))
                .build()
        }
        return dataSpec
    }
}
