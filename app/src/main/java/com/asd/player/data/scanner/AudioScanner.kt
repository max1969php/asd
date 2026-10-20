package com.asd.player.data.scanner

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/** File trovato nella cartella, prima della lettura dei metadati. */
data class ScannedFile(
    val uri: String,
    val fileName: String,
    val mimeType: String?,
    val size: Long,
    val lastModified: Long,
)

/**
 * Visita ricorsivamente l'albero SAF scelto dall'utente interrogando direttamente
 * DocumentsContract (molto più veloce di DocumentFile su cartelle grandi).
 */
class AudioScanner(private val resolver: ContentResolver) {

    fun scan(treeUri: Uri): List<ScannedFile> {
        val result = mutableListOf<ScannedFile>()
        val pending = ArrayDeque<String>()
        pending.add(DocumentsContract.getTreeDocumentId(treeUri))

        while (pending.isNotEmpty()) {
            val parentId = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            resolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2)
                    if (mime == Document.MIME_TYPE_DIR) {
                        pending.add(docId)
                    } else if (isSupported(name)) {
                        result += ScannedFile(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString(),
                            fileName = name,
                            mimeType = mime,
                            size = if (cursor.isNull(3)) 0 else cursor.getLong(3),
                            lastModified = if (cursor.isNull(4)) 0 else cursor.getLong(4),
                        )
                    }
                }
            }
        }
        return result
    }

    companion object {
        /** Whitelist esplicita: mostriamo solo ciò che dichiariamo di supportare. */
        val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "flac", "m4a", "aac", "ogg", "opus")

        fun isSupported(fileName: String): Boolean =
            !fileName.startsWith(".") &&
                fileName.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS

        private val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
