package com.travelogue.app.data.backup

import com.travelogue.app.data.db.entity.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val BACKUP_FORMAT = "travelogue"
const val BACKUP_FORMAT_VERSION = 1

object BackupType {
    const val TRIP = "trip"
    const val FULL = "full_backup"
}

/** manifest.json — identifies the archive and lists every file with its size. */
@Serializable
data class Manifest(
    val format: String = BACKUP_FORMAT,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val type: String,
    val appVersion: String,
    val exportedAt: String,
    val trips: List<ManifestTrip>,
    val files: List<ManifestFile> = emptyList(),
)

@Serializable
data class ManifestTrip(
    val uuid: String,
    val name: String,
    /** Folder inside the archive that holds trip.json, images/ and documents/ ("" for single-trip archives). */
    val path: String,
    val startDate: String = "",
    val endDate: String = "",
)

@Serializable
data class ManifestFile(val path: String, val size: Long)

/**
 * trip.json — the complete trip with every related record. Row ids are the exporting device's local
 * ids; they only serve to preserve relationships inside this file and are remapped on import.
 * File paths are archive-relative (e.g. images/diary/abc.jpg).
 */
@Serializable
data class TripArchive(
    val schemaVersion: Int = BACKUP_FORMAT_VERSION,
    val trip: TripEntity,
    val travellers: List<TravellerEntity> = emptyList(),
    val tripTravellers: List<TripTravellerEntity> = emptyList(),
    val categories: List<ExpenseCategoryEntity> = emptyList(),
    val rates: List<CurrencyRateEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val splits: List<ExpenseSplitEntity> = emptyList(),
    val settlements: List<SettlementEntity> = emptyList(),
    val hotels: List<HotelEntity> = emptyList(),
    val days: List<ItineraryDayEntity> = emptyList(),
    val activities: List<ItineraryActivityEntity> = emptyList(),
    val places: List<PlaceEntity> = emptyList(),
    val bookings: List<BookingEntity> = emptyList(),
    val contacts: List<TravelContactEntity> = emptyList(),
    val diary: List<DiaryEntryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val diaryTags: List<DiaryEntryTagEntity> = emptyList(),
    val photos: List<TripPhotoEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList(),
)

/** settings.json — only in full backups. */
@Serializable
data class SettingsArchive(
    val defaultCurrency: String = "INR",
    val themeMode: String = "SYSTEM",
    /** The chosen look (an AppTheme name); absent in backups made before themes existed. */
    val appTheme: String? = null,
    val travellers: List<TravellerEntity> = emptyList(),
    val categories: List<ExpenseCategoryEntity> = emptyList(),
    /** "EUR>INR=92.5" entries (Settings → Exchange rates). */
    val exchangeRates: List<String> = emptyList(),
    /** Holiday planner: every marked day off for both people. */
    val holidays: List<HolidayEntity> = emptyList(),
    /** Holiday planner names and weekly days off; absent in backups made before the planner existed. */
    val holidayPrefs: HolidayPrefsArchive? = null,
)

@Serializable
data class HolidayPrefsArchive(
    val myName: String = "",
    val partnerName: String = "",
    /** ISO weekday numbers, Monday = 1. */
    val myWeeklyOff: List<Int> = listOf(6, 7),
    val partnerWeeklyOff: List<Int> = listOf(6, 7),
    val showIndianHolidays: Boolean = true,
)

val BackupJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}

/** What the user is shown before an import runs. */
data class ImportPreview(
    val type: String,
    val exportedAt: String,
    val trips: List<PreviewTrip>,
    val totalFiles: Int,
    val totalBytes: Long,
    /** Marked days off in a full backup's holiday planner. */
    val holidays: Int = 0,
) {
    val duplicates: List<PreviewTrip> get() = trips.filter { it.alreadyExists }
}

data class PreviewTrip(
    val uuid: String,
    val name: String,
    val dates: String,
    val alreadyExists: Boolean,
    val counts: String,
)

enum class DuplicateStrategy { SKIP, REPLACE, KEEP_BOTH }

data class ImportResult(
    val imported: Int,
    val skipped: Int,
    val warnings: List<String>,
    val holidays: Int = 0,
    /** Trips that were not restored, each with the error that stopped it. Every other trip is intact. */
    val failed: List<FailedTrip> = emptyList(),
    /** Why settings.json could not be fully applied; null when it was, or when there was none. */
    val settingsError: String? = null,
    /**
     * When anything failed: the same archive narrowed to just what failed, ready to pass back to
     * [BackupManager.import]. Its file is kept until it is imported or [BackupManager.discard]ed.
     */
    val retry: BackupManager.ImportSession? = null,
) {
    val isPartial: Boolean get() = failed.isNotEmpty() || settingsError != null
}

data class FailedTrip(val name: String, val reason: String)
data class ExportResult(
    val trips: Int,
    val files: Int,
    val bytes: Long,
    val missingFiles: Int,
    val holidays: Int = 0,
    /** Every kind of record in a full backup, counted in the same snapshot it was written from. */
    val contents: ContentCounts? = null,
)

/** Progress callback: fraction 0..1 and a short human message. */
typealias Progress = (fraction: Float, message: String) -> Unit

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
