package com.travelogue.app.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Google Drive v3 REST client for backup ZIPs. Uses the `drive.file` scope, so the app only
 * ever sees files it created itself. Backups live in "App Backups/Travelogue Backups" in My Drive.
 */
class DriveClient(private val accessToken: String) {

    @Serializable
    data class DriveFile(val id: String, val name: String, val size: Long = 0, val createdTime: String = "")

    @Serializable
    private data class FileList(val files: List<DriveFile> = emptyList())

    @Serializable
    private data class Created(val id: String)

    private val json = Json { ignoreUnknownKeys = true }

    /** Uploads [zip] into the backup folder, reporting progress as 0..1. Returns the new file. */
    suspend fun upload(zip: File, name: String, progress: (Float) -> Unit): DriveFile = withContext(Dispatchers.IO) {
        val folderId = folderId(create = true)!!
        val meta = """{"name":${Json.encodeToString(String.serializer(), name)},"parents":["$folderId"],"mimeType":"application/zip"}"""
        // Resumable upload: one request for the session URL, one streaming PUT for the bytes.
        val session = open("$UPLOAD/files?uploadType=resumable&fields=id,name,size,createdTime", "POST").apply {
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-Upload-Content-Type", "application/zip")
            setRequestProperty("X-Upload-Content-Length", zip.length().toString())
            doOutput = true
            outputStream.use { it.write(meta.toByteArray()) }
        }
        check(session)
        val location = session.getHeaderField("Location") ?: throw IOException("Drive did not return an upload URL.")
        session.disconnect()

        val put = (URL(location).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Content-Type", "application/zip")
            setFixedLengthStreamingMode(zip.length())
            doOutput = true
            connectTimeout = TIMEOUT; readTimeout = TIMEOUT
        }
        val total = zip.length().coerceAtLeast(1)
        var sent = 0L
        put.outputStream.use { out ->
            zip.inputStream().use { input ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    sent += n
                    progress(sent.toFloat() / total)
                }
            }
        }
        json.decodeFromString(DriveFile.serializer(), body(put))
    }

    /** Backups in the folder, newest first. */
    suspend fun list(): List<DriveFile> = withContext(Dispatchers.IO) {
        val folderId = folderId(create = false) ?: return@withContext emptyList()
        val q = enc("'$folderId' in parents and trashed=false and mimeType='application/zip'")
        val conn = open("$API/files?q=$q&orderBy=createdTime%20desc&pageSize=100&fields=files(id,name,size,createdTime)&spaces=drive", "GET")
        json.decodeFromString(FileList.serializer(), body(conn)).files
    }

    /** Downloads a backup to [target], reporting progress as 0..1. */
    suspend fun download(file: DriveFile, target: File, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val conn = open("$API/files/${file.id}?alt=media", "GET")
        check(conn)
        val total = file.size.coerceAtLeast(1)
        var read = 0L
        conn.inputStream.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    read += n
                    progress((read.toFloat() / total).coerceAtMost(1f))
                }
            }
        }
        conn.disconnect()
    }

    /** Resolves (and optionally creates) "App Backups/Travelogue Backups", returning the inner folder's id. */
    private fun folderId(create: Boolean): String? {
        // The parent may sit anywhere in My Drive (the user can move it); the child must be inside it.
        val parent = findFolder(PARENT_FOLDER, inside = null) ?: if (create) createFolder(PARENT_FOLDER, inside = null) else return null
        return findFolder(FOLDER_NAME, inside = parent) ?: if (create) createFolder(FOLDER_NAME, inside = parent) else null
    }

    private fun findFolder(name: String, inside: String?): String? {
        val where = "mimeType='$FOLDER_MIME' and name='$name' and trashed=false" + (inside?.let { " and '$it' in parents" } ?: "")
        val conn = open("$API/files?q=${enc(where)}&orderBy=createdTime&fields=files(id,name)&spaces=drive", "GET")
        return json.decodeFromString(FileList.serializer(), body(conn)).files.firstOrNull()?.id
    }

    private fun createFolder(name: String, inside: String?): String {
        val parents = inside?.let { ""","parents":["$it"]""" }.orEmpty()
        val conn = open("$API/files?fields=id", "POST").apply {
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            doOutput = true
            outputStream.use { it.write("""{"name":"$name","mimeType":"$FOLDER_MIME"$parents}""".toByteArray()) }
        }
        return json.decodeFromString(Created.serializer(), body(conn)).id
    }

    private fun open(url: String, method: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        setRequestProperty("Authorization", "Bearer $accessToken")
        connectTimeout = TIMEOUT; readTimeout = TIMEOUT
    }

    private fun check(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code in 200..299) return
        val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        throw BackupException(
            when (code) {
                401 -> "Google sign-in expired. Please try again."
                403 -> "Google Drive refused access. ${err.take(200)}"
                404 -> "That backup no longer exists on Google Drive."
                else -> "Google Drive error $code. ${err.take(200)}"
            },
        )
    }

    private fun body(conn: HttpURLConnection): String {
        check(conn)
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        /** Shared top-level folder for all of the user's app backups. */
        private const val PARENT_FOLDER = "App Backups"
        private const val FOLDER_NAME = "Travelogue Backups"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val TIMEOUT = 60_000
    }
}
