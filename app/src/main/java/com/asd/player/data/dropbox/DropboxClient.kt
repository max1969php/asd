package com.asd.player.data.dropbox

import android.net.Uri
import com.asd.player.data.scanner.AudioScanner
import com.asd.player.data.scanner.ScannedFile
import com.asd.player.data.settings.CloudFolder
import com.dropbox.core.DbxException
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.ListFolderResult
import com.dropbox.core.v2.files.WriteMode
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Quello che serve ad ASD da Dropbox: cartelle, file audio, link per lo streaming, caricamento. Tutto bloccante. */
class DropboxClient(private val auth: DropboxAuth) {

    /** I link temporanei durano 4 ore: li riusiamo per 3. */
    private val links = ConcurrentHashMap<String, Pair<String, Long>>()

    fun listFolders(email: String, path: String): List<CloudFolder> =
        listAll(email, path, recursive = false)
            .filterIsInstance<FolderMetadata>()
            .map { CloudFolder(it.pathLower ?: it.pathDisplay ?: "/${it.name}", it.name) }
            .sortedBy { it.name.lowercase() }

    /** Visita ricorsiva della cartella: solo i file audio della whitelist. */
    fun scanAudio(email: String, path: String): List<ScannedFile> =
        listAll(email, path, recursive = true)
            .filterIsInstance<FileMetadata>()
            .filter { AudioScanner.isSupported(it.name) }
            .map { it.toScannedFile() }

    /** Un URL HTTPS diretto al file, apribile da ExoPlayer e dal lettore di tag senza header. */
    fun temporaryLink(email: String, fileId: String): String {
        links[fileId]?.let { (link, expiresAt) -> if (System.currentTimeMillis() < expiresAt) return link }
        val link = call(email) { auth.client(email).files().getTemporaryLink(fileId).link }
        links[fileId] = link to System.currentTimeMillis() + TimeUnit.HOURS.toMillis(3)
        return link
    }

    /** Carica [file] in [folderPath]; se il nome esiste già Dropbox aggiunge " (1)". */
    fun upload(email: String, folderPath: String, name: String, file: File): ScannedFile = call(email) {
        file.inputStream().use { input ->
            auth.client(email).files().uploadBuilder("$folderPath/$name")
                .withMode(WriteMode.ADD)
                .withAutorename(true)
                .uploadAndFinish(input)
        }.toScannedFile()
    }

    private fun listAll(email: String, path: String, recursive: Boolean) = call(email) {
        val files = auth.client(email).files()
        val entries = mutableListOf<com.dropbox.core.v2.files.Metadata>()
        var page: ListFolderResult = files.listFolderBuilder(path).withRecursive(recursive).start()
        entries += page.entries
        while (page.hasMore) {
            page = files.listFolderContinue(page.cursor)
            entries += page.entries
        }
        entries
    }

    private fun <T> call(email: String, block: () -> T): T = try {
        block()
    } catch (e: InvalidAccessTokenException) {
        auth.forget(email)
        throw DropboxAuthException("Dropbox ha revocato l'accesso di $email: ricollegalo in SETUP → Dropbox")
    } catch (e: DbxException) {
        throw IOException("Dropbox: ${e.message}")
    }

    private fun FileMetadata.toScannedFile() = ScannedFile(
        uri = uriFor(id),
        fileName = name,
        mimeType = AudioScanner.mimeTypeOf(name),
        size = size,
        lastModified = serverModified.time,
    )

    companion object {
        const val SCHEME = "dropbox"
        const val ROOT = ""

        /** URI interno salvato nel catalogo: `dropbox://file/<id senza "id:">`. */
        fun uriFor(fileId: String) = "$SCHEME://file/${fileId.removePrefix("id:")}"

        fun fileIdOf(uri: Uri): String? = uri.takeIf { it.scheme == SCHEME }?.lastPathSegment?.let { "id:$it" }
    }
}
