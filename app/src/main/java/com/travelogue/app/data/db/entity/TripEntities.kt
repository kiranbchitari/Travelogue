@file:UseSerializers(LocalDateSerializer::class, LocalTimeSerializer::class, LocalDateTimeSerializer::class)

package com.travelogue.app.data.db.entity

import com.travelogue.app.data.db.LocalDateSerializer
import com.travelogue.app.data.db.LocalDateTimeSerializer
import com.travelogue.app.data.db.LocalTimeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "trips", indices = [Index(value = ["uuid"], unique = true), Index("startDate")])
@Serializable
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable identity across export/import; used to detect duplicate imports. */
    val uuid: String,
    val name: String,
    val destination: String,
    val country: String = "",
    val startDate: LocalDate,
    val endDate: LocalDate,
    val travellerCount: Int = 1,
    val description: String = "",
    /** Relative path inside app files dir, e.g. media/<uuid>/cover/abc.jpg */
    val coverPath: String? = null,
    val status: TripStatus = TripStatus.PLANNING,
    val budget: Double = 0.0,
    val currency: String = "INR",
    val travelType: TravelType = TravelType.PERSONAL,
    val notes: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val days: Int get() = (endDate.toEpochDay() - startDate.toEpochDay()).toInt().coerceAtLeast(0) + 1
}

@Entity(tableName = "travellers", indices = [Index(value = ["name"])])
@Serializable
data class TravellerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "trip_travellers",
    primaryKeys = ["tripId", "travellerId"],
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TravellerEntity::class, parentColumns = ["id"], childColumns = ["travellerId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("travellerId")],
)
@Serializable
data class TripTravellerEntity(
    val tripId: Long,
    val travellerId: Long,
    val sortOrder: Int = 0,
)

/** Manual conversion rate: 1 unit of [currency] = [rate] units of the trip currency. */
@Entity(
    tableName = "currency_rates",
    primaryKeys = ["tripId", "currency"],
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
)
@Serializable
data class CurrencyRateEntity(
    val tripId: Long,
    val currency: String,
    val rate: Double,
)

@Entity(
    tableName = "documents",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId"), Index(value = ["ownerType", "ownerId"])],
)
@Serializable
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val ownerType: DocumentOwner,
    val ownerId: Long?,
    val relativePath: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
)
