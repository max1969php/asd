package com.asd.player.data.scanner

import android.net.Uri
import com.asd.player.domain.model.SongSource

/** Una fonte di brani: sa elencare i file e leggerne i tag. Il repository fa il resto. */
interface SourceScanner {
    val source: SongSource

    /** Quanti file leggere in parallelo: in rete conviene sovrapporre le attese. */
    val metadataParallelism: Int

    fun listFiles(): List<ScannedFile>
    fun readMetadata(file: ScannedFile): AudioMetadata
}

class LocalSourceScanner(
    private val treeUri: Uri,
    private val scanner: AudioScanner,
    private val reader: MetadataReader,
) : SourceScanner {
    override val source = SongSource.LOCAL
    override val metadataParallelism = 2
    override fun listFiles() = scanner.scan(treeUri)
    override fun readMetadata(file: ScannedFile) = reader.read(Uri.parse(file.uri))
}
