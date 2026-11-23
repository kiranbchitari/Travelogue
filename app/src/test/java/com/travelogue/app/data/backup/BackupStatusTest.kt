package com.travelogue.app.data.backup

import com.travelogue.app.core.util.Fmt
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class BackupStatusTest {
    private val now = LocalDateTime.of(2026, 11, 23, 15, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val counts = ContentCounts(trips = 3, photos = 40, expenses = 12, diary = 2, holidays = 5)

    private fun record(at: Long, contents: ContentCounts = counts, destination: String = BackupDestination.DRIVE) =
        BackupRecord(at = at, destination = destination, fileName = "b.zip", bytes = 1000, contents = contents)

    private fun hoursAgo(h: Long) = now - TimeUnit.HOURS.toMillis(h)
    private fun daysAgo(d: Long) = now - TimeUnit.DAYS.toMillis(d)

    @Test fun emptyPhoneWithNoBackupIsEmpty() {
        assertEquals(BackupHealth.EMPTY, backupStatus(BackupHistory(), ContentCounts(), now).health)
        assertEquals(BackupHealth.EMPTY, backupStatus(BackupHistory(), null, now).health)
    }

    @Test fun dataWithNoBackupIsNever() {
        val s = backupStatus(BackupHistory(), counts, now)
        assertEquals(BackupHealth.NEVER, s.health)
        assertNull(s.latest)
    }

    @Test fun holidaysAloneStillNeedABackup() {
        assertEquals(BackupHealth.NEVER, backupStatus(BackupHistory(), ContentCounts(holidays = 4), now).health)
    }

    @Test fun recentBackupWithSameCountsIsCurrent() {
        val s = backupStatus(BackupHistory(drive = record(hoursAgo(2))), counts, now)
        assertEquals(BackupHealth.CURRENT, s.health)
        assertTrue(s.changes.isEmpty())
    }

    @Test fun addedAndRemovedItemsAreListedInOrder() {
        val nowCounts = counts.copy(photos = 52, trips = 2, expenses = 13)
        val s = backupStatus(BackupHistory(drive = record(hoursAgo(2))), nowCounts, now)
        assertEquals(BackupHealth.CHANGED, s.health)
        assertEquals(listOf("1 trip removed", "12 new photos", "1 new expense"), s.changes)
    }

    @Test fun changesWinOverAge() {
        val s = backupStatus(BackupHistory(drive = record(daysAgo(40))), counts.copy(diary = 4), now)
        assertEquals(BackupHealth.CHANGED, s.health)
        assertEquals(listOf("2 new diary entries"), s.changes)
    }

    @Test fun oldBackupWithSameCountsIsStale() {
        assertEquals(BackupHealth.CURRENT, backupStatus(BackupHistory(drive = record(daysAgo(13))), counts, now).health)
        assertEquals(BackupHealth.STALE, backupStatus(BackupHistory(drive = record(daysAgo(15))), counts, now).health)
    }

    @Test fun theNewerOfDriveAndZipIsCompared() {
        val drive = record(daysAgo(3), counts.copy(photos = 10))
        val zip = record(hoursAgo(1), counts, BackupDestination.FILE)
        val s = backupStatus(BackupHistory(drive = drive, file = zip), counts, now)
        assertEquals(zip, s.latest)
        assertEquals(BackupHealth.CURRENT, s.health)
    }

    @Test fun recordSurvivesJsonAndToleratesOlderFields() {
        val r = record(hoursAgo(1))
        assertEquals(r, BackupJson.decodeFromString(BackupRecord.serializer(), BackupJson.encodeToString(BackupRecord.serializer(), r)))
        // A record written before a count existed still reads, with that count as zero.
        val old = Json { ignoreUnknownKeys = true }.decodeFromString(
            BackupRecord.serializer(),
            """{"at":1,"destination":"file","fileName":"x.zip","bytes":5,"contents":{"trips":2}}""",
        )
        assertEquals(2, old.contents.trips)
        assertEquals(0, old.contents.photos)
    }

    @Test fun agoReadsNaturally() {
        val z = ZoneOffset.UTC
        assertEquals("just now", Fmt.ago(now - 20_000, now, z))
        assertEquals("1 minute ago", Fmt.ago(now - 60_000, now, z))
        assertEquals("45 minutes ago", Fmt.ago(now - 45 * 60_000L, now, z))
        assertEquals("3 hours ago", Fmt.ago(hoursAgo(3), now, z))
        assertEquals("yesterday", Fmt.ago(hoursAgo(20), now, z))
        assertEquals("4 days ago", Fmt.ago(daysAgo(4), now, z))
        assertEquals(Fmt.date(LocalDateTime.of(2026, 11, 9, 15, 0).toLocalDate()), Fmt.ago(daysAgo(14), now, z))
    }
}
