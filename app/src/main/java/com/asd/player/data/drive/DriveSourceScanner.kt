package com.asd.player.data.drive

import android.net.Uri
import com.asd.player.data.scanner.AudioMetadata
import com.asd.player.data.scanner.MetadataReader
import com.asd.player.data.scanner.ScannedFile
import com.asd.player.data.scanner.SourceScanner
import com.asd.player.domain.model.SongSource

class DriveSourceScanner(
    private val email: String,
    private val folderId: String,
    private val client: DriveClient,
    private val auth: DriveAuth,
    private val reader: MetadataReader,
) : SourceScanner {
    override val source = SongSource.DRIVE
    override val metadataParallelism = 6

    override fun listFiles() = client.scanAudio(email, folderId)

    override fun readMetadata(file: ScannedFile): AudioMetadata {
        val fileId = DriveClient.fileIdOf(Uri.parse(file.uri)) ?: return AudioMetadata(null, null, null, 0L)
        return reader.readRemote(
            DriveClient.mediaUrl(fileId),
            mapOf("Authorization" to "Bearer ${auth.token(email)}"),
        )
    }
}
