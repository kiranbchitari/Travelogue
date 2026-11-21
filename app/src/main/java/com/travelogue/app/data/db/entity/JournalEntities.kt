@file:UseSerializers(LocalDateSerializer::class, LocalTimeSerializer::class, LocalDateTimeSerializer::class)

package com.travelogue.app.data.db.entity

import com.travelogue.app.data.db.LocalDateSerializer
import com.travelogue.app.data.db.LocalDateTimeSerializer
import com.travelogue.app.data.db.LocalTimeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Personal review of a stay, embedded in [HotelEntity] (one review per stay). */
@Serializable
data class HotelReview(
    val overallRating: Int = 0,
    val cleanlinessRating: Int = 0,
    val locationRating: Int = 0,
    val serviceRating: Int = 0,
    val valueRating: Int = 0,
    val review: String = "",
    val pros: String = "",
    val cons: String = "",
    /** null = not answered yet */
    val wouldStayAgain: Boolean? = null,
)

@Entity(
    tableName = "hotels",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
@Serializable
data class HotelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val address: String = "",
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val roomType: String = "",
    val bookingReference: String = "",
    val bookingPlatform: String = "",
    val phone: String = "",
    val pricePerNight: Double = 0.0,
    val totalPrice: Double = 0.0,
    val currency: String,
    val paymentStatus: PaymentStatus = PaymentStatus.PAID,
    /** Comma separated amenity names. */
    val amenities: String = "",
    @Embedded val review: HotelReview = HotelReview(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val nights: Int get() = (checkOut.toEpochDay() - checkIn.toEpochDay()).toInt().coerceAtLeast(0)
    val amenityList: List<String> get() = amenities.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(
    tableName = "itinerary_days",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["tripId", "date"], unique = true)],
)
@Serializable
data class ItineraryDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val date: LocalDate,
    val title: String = "",
    val notes: String = "",
)

@Entity(
    tableName = "itinerary_activities",
    foreignKeys = [
        ForeignKey(entity = ItineraryDayEntity::class, parentColumns = ["id"], childColumns = ["dayId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("dayId"), Index("tripId")],
)
@Serializable
data class ItineraryActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val dayId: Long,
    val name: String,
    val location: String = "",
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val description: String = "",
    val estimatedCost: Double? = null,
    val actualCost: Double? = null,
    val status: ActivityStatus = ActivityStatus.PLANNED,
    val photoPath: String? = null,
    val notes: String = "",
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "places",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
@Serializable
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val address: String = "",
    val mapsUrl: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val category: PlaceCategory = PlaceCategory.OTHER,
    val visitDate: LocalDate? = null,
    val description: String = "",
    val rating: Int = 0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "bookings",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
@Serializable
data class BookingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val title: String,
    val type: BookingType = BookingType.OTHER,
    val provider: String = "",
    val reference: String = "",
    val fromPlace: String = "",
    val toPlace: String = "",
    val departure: LocalDateTime? = null,
    val arrival: LocalDateTime? = null,
    val amount: Double = 0.0,
    val currency: String,
    val paymentStatus: PaymentStatus = PaymentStatus.PAID,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "contacts",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
@Serializable
data class TravelContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val type: ContactType = ContactType.OTHER,
    val phone: String = "",
    val whatsapp: String = "",
    val address: String = "",
    val associatedWith: String = "",
    val description: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
