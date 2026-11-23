package com.travelogue.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.travelogue.app.data.backup.ContentCounts
import kotlinx.coroutines.flow.Flow

/** Totals shown on the backup screen and stored with each backup. */
@Dao
interface BackupStatsDao {
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM trips) AS trips,
            (SELECT COUNT(*) FROM trip_photos) AS photos,
            (SELECT COUNT(*) FROM expenses) AS expenses,
            (SELECT COUNT(*) FROM diary_entries) AS diary,
            (SELECT COUNT(*) FROM documents) AS documents,
            (SELECT COUNT(*) FROM places) AS places,
            (SELECT COUNT(*) FROM itinerary_activities) AS activities,
            (SELECT COUNT(*) FROM hotels) AS hotels,
            (SELECT COUNT(*) FROM bookings) AS bookings,
            (SELECT COUNT(*) FROM holidays) AS holidays,
            (SELECT COUNT(*) FROM travellers) AS travellers
        """,
    )
    fun observeCounts(): Flow<ContentCounts>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM trips) AS trips,
            (SELECT COUNT(*) FROM trip_photos) AS photos,
            (SELECT COUNT(*) FROM expenses) AS expenses,
            (SELECT COUNT(*) FROM diary_entries) AS diary,
            (SELECT COUNT(*) FROM documents) AS documents,
            (SELECT COUNT(*) FROM places) AS places,
            (SELECT COUNT(*) FROM itinerary_activities) AS activities,
            (SELECT COUNT(*) FROM hotels) AS hotels,
            (SELECT COUNT(*) FROM bookings) AS bookings,
            (SELECT COUNT(*) FROM holidays) AS holidays,
            (SELECT COUNT(*) FROM travellers) AS travellers
        """,
    )
    suspend fun counts(): ContentCounts
}
