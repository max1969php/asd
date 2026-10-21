package com.asd.player.data.dropbox

import android.net.Uri
import com.asd.player.data.scanner.AudioMetadata
import com.asd.player.data.scanner.MetadataReader
import com.asd.player.data.scanner.ScannedFile
import com.asd.player.data.scanner.SourceScanner
import com.asd.player.domain.model.SongSource

class DropboxSourceScanner(
    private val email: String,
    private val path: String,
    private val client: DropboxClient,
    private val reader: MetadataReader,
) : SourceScanner {
    override val source = SongSource.DROPBOX
    override val metadataParallelism = 6

    override fun listFiles() = client.scanAudio(email, path)

    override fun readMetadata(file: ScannedFile): AudioMetadata {
        val fileId = DropboxClient.fileIdOf(Uri.parse(file.uri)) ?: return AudioMetadata(null, null, null, 0L)
        return reader.readRemote(client.temporaryLink(email, fileId), emptyMap())
    }
}
