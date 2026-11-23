package com.travelogue.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.travelogue.app.BuildConfig
import com.travelogue.app.core.designsystem.theme.AppTheme
import com.travelogue.app.core.designsystem.theme.ThemeMode
import com.travelogue.app.core.storage.MediaStorage
import com.travelogue.app.core.storage.StorageFullException
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.AppDatabase
import com.travelogue.app.data.db.entity.*
import com.travelogue.app.data.db.enumOr
import com.travelogue.app.data.repository.ExpenseRepository
import com.travelogue.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Exports trips (or everything) to ZIP archives and restores them.
 *
 * Single trip archive:            Full backup:
 *   manifest.json                   manifest.json
 *   trip.json                       settings.json
 *   images/cover|hotels|places|     trips/<uuid>/trip.json
 *          diary|receipts|...       trips/<uuid>/images/...
 *   documents/                      trips/<uuid>/documents/...
 *
 * settings.json also carries what belongs to no trip: travellers, categories, exchange rates and the
 * holiday planner's days off.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val media: MediaStorage,
    private val expenses: ExpenseRepository,
    private val settings: SettingsRepository,
) {

    // =========================================================================================
    // Export
    // =========================================================================================

    suspend fun exportTrip(tripId: Long, target: Uri, progress: Progress = { _, _ -> }): ExportResult = withContext(Dispatchers.IO) {
        // One transaction: every row comes from the same moment, even if the user is editing meanwhile.
        val bundle = db.withTransaction { collect(tripId) } ?: throw BackupException("This trip no longer exists.")
        writeArchive(target, BackupType.TRIP, listOf(bundle), settingsArchive = null, progress)
    }

    suspend fun exportAll(target: Uri, progress: Progress = { _, _ -> }): ExportResult = withContext(Dispatchers.IO) {
        // Every database read happens in one transaction, so the backup is a single consistent snapshot
        // even if the user edits or deletes something while it runs. It is only reads, so it is quick.
        class Snapshot(
            val bundles: List<Bundle>,
            val holidays: List<HolidayEntity>,
            val travellers: List<TravellerEntity>,
            val categories: List<ExpenseCategoryEntity>,
            val counts: ContentCounts,
        )
        val snap = db.withTransaction {
            Snapshot(
                bundles = db.tripDao().getAll().mapNotNull { collect(it.id) },
                holidays = db.holidayDao().getAll(),
                travellers = db.travellerDao().getAll(),
                categories = db.expenseDao().getCategories(),
                counts = db.backupStatsDao().counts(),
            )
        }
        val bundles = snap.bundles
        val holidays = snap.holidays
        val s = settings.current()
        val holidayPrefs = settings.holidayPrefs.first()
        val settingsArchive = SettingsArchive(
            defaultCurrency = s.defaultCurrency,
            themeMode = s.themeMode.name,
            appTheme = s.theme.name,
            travellers = snap.travellers,
            categories = snap.categories,
            exchangeRates = s.exchangeRates.map { SettingsRepository.encodeRates(listOf(it)) },
            holidayPrefs = SettingsRepository.archive(holidayPrefs),
        ).withHolidays(holidays)
        writeArchive(target, BackupType.FULL, bundles, settingsArchive, progress).copy(holidays = holidays.size, contents = snap.counts)
    }

    /** Live totals of everything a full backup would contain. */
    fun observeContents(): Flow<ContentCounts> = db.backupStatsDao().observeCounts()

    /** A trip archive plus the on-disk files it references (archive path -> file). */
    private class Bundle(val archive: TripArchive, val files: Map<String, File>)

    private suspend fun collect(tripId: Long): Bundle? {
        val tripDao = db.tripDao()
        val trip = tripDao.get(tripId) ?: return null
        val files = LinkedHashMap<String, File>()
        fun map(rel: String?): String? {
            if (rel.isNullOrBlank()) return null
            val path = archivePath(rel, trip.uuid)
            files[path] = media.file(rel)
            return path
        }
        val travellers = tripDao.getTravellers(tripId)
        val expenseRows = db.expenseDao().getForTrip(tripId)
        val usedCategoryIds = expenseRows.mapNotNull { it.categoryId }.toSet()
        val diary = db.diaryDao().getForTrip(tripId)
        val tagRows = diary.flatMap { e -> db.diaryDao().getTagsFor(e.id) }
        // Settlements/splits/paid-by may reference travellers removed from the trip later; include them too.
        val splits = db.expenseDao().getSplitsForTrip(tripId)
        val settlements = db.expenseDao().getSettlements(tripId)
        val extraTravellerIds = (splits.map { it.travellerId } + settlements.flatMap { listOf(it.fromTravellerId, it.toTravellerId) } +
            expenseRows.mapNotNull { it.paidByTravellerId }).toSet() - travellers.map { it.id }.toSet()
        val allTravellers = travellers + db.travellerDao().getAll().filter { it.id in extraTravellerIds }

        val archive = TripArchive(
            trip = trip.copy(coverPath = map(trip.coverPath)),
            travellers = allTravellers,
            tripTravellers = travellers.mapIndexed { i, t -> TripTravellerEntity(tripId, t.id, i) },
            categories = db.expenseDao().getCategories().filter { it.id in usedCategoryIds },
            rates = tripDao.getRates(tripId),
            expenses = expenseRows.map { it.copy(receiptPath = map(it.receiptPath)) },
            splits = splits,
            settlements = settlements,
            hotels = db.hotelDao().getForTrip(tripId),
            days = db.itineraryDao().getDays(tripId),
            activities = db.itineraryDao().getActivities(tripId).map { it.copy(photoPath = map(it.photoPath)) },
            places = db.placeDao().getForTrip(tripId),
            bookings = db.bookingDao().getForTrip(tripId),
            contacts = db.contactDao().getForTrip(tripId),
            diary = diary,
            tags = tagRows.map { TagEntity(it.tagId, it.name) }.distinctBy { it.id },
            diaryTags = tagRows.map { DiaryEntryTagEntity(it.entryId, it.tagId) },
            photos = db.photoDao().getForTrip(tripId).map { it.copy(relativePath = map(it.relativePath)!!) },
            documents = tripDao.getDocuments(tripId).map { it.copy(relativePath = map(it.relativePath)!!) },
        )
        return Bundle(archive, files)
    }

    private fun writeArchive(
        target: Uri,
        type: String,
        bundles: List<Bundle>,
        settingsArchive: SettingsArchive?,
        progress: Progress,
    ): ExportResult {
        val prefixOf = { b: Bundle -> if (type == BackupType.FULL) "trips/${b.archive.trip.uuid}/" else "" }
        val existing = bundles.flatMap { b -> b.files.filter { it.value.isFile }.map { (p, f) -> prefixOf(b) + p to f } }
        var missing = bundles.sumOf { b -> b.files.count { !it.value.isFile } }
        val added = mutableListOf<ManifestFile>()
        val totalBytes = existing.sumOf { it.second.length() }.coerceAtLeast(1)
        var written = 0L
        val out: OutputStream = context.contentResolver.openOutputStream(target, "wt")
            ?: throw BackupException("Could not open the chosen location for writing.")
        try {
            ZipOutputStream(out.buffered()).use { zip ->
                progress(0f, "Writing your trips and settings")
                zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                settingsArchive?.let { zip.putText("settings.json", BackupJson.encodeToString(SettingsArchive.serializer(), it)) }
                bundles.forEach { b -> zip.putText(prefixOf(b) + "trip.json", BackupJson.encodeToString(TripArchive.serializer(), b.archive)) }
                // Photos are already JPEG-compressed; storing them avoids wasting CPU.
                zip.setLevel(Deflater.NO_COMPRESSION)
                existing.forEachIndexed { i, (path, file) ->
                    // Copying can take a while. A file deleted since the snapshot (a cover replaced, a
                    // photo removed) is counted as missing, the same as one that was already gone,
                    // rather than failing the whole backup. Import already tolerates missing files.
                    val input = try {
                        file.inputStream()
                    } catch (e: FileNotFoundException) {
                        missing++
                        return@forEachIndexed
                    }
                    input.use {
                        zip.putNextEntry(ZipEntry(path))
                        val n = it.copyTo(zip)
                        zip.closeEntry()
                        written += n
                        added += ManifestFile(path, n)
                    }
                    progress(
                        written.toFloat() / totalBytes,
                        "Adding photos and documents: ${i + 1} of ${existing.size} · ${Fmt.bytes(written)} of ${Fmt.bytes(totalBytes)}",
                    )
                }
                // Written last so it lists exactly the files that made it in.
                zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                val manifest = Manifest(
                    type = type,
                    appVersion = BuildConfig.VERSION_NAME,
                    exportedAt = LocalDateTime.now().withNano(0).toString(),
                    trips = bundles.map {
                        ManifestTrip(it.archive.trip.uuid, it.archive.trip.name, prefixOf(it), it.archive.trip.startDate.toString(), it.archive.trip.endDate.toString())
                    },
                    files = added,
                )
                zip.putText("manifest.json", BackupJson.encodeToString(Manifest.serializer(), manifest))
            }
        } catch (e: IOException) {
            throw BackupException("Export failed: ${e.message ?: "could not write the file"}", e)
        }
        progress(1f, "Done")
        return ExportResult(bundles.size, added.size, written, missing)
    }

    private fun ZipOutputStream.putText(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    /** media/<uuid>/<folder>/<name> -> images/<folder>/<name> or documents/<name>. */
    private fun archivePath(relative: String, tripUuid: String): String {
        val inside = relative.removePrefix("${MediaStorage.MEDIA_DIR}/$tripUuid/")
        val folder = inside.substringBefore('/', "misc")
        val name = inside.substringAfterLast('/')
        return if (folder == "documents") "documents/$name" else "images/$folder/$name"
    }

    // =========================================================================================
    // Import
    // =========================================================================================

    /** An archive that has been copied locally and validated, waiting for the user's go-ahead. */
    class ImportSession internal constructor(
        internal val file: File,
        internal val manifest: Manifest,
        internal val archives: List<Pair<ManifestTrip, TripArchive>>,
        internal val settingsArchive: SettingsArchive?,
        val preview: ImportPreview,
    )

    /**
     * Copies the chosen ZIP into app cache, validates its structure, entry names, sizes, file types
     * and JSON, and returns a preview including which trips already exist on this device.
     */
    suspend fun inspect(source: Uri, progress: Progress = { _, _ -> }): ImportSession = withContext(Dispatchers.IO) {
        progress(0f, "Reading archive")
        val name = media.displayName(source).orEmpty()
        if (name.isNotEmpty() && !name.endsWith(".zip", ignoreCase = true)) throw BackupException("Please choose a Travelogue .zip file.")
        val temp = File(context.cacheDir, "$IMPORT_PREFIX${UUID.randomUUID()}.zip")
        try {
            val input: InputStream = context.contentResolver.openInputStream(source) ?: throw BackupException("Could not open the selected file.")
            input.use { i -> temp.outputStream().use { o -> copyLimited(i, o, MAX_ARCHIVE_BYTES) } }
            progress(0.3f, "Validating")
            val session = ZipFile(temp).use { zip -> validate(zip, temp) }
            progress(1f, "Ready")
            session
        } catch (e: ZipException) {
            temp.delete(); throw BackupException("This file is not a valid ZIP archive.", e)
        } catch (e: BackupException) {
            temp.delete(); throw e
        } catch (e: StorageFullException) {
            temp.delete(); throw BackupException(e.message ?: "Not enough storage.", e)
        } catch (e: IOException) {
            temp.delete(); throw BackupException("Could not read the file: ${e.message}", e)
        }
    }

    private suspend fun validate(zip: ZipFile, temp: File): ImportSession {
        val entries = zip.entries().toList()
        if (entries.size > MAX_ENTRIES) throw BackupException("The archive contains too many files.")
        var declaredTotal = 0L
        for (e in entries) {
            val n = e.name
            if (n.contains("..") || n.startsWith("/") || n.contains('\\') || n.contains(':')) throw BackupException("The archive contains an unsafe file path: $n")
            if (e.isDirectory) continue
            val ext = n.substringAfterLast('.', "").lowercase()
            // Booking attachments keep whatever type the user attached (tiff, docx, …); they are only
            // ever handed to another app to open. Everything else must be a type the app itself reads.
            val isDocument = n.startsWith("documents/") || n.contains("/documents/")
            val allowed = if (isDocument) ext.matches(DOCUMENT_EXTENSION) else ext in ALLOWED_EXTENSIONS
            if (!allowed) throw BackupException("Unsupported file type in archive: ${n.substringAfterLast('/')}")
            if (e.size > MAX_ENTRY_BYTES) throw BackupException("A file in the archive is too large: ${n.substringAfterLast('/')}")
            declaredTotal += e.size.coerceAtLeast(0)
        }
        media.ensureSpace(declaredTotal)

        val manifestEntry = zip.getEntry("manifest.json") ?: throw BackupException("This is not a Travelogue archive (manifest.json is missing).")
        val manifest = parse("manifest.json") { BackupJson.decodeFromString(Manifest.serializer(), zip.readText(manifestEntry)) }
        if (manifest.format != BACKUP_FORMAT) throw BackupException("This is not a Travelogue archive.")
        if (manifest.formatVersion > BACKUP_FORMAT_VERSION) throw BackupException("This archive was made by a newer version of Travelogue. Please update the app.")
        if (manifest.type != BackupType.TRIP && manifest.type != BackupType.FULL) throw BackupException("Unknown archive type: ${manifest.type}")
        if (manifest.trips.isEmpty() && manifest.type == BackupType.TRIP) throw BackupException("The archive does not contain a trip.")

        val archives = manifest.trips.map { mt ->
            val path = mt.path.trimEnd('/').let { if (it.isEmpty()) "" else "$it/" }
            val entry = zip.getEntry(path + "trip.json") ?: throw BackupException("trip.json is missing for “${mt.name}”.")
            val archive = parse("trip.json") { BackupJson.decodeFromString(TripArchive.serializer(), zip.readText(entry)) }
            validateArchive(archive)
            mt.copy(path = path) to archive
        }
        val settingsArchive = zip.getEntry("settings.json")?.let { e ->
            parse("settings.json") { BackupJson.decodeFromString(SettingsArchive.serializer(), zip.readText(e)) }
        }

        val previews = archives.map { (mt, a) ->
            val counts = listOfNotNull(
                a.expenses.size.takeIf { it > 0 }?.let { "$it expenses" },
                a.places.size.takeIf { it > 0 }?.let { "$it places" },
                a.diary.size.takeIf { it > 0 }?.let { "$it diary entries" },
                a.photos.size.takeIf { it > 0 }?.let { "$it photos" },
            ).joinToString(" · ").ifEmpty { "No records yet" }
            PreviewTrip(
                uuid = mt.uuid,
                name = a.trip.name,
                dates = Fmt.dateRange(a.trip.startDate, a.trip.endDate),
                alreadyExists = db.tripDao().getByUuid(a.trip.uuid) != null,
                counts = counts,
            )
        }
        val preview = ImportPreview(manifest.type, manifest.exportedAt, previews, manifest.files.size, declaredTotal, settingsArchive?.allHolidays?.size ?: 0)
        return ImportSession(temp, manifest, archives, settingsArchive, preview)
    }

    private fun validateArchive(a: TripArchive) {
        val t = a.trip
        // The identifier becomes a folder name under media/, so it must not be able to climb out of it.
        if (!SAFE_UUID.matches(t.uuid)) throw BackupException("A trip in the archive has an invalid identifier.")
        if (t.name.isBlank()) throw BackupException("A trip in the archive has no name.")
        if (t.endDate.isBefore(t.startDate)) throw BackupException("“${t.name}” has an end date before its start date.")
        if (t.budget < 0 || a.expenses.any { it.amount.isNaN() || it.amount.isInfinite() }) throw BackupException("“${t.name}” contains invalid amounts.")
        val paths = (listOfNotNull(t.coverPath) + a.expenses.mapNotNull { it.receiptPath } + a.activities.mapNotNull { it.photoPath } +
            a.photos.map { it.relativePath } + a.documents.map { it.relativePath })
        paths.forEach { p -> if (!SAFE_ARCHIVE_PATH.matches(p)) throw BackupException("“${t.name}” references an invalid file path.") }
    }

    private inline fun <T> parse(what: String, block: () -> T): T = try {
        block()
    } catch (e: SerializationException) {
        throw BackupException("$what is damaged or not valid Travelogue data.", e)
    } catch (e: IllegalArgumentException) {
        throw BackupException("$what is damaged or not valid Travelogue data.", e)
    } catch (e: java.time.format.DateTimeParseException) {
        throw BackupException("$what contains an invalid date.", e)
    }

    private fun ZipFile.readText(e: ZipEntry): String {
        if (e.size > MAX_JSON_BYTES) throw BackupException("${e.name} is too large.")
        return getInputStream(e).use { i ->
            val buf = java.io.ByteArrayOutputStream()
            copyLimited(i, buf, MAX_JSON_BYTES)
            buf.toString(Charsets.UTF_8.name())
        }
    }

    fun discard(session: ImportSession) { session.file.delete() }

    /**
     * Restores the trips in [session]. Files are extracted to a staging folder first; the database
     * work for each trip runs in one transaction, so a failure never leaves a half-imported trip.
     *
     * A trip that fails does not stop the others, and does not undo the ones already restored. The
     * result names every trip that failed with its error and carries a [ImportResult.retry] session
     * for just those. Only a single-trip archive with nothing else in it fails as a whole.
     */
    suspend fun import(session: ImportSession, strategy: DuplicateStrategy, progress: Progress = { _, _ -> }): ImportResult = withContext(Dispatchers.IO) { stagingLock.withLock {
        val warnings = mutableListOf<String>()
        var imported = 0
        var skipped = 0
        var holidays = 0
        var settingsError: String? = null
        val failed = mutableListOf<FailedTrip>()
        val failedIndices = mutableListOf<Int>()
        var firstError: Exception? = null
        try {
            ZipFile(session.file).use { zip ->
                session.settingsArchive?.let { s ->
                    try {
                        holidays = restoreSettings(s, warnings)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        settingsError = reasonFor(e)
                    }
                }
                val n = session.archives.size.coerceAtLeast(1)
                // Once the disk is full every later trip would fail the same way; don't churn through them.
                var stopReason: String? = null
                session.archives.forEachIndexed { index, (mt, archive) ->
                    stopReason?.let { failed += FailedTrip(archive.trip.name, it); failedIndices += index; return@forEachIndexed }
                    val base = index.toFloat() / n
                    val span = 1f / n
                    try {
                        val existing = db.tripDao().getByUuid(archive.trip.uuid)
                        if (existing != null && strategy == DuplicateStrategy.SKIP) { skipped++; return@forEachIndexed }
                        progress(base, "Restoring “${archive.trip.name}”")
                        val targetUuid = if (existing != null && strategy == DuplicateStrategy.KEEP_BOTH) UUID.randomUUID().toString() else archive.trip.uuid
                        val replace = existing != null && strategy == DuplicateStrategy.REPLACE
                        warnings += importTrip(zip, mt.path, archive, targetUuid, if (replace) existing else null, keepBothSuffix = existing != null && !replace) { f, msg ->
                            progress(base + span * f, msg)
                        }
                        imported++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (firstError == null) firstError = e
                        val reason = reasonFor(e)
                        failed += FailedTrip(archive.trip.name, reason)
                        failedIndices += index
                        if (isOutOfSpace(e)) stopReason = "Not attempted: $reason"
                    }
                }
            }
            // A single-trip archive that failed changed nothing on this device: report it as the error
            // it was. (A retry narrowed to one trip of a full backup keeps the report and the retry.)
            firstError?.let { e ->
                if (session.manifest.type == BackupType.TRIP) {
                    throw e as? BackupException ?: BackupException(reasonFor(e), e)
                }
            }
        } catch (e: Throwable) {
            session.file.delete()
            throw if (e is Exception && e !is BackupException && e !is CancellationException) BackupException(reasonFor(e), e) else e
        }
        val retry = if (failed.isNotEmpty() || settingsError != null) {
            val keep = failedIndices.toSet()
            val archives = session.archives.filterIndexed { i, _ -> i in keep }
            val uuids = archives.map { it.second.trip.uuid }.toSet()
            ImportSession(
                file = session.file,
                manifest = session.manifest,
                archives = archives,
                // Settings are applied again only if they were what failed.
                settingsArchive = session.settingsArchive?.takeIf { settingsError != null },
                preview = session.preview.copy(trips = session.preview.trips.filter { it.uuid in uuids }),
            )
        } else null
        // The retry session reuses the archive, so it stays until that retry runs or is discarded.
        if (retry == null) session.file.delete()
        progress(1f, "Done")
        ImportResult(imported, skipped, warnings, holidays, failed, settingsError, retry)
    } }

    /** The message shown for an import error, keeping the original wording where there is one. */
    private fun reasonFor(e: Exception): String = when {
        e is BackupException -> e.message ?: "Import failed."
        isOutOfSpace(e) -> e.message?.takeIf { e is StorageFullException } ?: "Not enough storage on this phone."
        else -> "Import failed: ${e.message ?: e.javaClass.simpleName}"
    }

    private fun isOutOfSpace(e: Throwable): Boolean =
        e is StorageFullException || e.cause is StorageFullException || e is android.database.sqlite.SQLiteFullException ||
            (e is IOException && e.message.orEmpty().let { "ENOSPC" in it || "No space left" in it })

    /** Imports and the start-up recovery below both move files through staging folders; never at once. */
    private val stagingLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Finishes or discards imports that were cut off when the app was killed. Called once at start-up.
     *
     * An import extracts files into `media/.staging-<uuid>`, commits the trip's rows, and only then
     * moves the folder to `media/<uuid>`. If the process dies in between, the rows are saved but
     * their files are still in staging, so every photo of that trip would be broken. Which folder is
     * the right one is decided from the database itself: whichever of the two holds more of the files
     * the trip's rows actually reference wins. A staging folder left by an import that died earlier,
     * before its rows were committed, matches nothing (or fewer files than the folder already in
     * place) and is deleted.
     */
    suspend fun recoverInterruptedImports() = withContext(Dispatchers.IO) {
        stagingLock.withLock {
            // Archives copied for an import (or kept for a retry) that the process died holding.
            val staleBefore = System.currentTimeMillis() - STALE_IMPORT_MS
            context.cacheDir.listFiles { f -> f.isFile && f.name.startsWith(IMPORT_PREFIX) && f.name.endsWith(".zip") && f.lastModified() < staleBefore }
                ?.forEach { it.delete() }
            val mediaRoot = File(media.root, MediaStorage.MEDIA_DIR)
            val leftovers = mediaRoot.listFiles { f -> f.isDirectory && f.name.startsWith(STAGING_PREFIX) } ?: return@withLock
            for (staging in leftovers) {
                runCatching {
                    val uuid = staging.name.removePrefix(STAGING_PREFIX)
                    val finalDir = media.tripDir(uuid)
                    val prefix = "${MediaStorage.MEDIA_DIR}/$uuid/"
                    val referenced = db.tripDao().getByUuid(uuid)?.let { referencedFiles(it) }.orEmpty()
                        .filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
                    val inStaging = referenced.count { File(staging, it).isFile }
                    val inPlace = referenced.count { File(finalDir, it).isFile }
                    if (inStaging > inPlace) {
                        finalDir.deleteRecursively()
                        if (!staging.renameTo(finalDir)) {
                            staging.copyRecursively(finalDir, overwrite = true)
                            staging.deleteRecursively()
                        }
                    } else {
                        staging.deleteRecursively()
                    }
                }
            }
        }
    }

    /** Every stored file a trip's rows point at, as paths relative to the app's files folder. */
    private suspend fun referencedFiles(trip: TripEntity): List<String> =
        listOfNotNull(trip.coverPath) +
            db.expenseDao().getForTrip(trip.id).mapNotNull { it.receiptPath } +
            db.itineraryDao().getActivities(trip.id).mapNotNull { it.photoPath } +
            db.photoDao().getForTrip(trip.id).map { it.relativePath } +
            db.tripDao().getDocuments(trip.id).map { it.relativePath }

    private suspend fun importTrip(
        zip: ZipFile,
        prefix: String,
        a: TripArchive,
        uuid: String,
        replacing: TripEntity?,
        keepBothSuffix: Boolean,
        progress: Progress,
    ): List<String> {
        val warnings = mutableListOf<String>()
        val staging = File(media.root, "${MediaStorage.MEDIA_DIR}/$STAGING_PREFIX$uuid").apply { deleteRecursively(); mkdirs() }
        val finalDir = media.tripDir(uuid)
        val finalRel = "${MediaStorage.MEDIA_DIR}/$uuid"

        // 1. Extract referenced files into staging.
        val referenced = (listOfNotNull(a.trip.coverPath) + a.expenses.mapNotNull { it.receiptPath } + a.activities.mapNotNull { it.photoPath } +
            a.photos.map { it.relativePath } + a.documents.map { it.relativePath }).distinct()
        val available = HashSet<String>()
        try {
            referenced.forEachIndexed { i, p ->
                val entry = zip.getEntry(prefix + p)
                if (entry == null) { warnings += "Missing file: ${p.substringAfterLast('/')}"; return@forEachIndexed }
                val target = File(staging, localSubPath(p))
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { i2 -> target.outputStream().use { o -> copyLimited(i2, o, MAX_ENTRY_BYTES) } }
                available += p
                progress(0.7f * (i + 1) / referenced.size.coerceAtLeast(1), "Restoring photos ${i + 1} of ${referenced.size}")
            }
        } catch (e: Exception) {
            // A full disk or an oversized entry must not leave a half-filled staging folder behind.
            staging.deleteRecursively()
            throw e
        }
        fun local(p: String?): String? = p?.takeIf { it in available }?.let { "$finalRel/${localSubPath(it)}" }

        // 2. Database rows, remapping every id.
        progress(0.75f, "Rebuilding records")
        try {
            db.withTransaction {
                replacing?.let { db.tripDao().delete(it.id) }
                val tripId = db.tripDao().insert(
                    a.trip.copy(
                        id = 0, uuid = uuid, coverPath = local(a.trip.coverPath),
                        name = if (keepBothSuffix) "${a.trip.name} (imported)" else a.trip.name,
                    ),
                )
                val travellerMap = HashMap<Long, Long>()
                a.travellers.forEach { t ->
                    val name = t.name.trim().ifEmpty { "Traveller" }
                    travellerMap[t.id] = db.travellerDao().findByName(name)?.id ?: db.travellerDao().insert(TravellerEntity(name = name))
                }
                db.tripDao().insertTripTravellers(a.tripTravellers.mapNotNull { tt -> travellerMap[tt.travellerId]?.let { TripTravellerEntity(tripId, it, tt.sortOrder) } })
                val categoryMap = HashMap<Long, Long>()
                a.categories.forEach { c ->
                    categoryMap[c.id] = db.expenseDao().findCategory(c.name)?.id
                        ?: db.expenseDao().insertCategory(c.copy(id = 0, isDefault = false, sortOrder = 100 + c.sortOrder))
                }
                db.tripDao().upsertRates(a.rates.map { it.copy(tripId = tripId) })

                val expenseMap = HashMap<Long, Long>()
                a.expenses.forEach { e ->
                    expenseMap[e.id] = db.expenseDao().insert(
                        e.copy(
                            id = 0, tripId = tripId, categoryId = e.categoryId?.let(categoryMap::get),
                            paidByTravellerId = e.paidByTravellerId?.let(travellerMap::get), receiptPath = local(e.receiptPath),
                        ),
                    )
                }
                val splits = a.splits.mapNotNull { s ->
                    val eid = expenseMap[s.expenseId]; val tid = travellerMap[s.travellerId]
                    if (eid == null || tid == null) null else ExpenseSplitEntity(expenseId = eid, travellerId = tid, amount = s.amount)
                }
                if (splits.size < a.splits.size) warnings += "${a.splits.size - splits.size} split rows referenced missing records and were skipped"
                db.expenseDao().insertSplits(splits)
                a.settlements.forEach { s ->
                    val from = travellerMap[s.fromTravellerId]; val to = travellerMap[s.toTravellerId]
                    if (from != null && to != null) db.expenseDao().insertSettlement(s.copy(id = 0, tripId = tripId, fromTravellerId = from, toTravellerId = to))
                }

                val hotelMap = HashMap<Long, Long>()
                a.hotels.forEach { h -> hotelMap[h.id] = db.hotelDao().insert(h.copy(id = 0, tripId = tripId)) }
                val dayMap = HashMap<Long, Long>()
                a.days.forEach { d ->
                    val newId = db.itineraryDao().insertDay(d.copy(id = 0, tripId = tripId))
                    dayMap[d.id] = if (newId > 0) newId else db.itineraryDao().getDays(tripId).first { it.date == d.date }.id
                }
                a.activities.forEach { act ->
                    val day = dayMap[act.dayId] ?: return@forEach
                    db.itineraryDao().insertActivity(act.copy(id = 0, tripId = tripId, dayId = day, photoPath = local(act.photoPath)))
                }
                val placeMap = HashMap<Long, Long>()
                a.places.forEach { p -> placeMap[p.id] = db.placeDao().insert(p.copy(id = 0, tripId = tripId)) }
                val bookingMap = HashMap<Long, Long>()
                a.bookings.forEach { b -> bookingMap[b.id] = db.bookingDao().insert(b.copy(id = 0, tripId = tripId)) }
                a.contacts.forEach { c -> db.contactDao().insert(c.copy(id = 0, tripId = tripId)) }

                val diaryMap = HashMap<Long, Long>()
                a.diary.forEach { d -> diaryMap[d.id] = db.diaryDao().insert(d.copy(id = 0, tripId = tripId)) }
                val tagMap = HashMap<Long, Long>()
                a.tags.forEach { t -> tagMap[t.id] = db.diaryDao().findTag(t.name)?.id ?: db.diaryDao().insertTag(TagEntity(name = t.name)) }
                db.diaryDao().insertEntryTags(a.diaryTags.mapNotNull { et ->
                    val e = diaryMap[et.entryId]; val t = tagMap[et.tagId]
                    if (e == null || t == null) null else DiaryEntryTagEntity(e, t)
                })

                a.photos.forEach { p ->
                    val path = local(p.relativePath) ?: return@forEach
                    val owner = when (p.ownerType) {
                        PhotoOwner.DIARY -> p.ownerId?.let(diaryMap::get)
                        PhotoOwner.HOTEL -> p.ownerId?.let(hotelMap::get)
                        PhotoOwner.PLACE -> p.ownerId?.let(placeMap::get)
                        else -> null
                    }
                    // A photo whose owner is gone stays in the trip gallery rather than being lost.
                    val type = if (p.ownerType != PhotoOwner.GENERAL && owner == null) PhotoOwner.GENERAL else p.ownerType
                    db.photoDao().insert(p.copy(id = 0, tripId = tripId, ownerType = type, ownerId = owner, relativePath = path))
                }
                a.documents.forEach { d ->
                    val path = local(d.relativePath) ?: return@forEach
                    val owner = if (d.ownerType == DocumentOwner.BOOKING) d.ownerId?.let(bookingMap::get) else null
                    db.tripDao().insertDocument(
                        d.copy(id = 0, tripId = tripId, relativePath = path, ownerId = owner, ownerType = if (owner == null) DocumentOwner.TRIP else d.ownerType),
                    )
                }
            }
        } catch (e: Exception) {
            staging.deleteRecursively()
            throw e
        }

        // 3. Move files into place (after the transaction committed). The trip is saved from here on,
        // so a failure must not be reported as a failed trip (a retry would import it twice). Whatever
        // is still in staging is moved by recoverInterruptedImports() on the next start.
        progress(0.95f, "Finishing")
        try {
            if (replacing != null) media.deleteTripMedia(replacing.uuid)
            finalDir.deleteRecursively()
            if (!staging.renameTo(finalDir)) {
                staging.copyRecursively(finalDir, overwrite = true)
                staging.deleteRecursively()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            warnings += "Some photos couldn't be moved into place yet; they'll be finished the next time the app starts."
        }
        return warnings.map { "${a.trip.name}: $it" }
    }

    /** images/<folder>/<name> -> <folder>/<name>; documents/<name> -> documents/<name>. */
    private fun localSubPath(archivePath: String): String =
        if (archivePath.startsWith("images/")) archivePath.removePrefix("images/") else archivePath

    /**
     * Restores everything in settings.json and returns how many days off were added to the holiday
     * planner. Anything the user should know about (people the planner had no room for) goes in [warnings].
     */
    private suspend fun restoreSettings(s: SettingsArchive, warnings: MutableList<String>): Int {
        s.travellers.forEach { t -> if (t.name.isNotBlank() && db.travellerDao().findByName(t.name) == null) db.travellerDao().insert(TravellerEntity(name = t.name.trim())) }
        s.categories.forEach { c -> if (c.name.isNotBlank() && db.expenseDao().findCategory(c.name) == null) db.expenseDao().insertCategory(c.copy(id = 0)) }
        settings.setDefaultCurrency(s.defaultCurrency)
        settings.setTheme(enumOr(s.themeMode, ThemeMode.SYSTEM))
        // Older backups have no look, and a newer app may name one this version lacks: keep the current one.
        AppTheme.entries.firstOrNull { it.name == s.appTheme }?.let { settings.setAppTheme(it) }
        SettingsRepository.decodeRates(s.exchangeRates.joinToString(";")).forEach { settings.setExchangeRate(it.from, it.to, it.rate) }
        val before = settings.holidayPrefs.first()
        s.holidayPrefs?.let { settings.setHolidayPrefs(SettingsRepository.restore(it, before)) }
        val people = settings.holidayPrefs.first().people.map { it.id }.toSet()
        // The planner has room for only so many people and the backup's come first. Anyone added on
        // this phone who no longer fits is removed with their days off, rather than leaving days off
        // that nothing shows, and the user is told.
        val pushedOut = before.extras.filter { it.id !in people }
        if (pushedOut.isNotEmpty()) {
            warnings += "Holiday planner: there was no room for ${pushedOut.joinToString { it.name }}, " +
                "added on this phone, so ${if (pushedOut.size == 1) "they were" else "they were all"} removed with their days off."
        }
        // Days off are merged: a day already marked on this device is kept, and only gains the
        // backup's name for it if it had none.
        var added = 0
        db.withTransaction {
            pushedOut.forEach { db.holidayDao().deleteOwner(it.id) }
            s.allHolidays.forEach { h ->
                // A day off for someone who is not in the planner would never show, so it is not restored.
                if (h.date.year !in HOLIDAY_YEARS || h.owner !in people) return@forEach
                val label = h.label.trim().take(HolidayEntity.MAX_LABEL)
                if (db.holidayDao().insertIfAbsent(h.copy(label = label)) != -1L) added++
                else if (label.isNotEmpty()) db.holidayDao().fillBlankLabel(h.owner, h.date, label)
            }
        }
        return added
    }

    // =========================================================================================
    // Maintenance
    // =========================================================================================

    /** Deletes every trip, record, photo, document and preference, then restores default categories. */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        media.deleteAllMedia()
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        settings.clear()
        expenses.ensureDefaultCategories()
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val r = input.read(buf)
            if (r < 0) break
            total += r
            if (total > limit) throw BackupException("The archive is larger than allowed or is corrupted.")
            output.write(buf, 0, r)
        }
        return total
    }

    companion object {
        const val MAX_ARCHIVE_BYTES = 4L * 1024 * 1024 * 1024
        const val MAX_ENTRY_BYTES = 60L * 1024 * 1024
        const val MAX_JSON_BYTES = 50L * 1024 * 1024
        const val MAX_ENTRIES = 50_000
        val ALLOWED_EXTENSIONS = setOf("json", "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "pdf", "bin")
        private const val STAGING_PREFIX = ".staging-"
        private const val IMPORT_PREFIX = "import-"
        /** Older than any import still being prepared when the app starts. */
        private const val STALE_IMPORT_MS = 10 * 60 * 1000L
        /** Guards the calendar against nonsense dates in a hand-edited backup. */
        private val HOLIDAY_YEARS = 1970..2200
        private val DOCUMENT_EXTENSION = Regex("[a-z0-9]{1,8}")
        private val SAFE_UUID = Regex("^[A-Za-z0-9_-]{1,64}$")
        private val SAFE_ARCHIVE_PATH = Regex("^(images/[A-Za-z0-9_-]+|documents)/[A-Za-z0-9._-]+$")

        fun suggestedFileName(tripName: String): String {
            val safe = tripName.trim().replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "Trip" }.take(60)
            return "Travelogue_$safe.zip"
        }

        fun backupFileName(): String = "Travelogue_Backup_${java.time.LocalDate.now()}.zip"
    }
}
