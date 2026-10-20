package com.asd.player.data.drive

import android.net.Uri
import com.asd.player.data.scanner.AudioScanner
import com.asd.player.data.scanner.ScannedFile
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val modifiedAt: Long,
) {
    val isFolder: Boolean get() = mimeType == FOLDER_MIME

    companion object {
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
    }
}

/** Client minimale per Drive API v3: solo lettura, solo quello che serve ad ASD. */
class DriveClient(private val auth: DriveAuth) {

    fun listFolders(email: String, parentId: String): List<DriveFile> =
        listChildren(email, parentId, foldersOnly = true)

    /** Visita ricorsiva della cartella: restituisce solo i file audio della whitelist. */
    fun scanAudio(email: String, folderId: String): List<ScannedFile> {
        val result = mutableListOf<ScannedFile>()
        val pending = ArrayDeque(listOf(folderId))
        while (pending.isNotEmpty()) {
            listChildren(email, pending.removeFirst(), foldersOnly = false).forEach { file ->
                when {
                    file.isFolder -> pending.add(file.id)
                    AudioScanner.isSupported(file.name) -> result += ScannedFile(
                        uri = uriFor(file.id),
                        fileName = file.name,
                        mimeType = file.mimeType,
                        size = file.size,
                        lastModified = file.modifiedAt,
                    )
                }
            }
        }
        return result
    }

    private fun listChildren(email: String, parentId: String, foldersOnly: Boolean): List<DriveFile> {
        val files = mutableListOf<DriveFile>()
        var pageToken: String? = null
        val query = buildString {
            append("'").append(parentId.replace("'", "\\'")).append("' in parents and trashed = false")
            if (foldersOnly) append(" and mimeType = '").append(DriveFile.FOLDER_MIME).append("'")
        }
        do {
            val url = "$API/files?q=${enc(query)}&fields=${enc(FIELDS)}&pageSize=1000&orderBy=${enc("folder,name")}" +
                (pageToken?.let { "&pageToken=${enc(it)}" } ?: "")
            val page = parseFileList(get(email, url))
            files += page.first
            pageToken = page.second
        } while (pageToken != null)
        return files
    }

    private fun get(email: String, url: String, retry: Boolean = true): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer ${auth.token(email)}")
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED && retry) {
                auth.invalidate(email)
                return get(email, url, retry = false)
            }
            if (code !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Drive ha risposto $code ${body.take(200)}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val SCHEME = "gdrive"
        const val ROOT = "root"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val FIELDS = "nextPageToken,files(id,name,mimeType,size,modifiedTime)"

        /** URI interno salvato nel catalogo: `gdrive://file/<id>`. Il token si aggiunge solo al momento di suonare. */
        fun uriFor(fileId: String) = "$SCHEME://file/$fileId"

        fun fileIdOf(uri: Uri): String? = uri.takeIf { it.scheme == SCHEME }?.lastPathSegment

        fun mediaUrl(fileId: String) = "$API/files/$fileId?alt=media"

        fun parseFileList(json: String): Pair<List<DriveFile>, String?> {
            val root = JSONObject(json)
            val array = root.optJSONArray("files")
            val files = (0 until (array?.length() ?: 0)).map { i ->
                val o = array!!.getJSONObject(i)
                DriveFile(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    mimeType = o.optString("mimeType"),
                    size = o.optString("size").toLongOrNull() ?: 0L,
                    modifiedAt = o.optString("modifiedTime").takeIf { it.isNotEmpty() }
                        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
                )
            }
            return files to root.optString("nextPageToken").takeIf { it.isNotEmpty() }
        }

        private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    }
}
