package com.asd.player.data.scanner

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.asd.player.L10n
import com.asd.player.R
import java.io.File
import java.io.IOException
import java.io.OutputStream

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

    /** Copia [file] nella radice della cartella scelta, con il nome [displayName]. */
    fun write(treeUri: Uri, displayName: String, mimeType: String, file: File): ScannedFile =
        create(treeUri, emptyList(), displayName, mimeType) { out -> file.inputStream().use { it.copyTo(out) } }

    /**
     * Crea un file nella cartella scelta, dentro le sottocartelle [dirs] (create se mancano),
     * e lo riempie con [writeTo]. Serve il permesso di scrittura sull'albero SAF.
     */
    fun create(
        treeUri: Uri,
        dirs: List<String>,
        displayName: String,
        mimeType: String,
        writeTo: (OutputStream) -> Unit,
    ): ScannedFile {
        var parentId = DocumentsContract.getTreeDocumentId(treeUri)
        dirs.forEach { parentId = ensureDir(treeUri, parentId, it) }
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
        val created = DocumentsContract.createDocument(resolver, parent, mimeType, displayName)
            ?: throw IOException(L10n.str(R.string.cannot_create, displayName))
        try {
            resolver.openOutputStream(created)?.use(writeTo)
                ?: throw IOException(L10n.str(R.string.cannot_write, displayName))
        } catch (e: Exception) {
            // Unica eccezione alla regola "l'app non cancella file": il file vuoto o a metà
            // appena creato qui sopra da un download fallito, mai un file dell'utente.
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            throw e
        }
        // Rileggiamo nome, dimensione e data veri: la prossima riscansione li confronta con questi.
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getDocumentId(created))
        resolver.query(uri, PROJECTION, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                return ScannedFile(
                    uri = uri.toString(),
                    fileName = c.getString(1) ?: displayName,
                    mimeType = c.getString(2) ?: mimeType,
                    size = c.getLong(3),
                    lastModified = c.getLong(4),
                )
            }
        }
        return ScannedFile(uri.toString(), displayName, mimeType, 0L, System.currentTimeMillis())
    }

    /** Id della sottocartella [name] di [parentId]; se non c'è la crea. */
    private fun ensureDir(treeUri: Uri, parentId: String, name: String): String {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        resolver.query(children, PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(2) == Document.MIME_TYPE_DIR && c.getString(1) == name) return c.getString(0)
            }
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
        val created = DocumentsContract.createDocument(resolver, parent, Document.MIME_TYPE_DIR, name)
            ?: throw IOException(L10n.str(R.string.cannot_create_dir, name))
        return DocumentsContract.getDocumentId(created)
    }

    /** Le cartelle scelte prima del TUBO hanno solo il permesso di lettura. */
    fun canWrite(treeUri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == treeUri && it.isWritePermission }

    companion object {
        /** Whitelist esplicita: mostriamo solo ciò che dichiariamo di supportare. */
        private val MIME_TYPES = linkedMapOf(
            "mp3" to "audio/mpeg",
            "wav" to "audio/wav",
            "flac" to "audio/flac",
            "m4a" to "audio/mp4",
            "aac" to "audio/aac",
            "ogg" to "audio/ogg",
            "opus" to "audio/opus",
        )
        val SUPPORTED_EXTENSIONS: Set<String> = MIME_TYPES.keys

        /** "primary:Music/Punk" → "Music/Punk", più leggibile di un content:// URI. */
        fun readablePath(treeUri: Uri): String =
            runCatching { DocumentsContract.getTreeDocumentId(treeUri).substringAfter(':').ifEmpty { "/" } }
                .getOrDefault(treeUri.toString())

        /** Tipo MIME dall'estensione, per le fonti che non lo dicono. */
        fun mimeTypeOf(fileName: String): String? = MIME_TYPES[fileName.substringAfterLast('.', "").lowercase()]

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
