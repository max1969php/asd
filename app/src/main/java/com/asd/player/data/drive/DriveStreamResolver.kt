package com.asd.player.data.drive

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.asd.player.data.settings.SettingsStore
import java.io.IOException

/**
 * Trasforma `gdrive://file/<id>` in una richiesta HTTPS autenticata verso Drive,
 * un attimo prima che ExoPlayer apra lo stream. Gli URI locali passano intatti.
 */
@OptIn(UnstableApi::class)
class DriveStreamResolver(
    private val auth: DriveAuth,
    private val settings: SettingsStore,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val fileId = DriveClient.fileIdOf(dataSpec.uri) ?: return dataSpec
        val email = settings.drive.value.defaultAccount
            ?: throw IOException("Nessun account Google Drive selezionato")
        return dataSpec.buildUpon()
            .setUri(DriveClient.mediaUrl(fileId))
            .setHttpRequestHeaders(dataSpec.httpRequestHeaders + ("Authorization" to "Bearer ${auth.token(email)}"))
            .build()
    }
}
