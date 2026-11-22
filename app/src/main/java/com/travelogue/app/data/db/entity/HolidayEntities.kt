@file:UseSerializers(LocalDateSerializer::class)

package com.travelogue.app.data.db.entity

import androidx.room.Entity
import com.travelogue.app.data.db.LocalDateSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate

/**
 * One day off for one person (a public holiday, a company holiday or booked leave). A day both
 * people have off is two rows. Weekly days off are a setting, not rows; see HolidayPrefs.
 */
@Entity(tableName = "holidays", primaryKeys = ["owner", "date"])
@Serializable
data class HolidayEntity(
    val owner: HolidayOwner,
    val date: LocalDate,
    /** Optional name such as "Diwali" or "Annual leave". */
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val MAX_LABEL = 60
    }
}
