package com.travelogue.app.data.backup

import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

/**
 * How many of each kind of record exist. Read live from the database for the backup screen, and
 * stored with each [BackupRecord] so the screen can say what has changed since.
 */
@Serializable
data class ContentCounts(
    val trips: Int = 0,
    val photos: Int = 0,
    val expenses: Int = 0,
    val diary: Int = 0,
    val documents: Int = 0,
    val places: Int = 0,
    val activities: Int = 0,
    val hotels: Int = 0,
    val bookings: Int = 0,
    val holidays: Int = 0,
    val travellers: Int = 0,
) {
    val isEmpty: Boolean get() = trips == 0 && holidays == 0

    /** Each kind with its singular and plural name, most important first. */
    internal fun kinds(): List<Triple<Int, String, String>> = listOf(
        Triple(trips, "trip", "trips"),
        Triple(photos, "photo", "photos"),
        Triple(expenses, "expense", "expenses"),
        Triple(diary, "diary entry", "diary entries"),
        Triple(documents, "document", "documents"),
        Triple(places, "place", "places"),
        Triple(activities, "itinerary activity", "itinerary activities"),
        Triple(hotels, "hotel", "hotels"),
        Triple(bookings, "booking", "bookings"),
        Triple(holidays, "day off", "days off"),
        Triple(travellers, "traveller", "travellers"),
    )
}

object BackupDestination {
    const val DRIVE = "drive"
    const val FILE = "file"
}

/** One completed full backup: when, where, how big and what was in it. */
@Serializable
data class BackupRecord(
    /** Epoch milliseconds when the backup finished. */
    val at: Long,
    /** [BackupDestination.DRIVE] or [BackupDestination.FILE]. */
    val destination: String,
    val fileName: String,
    val bytes: Long,
    val contents: ContentCounts = ContentCounts(),
    /** Photos and documents written into the archive. */
    val files: Int = 0,
    /** Files the records point at that were already gone from the phone, so are not in the archive. */
    val missingFiles: Int = 0,
) {
    val isDrive: Boolean get() = destination == BackupDestination.DRIVE
}

/** The last full backup to each place. Either can be null (never done). */
data class BackupHistory(val drive: BackupRecord? = null, val file: BackupRecord? = null) {
    val latest: BackupRecord? get() = listOfNotNull(drive, file).maxByOrNull { it.at }
}

enum class BackupHealth {
    /** Nothing on the phone yet, so nothing to lose. */
    EMPTY,
    /** There is data but it has never been backed up. */
    NEVER,
    /** The latest backup has the same number of everything as the phone, and is recent. */
    CURRENT,
    /** Things were added or removed since the latest backup. */
    CHANGED,
    /** Nothing added or removed, but the backup is old enough that edits since are likely. */
    STALE,
}

data class BackupStatus(
    val health: BackupHealth,
    val latest: BackupRecord?,
    /** "12 new photos", "1 trip removed": what differs between the phone and [latest]. */
    val changes: List<String> = emptyList(),
)

/** A backup this old is flagged even when no records were added or removed since. */
val STALE_AFTER_MS: Long = TimeUnit.DAYS.toMillis(14)

/**
 * Compares the phone's current [counts] with the latest backup in [history]. Only counts are
 * compared, so an edit that adds or removes nothing is not seen; [BackupHealth.STALE] covers that
 * by flagging any backup older than [STALE_AFTER_MS].
 */
fun backupStatus(history: BackupHistory, counts: ContentCounts?, now: Long = System.currentTimeMillis()): BackupStatus {
    val latest = history.latest
    if (latest == null) {
        return BackupStatus(if (counts == null || counts.isEmpty) BackupHealth.EMPTY else BackupHealth.NEVER, null)
    }
    val changes = if (counts == null) emptyList() else changesBetween(latest.contents, counts)
    val health = when {
        changes.isNotEmpty() -> BackupHealth.CHANGED
        now - latest.at > STALE_AFTER_MS -> BackupHealth.STALE
        else -> BackupHealth.CURRENT
    }
    return BackupStatus(health, latest, changes)
}

private fun changesBetween(then: ContentCounts, now: ContentCounts): List<String> =
    then.kinds().zip(now.kinds()).mapNotNull { (old, new) ->
        val diff = new.first - old.first
        val (one, many) = new.second to new.third
        when {
            diff > 0 -> "$diff new ${if (diff == 1) one else many}"
            diff < 0 -> "${-diff} ${if (diff == -1) one else many} removed"
            else -> null
        }
    }
