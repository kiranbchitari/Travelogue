package com.travelogue.app.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.travelogue.app.core.storage.MediaStorage
import com.travelogue.app.data.db.AppDatabase
import com.travelogue.app.data.db.entity.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/** Hotels, itinerary, places, bookings and contacts for a trip. */
class JournalRepository(
    private val db: AppDatabase,
    private val media: MediaStorage,
    private val photos: PhotoRepository,
    private val trips: TripRepository,
) {
    private val hotels = db.hotelDao()
    private val itinerary = db.itineraryDao()
    private val places = db.placeDao()
    private val bookings = db.bookingDao()
    private val contacts = db.contactDao()

    // Hotels ------------------------------------------------------------------------------------
    fun observeAllHotels(): Flow<List<HotelEntity>> = hotels.observeAll()
    fun observeHotels(tripId: Long): Flow<List<HotelEntity>> = hotels.observeForTrip(tripId)
    fun observeHotel(id: Long): Flow<HotelEntity?> = hotels.observe(id)
    suspend fun getHotel(id: Long): HotelEntity? = hotels.get(id)

    suspend fun saveHotel(hotel: HotelEntity, newPhotos: List<Uri> = emptyList()): Long {
        val id = if (hotel.id == 0L) hotels.insert(hotel) else { hotels.update(hotel); hotel.id }
        undoNewRowOnFailure(isNew = hotel.id == 0L, undo = { deleteHotel(id) }) {
            if (newPhotos.isNotEmpty()) photos.addPhotos(hotel.tripId, newPhotos, PhotoOwner.HOTEL, id, hotel.checkIn.atTime(14, 0))
        }
        return id
    }

    suspend fun deleteHotel(id: Long) {
        photos.deleteFor(PhotoOwner.HOTEL, id)
        hotels.delete(id)
    }

    // Itinerary ---------------------------------------------------------------------------------
    fun observeDays(tripId: Long): Flow<List<ItineraryDayEntity>> = itinerary.observeDays(tripId)
    fun observeActivities(tripId: Long): Flow<List<ItineraryActivityEntity>> = itinerary.observeActivities(tripId)
    fun observeAllActivities(): Flow<List<ItineraryActivityEntity>> = itinerary.observeAllActivities()
    suspend fun getActivity(id: Long): ItineraryActivityEntity? = itinerary.getActivity(id)
    suspend fun getDays(tripId: Long): List<ItineraryDayEntity> = itinerary.getDays(tripId)

    suspend fun addDay(tripId: Long, date: java.time.LocalDate): Long = itinerary.insertDay(ItineraryDayEntity(tripId = tripId, date = date))
    suspend fun updateDay(day: ItineraryDayEntity) = itinerary.updateDay(day)
    suspend fun deleteDay(id: Long) {
        itinerary.getActivitiesForDay(id).forEach { media.delete(it.photoPath) }
        itinerary.deleteDay(id)
    }

    suspend fun saveActivity(activity: ItineraryActivityEntity, newPhoto: Uri? = null, removePhoto: Boolean = false): Long {
        val trip = trips.get(activity.tripId) ?: error("Trip not found")
        val previous = if (activity.id != 0L) itinerary.getActivity(activity.id) else null
        var path = previous?.photoPath ?: activity.photoPath
        var toDelete: String? = null
        var imported: String? = null
        if (newPhoto != null) {
            imported = media.importImage(newPhoto, trip.uuid, PhotoOwner.ACTIVITY.folder).relativePath
            path = imported
            toDelete = previous?.photoPath
        } else if (removePhoto) {
            toDelete = path; path = null
        }
        val id = media.recordOrDiscard(imported) { if (activity.id == 0L) {
            val order = itinerary.maxSort(activity.dayId) + 1
            itinerary.insertActivity(activity.copy(photoPath = path, sortOrder = order))
        } else {
            // Moving to another day puts it at the end of that day.
            val row = if (previous != null && previous.dayId != activity.dayId) {
                activity.copy(photoPath = path, sortOrder = itinerary.maxSort(activity.dayId) + 1)
            } else activity.copy(photoPath = path)
            itinerary.updateActivity(row); activity.id
        } }
        toDelete?.let { media.delete(it) }
        return id
    }

    suspend fun setActivityStatus(id: Long, status: ActivityStatus) {
        itinerary.getActivity(id)?.let { itinerary.updateActivity(it.copy(status = status)) }
    }

    suspend fun deleteActivity(id: Long) {
        val a = itinerary.getActivity(id) ?: return
        itinerary.deleteActivity(id)
        media.delete(a.photoPath)
    }

    /** Persists a new order for one day's activities. */
    suspend fun reorderActivities(dayId: Long, orderedIds: List<Long>) = db.withTransaction {
        val current = itinerary.getActivitiesForDay(dayId).associateBy { it.id }
        itinerary.updateActivities(orderedIds.mapIndexedNotNull { i, id -> current[id]?.copy(sortOrder = i) })
    }

    /** Moves an activity up (-1) or down (+1) within its day. */
    suspend fun nudgeActivity(id: Long, delta: Int) {
        val a = itinerary.getActivity(id) ?: return
        val list = itinerary.getActivitiesForDay(a.dayId).sortedBy { it.sortOrder }.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        reorderActivities(a.dayId, list.map { it.id })
    }

    suspend fun moveActivity(id: Long, toDayId: Long) {
        val a = itinerary.getActivity(id) ?: return
        if (a.dayId == toDayId) return
        itinerary.updateActivity(a.copy(dayId = toDayId, sortOrder = itinerary.maxSort(toDayId) + 1))
    }

    // Places ------------------------------------------------------------------------------------
    fun observeAllPlaces(): Flow<List<PlaceEntity>> = places.observeAll()
    fun observePlaces(tripId: Long): Flow<List<PlaceEntity>> = places.observeForTrip(tripId)
    fun observePlace(id: Long): Flow<PlaceEntity?> = places.observe(id)
    suspend fun getPlace(id: Long): PlaceEntity? = places.get(id)

    suspend fun savePlace(place: PlaceEntity, newPhotos: List<Uri> = emptyList()): Long {
        val id = if (place.id == 0L) places.insert(place) else { places.update(place); place.id }
        undoNewRowOnFailure(isNew = place.id == 0L, undo = { deletePlace(id) }) {
            if (newPhotos.isNotEmpty()) {
                val at = (place.visitDate ?: java.time.LocalDate.now()).atTime(12, 0)
                photos.addPhotos(place.tripId, newPhotos, PhotoOwner.PLACE, id, at)
            }
        }
        return id
    }

    suspend fun deletePlace(id: Long) {
        photos.deleteFor(PhotoOwner.PLACE, id)
        places.delete(id)
    }

    // Bookings ----------------------------------------------------------------------------------
    fun observeBookings(tripId: Long): Flow<List<BookingEntity>> = bookings.observeForTrip(tripId)
    suspend fun getBooking(id: Long): BookingEntity? = bookings.get(id)

    suspend fun saveBooking(booking: BookingEntity, newAttachments: List<Uri> = emptyList()): Long {
        val id = if (booking.id == 0L) bookings.insert(booking) else { bookings.update(booking); booking.id }
        undoNewRowOnFailure(isNew = booking.id == 0L, undo = { deleteBooking(id) }) {
            newAttachments.forEach { trips.addDocument(booking.tripId, DocumentOwner.BOOKING, id, it) }
        }
        return id
    }

    suspend fun deleteBooking(id: Long) {
        trips.deleteDocumentsFor(DocumentOwner.BOOKING, id)
        bookings.delete(id)
    }

    // Contacts ----------------------------------------------------------------------------------
    fun observeContacts(tripId: Long): Flow<List<TravelContactEntity>> = contacts.observeForTrip(tripId)
    suspend fun getContact(id: Long): TravelContactEntity? = contacts.get(id)
    suspend fun saveContact(c: TravelContactEntity): Long = if (c.id == 0L) contacts.insert(c) else { contacts.update(c); c.id }
    suspend fun deleteContact(id: Long) = contacts.delete(id)

}

/**
 * Runs the media step of a save. The row is written first (photos and documents need its id), so if
 * copying a file then fails (file too large, storage full) a row that was only just created is
 * removed again. Otherwise the editor, which still thinks the record is new, would insert a second
 * copy when the user fixes the problem and taps Save again.
 */
internal suspend fun undoNewRowOnFailure(isNew: Boolean, undo: suspend () -> Unit, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        if (isNew && e !is kotlinx.coroutines.CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { runCatching { undo() } }
        }
        throw e
    }
}
