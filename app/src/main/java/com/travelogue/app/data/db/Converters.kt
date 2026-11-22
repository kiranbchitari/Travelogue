package com.travelogue.app.data.db

import androidx.room.TypeConverter
import com.travelogue.app.data.db.entity.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class Converters {
    @TypeConverter fun dateToLong(v: LocalDate?): Long? = v?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)

    @TypeConverter fun timeToInt(v: LocalTime?): Int? = v?.toSecondOfDay()
    @TypeConverter fun intToTime(v: Int?): LocalTime? = v?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    /** Local wall-clock time, stored as seconds with a fixed UTC offset so ordering is stable. */
    @TypeConverter fun dateTimeToLong(v: LocalDateTime?): Long? = v?.toEpochSecond(ZoneOffset.UTC)
    @TypeConverter fun longToDateTime(v: Long?): LocalDateTime? = v?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }

    @TypeConverter fun tripStatus(v: TripStatus): String = v.name
    @TypeConverter fun toTripStatus(v: String): TripStatus = enumOr(v, TripStatus.PLANNING)
    @TypeConverter fun travelType(v: TravelType): String = v.name
    @TypeConverter fun toTravelType(v: String): TravelType = enumOr(v, TravelType.PERSONAL)
    @TypeConverter fun splitType(v: SplitType): String = v.name
    @TypeConverter fun toSplitType(v: String): SplitType = enumOr(v, SplitType.NONE)
    @TypeConverter fun paymentMethod(v: PaymentMethod): String = v.name
    @TypeConverter fun toPaymentMethod(v: String): PaymentMethod = enumOr(v, PaymentMethod.OTHER)
    @TypeConverter fun paymentStatus(v: PaymentStatus): String = v.name
    @TypeConverter fun toPaymentStatus(v: String): PaymentStatus = enumOr(v, PaymentStatus.PAID)
    @TypeConverter fun activityStatus(v: ActivityStatus): String = v.name
    @TypeConverter fun toActivityStatus(v: String): ActivityStatus = enumOr(v, ActivityStatus.PLANNED)
    @TypeConverter fun placeCategory(v: PlaceCategory): String = v.name
    @TypeConverter fun toPlaceCategory(v: String): PlaceCategory = enumOr(v, PlaceCategory.OTHER)
    @TypeConverter fun bookingType(v: BookingType): String = v.name
    @TypeConverter fun toBookingType(v: String): BookingType = enumOr(v, BookingType.OTHER)
    @TypeConverter fun contactType(v: ContactType): String = v.name
    @TypeConverter fun toContactType(v: String): ContactType = enumOr(v, ContactType.OTHER)
    @TypeConverter fun mood(v: Mood?): String? = v?.name
    @TypeConverter fun toMood(v: String?): Mood? = v?.let { s -> Mood.entries.firstOrNull { it.name == s } }
    @TypeConverter fun photoOwner(v: PhotoOwner): String = v.name
    @TypeConverter fun toPhotoOwner(v: String): PhotoOwner = enumOr(v, PhotoOwner.GENERAL)
    @TypeConverter fun documentOwner(v: DocumentOwner): String = v.name
    @TypeConverter fun toDocumentOwner(v: String): DocumentOwner = enumOr(v, DocumentOwner.TRIP)
    @TypeConverter fun holidayOwner(v: HolidayOwner): String = v.name
    @TypeConverter fun toHolidayOwner(v: String): HolidayOwner = enumOr(v, HolidayOwner.ME)
}

inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback
