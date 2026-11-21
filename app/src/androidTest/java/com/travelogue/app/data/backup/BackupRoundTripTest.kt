package com.travelogue.app.data.backup

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.travelogue.app.TravelogueApp
import com.travelogue.app.data.db.entity.TripEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Exports a demo trip to a ZIP, re-imports it, and checks every record and file survived. */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val c = (context.applicationContext as TravelogueApp).container
    private val db = c.database

    @After fun cleanup() = runBlocking { c.demo.remove() }

    private data class Counts(
        val expenses: Int, val splits: Int, val settlements: Int, val hotels: Int, val days: Int, val activities: Int,
        val places: Int, val bookings: Int, val contacts: Int, val diary: Int, val photos: Int, val travellers: Int, val tags: Int, val total: Double,
    )

    private suspend fun counts(t: TripEntity) = Counts(
        db.expenseDao().getForTrip(t.id).size, db.expenseDao().getSplitsForTrip(t.id).size, db.expenseDao().getSettlements(t.id).size,
        db.hotelDao().getForTrip(t.id).size, db.itineraryDao().getDays(t.id).size, db.itineraryDao().getActivities(t.id).size,
        db.placeDao().getForTrip(t.id).size, db.bookingDao().getForTrip(t.id).size, db.contactDao().getForTrip(t.id).size,
        db.diaryDao().getForTrip(t.id).size, db.photoDao().getForTrip(t.id).size, db.tripDao().getTravellers(t.id).size,
        db.diaryDao().getForTrip(t.id).sumOf { db.diaryDao().getTagsFor(it.id).size },
        db.expenseDao().getForTrip(t.id).sumOf { it.amount },
    )

    @Test fun exportThenImportRestoresEverything() = runBlocking {
        c.demo.seed()
        val original = db.tripDao().getAll().first { it.name == "Kerala Backwaters" }
        val before = counts(original)
        assertTrue(before.photos > 0 && before.expenses > 0 && before.splits > 0)

        val zipFile = File(context.cacheDir, "roundtrip.zip").apply { delete() }
        val export = c.backup.exportTrip(original.id, Uri.fromFile(zipFile))
        assertEquals(0, export.missingFiles)

        // Archive layout matches the documented structure.
        ZipFile(zipFile).use { z ->
            assertNotNull(z.getEntry("manifest.json"))
            assertNotNull(z.getEntry("trip.json"))
            val names = z.entries().toList().map { it.name }
            assertTrue(names.any { it.startsWith("images/cover/") })
            assertTrue(names.any { it.startsWith("images/hotels/") })
            assertTrue(names.any { it.startsWith("images/places/") })
            assertTrue(names.any { it.startsWith("images/diary/") })
        }

        // Same archive again → detected as a duplicate.
        val session = c.backup.inspect(Uri.fromFile(zipFile))
        assertEquals(1, session.preview.duplicates.size)
        val result = c.backup.import(session, DuplicateStrategy.KEEP_BOTH)
        assertEquals(1, result.imported)
        assertTrue(result.warnings.toString(), result.warnings.isEmpty())

        val copy = db.tripDao().getAll().first { it.name == "Kerala Backwaters (imported)" }
        assertFalse(copy.uuid == original.uuid)
        assertEquals(before, counts(copy))
        // Every restored photo file exists on disk.
        db.photoDao().getForTrip(copy.id).forEach { assertTrue(it.relativePath, c.media.file(it.relativePath).isFile) }
        assertTrue(c.media.file(copy.coverPath!!).isFile)

        // Replace keeps a single copy with the original uuid.
        val again = c.backup.inspect(Uri.fromFile(zipFile))
        c.backup.import(again, DuplicateStrategy.REPLACE)
        val replaced = db.tripDao().getByUuid(original.uuid)!!
        assertEquals(before, counts(replaced))
        db.photoDao().getForTrip(replaced.id).forEach { assertTrue(c.media.file(it.relativePath).isFile) }

        // Skip leaves things untouched.
        val tripsBefore = db.tripDao().getAll().size
        val skip = c.backup.import(c.backup.inspect(Uri.fromFile(zipFile)), DuplicateStrategy.SKIP)
        assertEquals(1, skip.skipped)
        assertEquals(tripsBefore, db.tripDao().getAll().size)
        c.trips.delete(copy.id)
    }

    @Test fun fullBackupRoundTrip() = runBlocking {
        c.demo.seed()
        val zipFile = File(context.cacheDir, "full.zip").apply { delete() }
        val r = c.backup.exportAll(Uri.fromFile(zipFile))
        assertTrue(r.trips >= 3)
        val session = c.backup.inspect(Uri.fromFile(zipFile))
        assertEquals(BackupType.FULL, session.preview.type)
        assertEquals(r.trips, session.preview.duplicates.size)
        c.backup.discard(session)
    }

    @Test fun rejectsInvalidArchives() = runBlocking {
        fun zipOf(vararg entries: Pair<String, String>): File = File(context.cacheDir, "bad-${entries.size}.zip").apply {
            ZipOutputStream(outputStream()).use { z -> entries.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
        }
        suspend fun expectFailure(f: File) {
            try { c.backup.inspect(Uri.fromFile(f)); fail("Expected ${f.name} to be rejected") } catch (_: BackupException) {}
        }
        expectFailure(File(context.cacheDir, "notzip.zip").apply { writeText("hello") })
        expectFailure(zipOf("readme.json" to "{}"))
        expectFailure(zipOf("manifest.json" to "{not json"))
        expectFailure(zipOf("manifest.json" to """{"format":"other","type":"trip","appVersion":"1","exportedAt":"x","trips":[]}"""))
        expectFailure(zipOf("manifest.json" to "{}", "../evil.json" to "{}"))
        expectFailure(zipOf("manifest.json" to "{}", "images/run.exe" to "x"))
        expectFailure(
            zipOf(
                "manifest.json" to """{"format":"travelogue","type":"trip","appVersion":"1","exportedAt":"x","trips":[{"uuid":"u","name":"n","path":""}]}""",
                "trip.json" to """{"trip":{"uuid":"u","name":"n","destination":"d","startDate":"2026-01-05","endDate":"2026-01-01"}}""",
            ),
        )
        assertTrue(c.trips.observeTrips().first().none { it.uuid == "u" })
    }
}
