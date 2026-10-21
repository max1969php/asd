package com.asd.player.data.drive

import android.net.Uri
import com.asd.player.data.scanner.AudioScanner
import com.asd.player.data.scanner.ScannedFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

/** Client minimale per Drive API v3: solo quello che serve ad ASD (leggere la cartella, caricarci i brani dal Tubo). */
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

    /** Carica un file nella cartella [folderId] (upload multipart, va bene per file da pochi MB). */
    fun upload(email: String, folderId: String, name: String, mimeType: String, file: File): DriveFile {
        val boundary = "asd-${System.nanoTime()}"
        val metadata = JSONObject()
            .put("name", name)
            .put("mimeType", mimeType)
            .put("parents", JSONArray().put(folderId))
        val head = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
            "--$boundary\r\nContent-Type: $mimeType\r\n\r\n").toByteArray()
        val tail = "\r\n--$boundary--\r\n".toByteArray()

        for (attempt in 1..2) {
            val connection = (URL("$UPLOAD_API/files?uploadType=multipart&fields=${enc(FILE_FIELDS)}")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setFixedLengthStreamingMode(head.size + file.length() + tail.size)
                setRequestProperty("Authorization", "Bearer ${auth.token(email)}")
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                connectTimeout = 15_000
                readTimeout = 60_000
            }
            try {
                connection.outputStream.use { out ->
                    out.write(head)
                    file.inputStream().use { it.copyTo(out) }
                    out.write(tail)
                }
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_UNAUTHORIZED && attempt == 1) {
                    auth.invalidate(email)
                    continue
                }
                if (code !in 200..299) {
                    val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IOException("Drive ha rifiutato il caricamento ($code) ${body.take(200)}")
                }
                return parseFile(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
            } finally {
                connection.disconnect()
            }
        }
        throw DriveAuthException("Drive non accetta il token di $email")
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
        private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
        private const val FILE_FIELDS = "id,name,mimeType,size,modifiedTime"
        private const val FIELDS = "nextPageToken,files($FILE_FIELDS)"

        /** URI interno salvato nel catalogo: `gdrive://file/<id>`. Il token si aggiunge solo al momento di suonare. */
        fun uriFor(fileId: String) = "$SCHEME://file/$fileId"

        fun fileIdOf(uri: Uri): String? = uri.takeIf { it.scheme == SCHEME }?.lastPathSegment

        fun mediaUrl(fileId: String) = "$API/files/$fileId?alt=media"

        fun parseFileList(json: String): Pair<List<DriveFile>, String?> {
            val root = JSONObject(json)
            val array = root.optJSONArray("files")
            val files = (0 until (array?.length() ?: 0)).map { i -> parseFile(array!!.getJSONObject(i)) }
            return files to root.optString("nextPageToken").takeIf { it.isNotEmpty() }
        }

        fun parseFile(o: JSONObject) = DriveFile(
            id = o.getString("id"),
            name = o.optString("name"),
            mimeType = o.optString("mimeType"),
            size = o.optString("size").toLongOrNull() ?: 0L,
            modifiedAt = o.optString("modifiedTime").takeIf { it.isNotEmpty() }
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
        )

        private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    }
}
