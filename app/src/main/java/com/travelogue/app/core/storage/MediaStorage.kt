package com.travelogue.app.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class StorageFullException(message: String) : IOException(message)

data class StoredImage(val relativePath: String, val width: Int, val height: Int, val sizeBytes: Long)
data class StoredDocument(val relativePath: String, val fileName: String, val mimeType: String, val sizeBytes: Long)

/**
 * Owns every file the app keeps. All media lives under `filesDir/media/<tripUuid>/<folder>/` and the
 * database only stores the path relative to `filesDir`, so the data survives URI permission expiry and
 * can be zipped/restored as-is.
 */
class MediaStorage(private val context: Context) {

    val root: File get() = context.filesDir
    private val mediaRoot: File get() = File(root, MEDIA_DIR).apply { mkdirs() }

    fun file(relativePath: String): File = File(root, relativePath)

    fun tripDir(tripUuid: String): File = File(mediaRoot, tripUuid)

    /**
     * Copies a picked image into app storage. Large photos are downscaled to [MAX_EDGE] on the long edge
     * and re-encoded as JPEG (EXIF rotation applied on API 28+), which keeps the library fast and small.
     */
    suspend fun importImage(uri: Uri, tripUuid: String, folder: String): StoredImage = withContext(Dispatchers.IO) {
        ensureSpace(estimateSize(uri))
        val dir = File(tripDir(tripUuid), folder).apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.jpg")
        writeOrDelete(target) {
            val bitmap = decodeScaled(uri)
            if (bitmap != null) {
                target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                val result = StoredImage(relative(target), bitmap.width, bitmap.height, target.length())
                bitmap.recycle()
                result
            } else {
                // Unknown format: keep the original bytes.
                copyRaw(uri, target)
                StoredImage(relative(target), 0, 0, target.length())
            }
        }
    }

    /** Copies any document (PDF, image, etc.) byte-for-byte. */
    suspend fun importDocument(uri: Uri, tripUuid: String, folder: String = "documents"): StoredDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = displayName(uri) ?: "document"
        val size = estimateSize(uri)
        if (size > MAX_DOCUMENT_BYTES) throw IOException("File is larger than 50 MB")
        ensureSpace(size)
        // Display names are free text ("E-Ticket 21.11", "Invoice No. 4521"), so whatever follows the
        // last dot is only trusted when it is a real, known extension. Otherwise fall back to the MIME
        // type. A bad extension here would make the trip's own export impossible to import again.
        val mimes = MimeTypeMap.getSingleton()
        val safe = Regex("[a-z0-9]{1,8}")
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.matches(safe) && mimes.hasExtension(it) }
            ?: mimes.getExtensionFromMimeType(mime)?.lowercase()?.takeIf { it.matches(safe) }
            ?: "bin"
        val dir = File(tripDir(tripUuid), folder).apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.$ext")
        writeOrDelete(target) {
            copyRaw(uri, target)
            StoredDocument(relative(target), name, mime, target.length())
        }
    }

    /** A copy cut off part-way (full disk, unreadable source) must not leave a broken file behind. */
    private inline fun <T> writeOrDelete(target: File, write: () -> T): T = try {
        write()
    } catch (e: Throwable) {
        target.delete()
        throw e
    }

    /**
     * Runs [write], the database change that records the just-imported file at [newPath]. If it fails,
     * nothing points at the file, so it is deleted rather than left behind as an orphan.
     *
     * Cancellation is left alone: Room may already have committed the write when it is noticed, and
     * deleting the file then would break a saved record. A rare leftover is the lesser harm.
     */
    suspend inline fun <T> recordOrDiscard(newPath: String?, write: () -> T): T = try {
        write()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        if (newPath != null) withContext(NonCancellable) { delete(newPath) }
        throw e
    }

    /** Writes an already-encoded bitmap (used for generated demo covers). */
    suspend fun saveBitmap(bitmap: Bitmap, tripUuid: String, folder: String): StoredImage = withContext(Dispatchers.IO) {
        val dir = File(tripDir(tripUuid), folder).apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.jpg")
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        StoredImage(relative(target), bitmap.width, bitmap.height, target.length())
    }

    /** A fresh file + content URI for camera capture into app storage. */
    fun newCaptureFile(tripUuid: String, folder: String): File {
        val dir = File(tripDir(tripUuid), folder).apply { mkdirs() }
        return File(dir, "${UUID.randomUUID()}.jpg")
    }

    fun relative(file: File): String = file.relativeTo(root).path

    suspend fun delete(relativePath: String?) = withContext(Dispatchers.IO) {
        if (relativePath.isNullOrBlank()) return@withContext
        val f = file(relativePath)
        if (f.canonicalPath.startsWith(mediaRoot.canonicalPath)) f.delete()
    }

    suspend fun deleteTripMedia(tripUuid: String) = withContext(Dispatchers.IO) {
        tripDir(tripUuid).deleteRecursively()
    }

    suspend fun deleteAllMedia() = withContext(Dispatchers.IO) {
        File(root, MEDIA_DIR).deleteRecursively()
    }

    /** Bytes used by everything under media/, split into images and other files. */
    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        var images = 0L
        var docs = 0L
        File(root, MEDIA_DIR).walkTopDown().filter { it.isFile }.forEach {
            if (it.extension.lowercase() in IMAGE_EXT) images += it.length() else docs += it.length()
        }
        val db = context.getDatabasePath("travelogue.db")
        val dbBytes = listOf(db, File(db.path + "-wal"), File(db.path + "-shm")).sumOf { if (it.exists()) it.length() else 0L }
        StorageUsage(images, docs, dbBytes, root.usableSpace)
    }

    fun ensureSpace(neededBytes: Long) {
        val free = root.usableSpace
        if (free < neededBytes + MIN_FREE_BYTES) {
            throw StorageFullException("Not enough free storage on this device. Free up some space and try again.")
        }
    }

    /** The size the provider reports for [uri], or 0 when it does not say. */
    fun sizeOf(uri: Uri): Long = estimateSize(uri)

    /** Whether [uri] can still be opened; a picker's grant may not outlive the process that got it. */
    fun canRead(uri: Uri): Boolean =
        runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)

    private fun estimateSize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun copyRaw(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the selected file")
        input.use { i -> target.outputStream().use { o -> i.copyTo(o) } }
    }

    private fun decodeScaled(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val longEdge = maxOf(w, h)
                if (longEdge > MAX_EDGE) {
                    val scale = MAX_EDGE.toFloat() / longEdge
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    }.getOrNull()

    companion object {
        const val MEDIA_DIR = "media"
        const val MAX_EDGE = 2560
        const val JPEG_QUALITY = 88
        const val MIN_FREE_BYTES = 30L * 1024 * 1024
        const val MAX_DOCUMENT_BYTES = 50L * 1024 * 1024
        val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif")
    }
}

data class StorageUsage(val imageBytes: Long, val documentBytes: Long, val databaseBytes: Long, val freeBytes: Long) {
    val totalBytes: Long get() = imageBytes + documentBytes + databaseBytes
}
