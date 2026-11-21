package com.travelogue.app.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.travelogue.app.core.storage.MediaStorage
import com.travelogue.app.data.db.AppDatabase
import com.travelogue.app.data.db.entity.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

class TripRepository(private val db: AppDatabase, private val media: MediaStorage) {
    private val trips = db.tripDao()
    private val travellers = db.travellerDao()

    fun observeTrips(): Flow<List<TripEntity>> = trips.observeAll()
    fun observeTrip(id: Long): Flow<TripEntity?> = trips.observe(id)
    fun observeTravellers(tripId: Long): Flow<List<TravellerEntity>> = trips.observeTravellers(tripId)
    fun observeAllTripTravellers(): Flow<List<TripTravellerEntity>> = trips.observeAllTripTravellers()
    fun observeRates(tripId: Long): Flow<List<CurrencyRateEntity>> = trips.observeRates(tripId)
    fun observeAllRates(): Flow<List<CurrencyRateEntity>> = trips.observeAllRates()
    fun observeDocuments(tripId: Long): Flow<List<DocumentEntity>> = trips.observeDocuments(tripId)
    fun observeDocumentsFor(owner: DocumentOwner, ownerId: Long): Flow<List<DocumentEntity>> = trips.observeDocumentsFor(owner, ownerId)

    suspend fun get(id: Long): TripEntity? = trips.get(id)
    suspend fun getTravellers(tripId: Long): List<TravellerEntity> = trips.getTravellers(tripId)

    /** Suggested status from the dates; the user can override it in the editor. */
    fun statusFor(start: LocalDate, end: LocalDate, today: LocalDate = LocalDate.now()): TripStatus = when {
        today.isAfter(end) -> TripStatus.COMPLETED
        !today.isBefore(start) -> TripStatus.ONGOING
        else -> TripStatus.UPCOMING
    }

    /**
     * Creates or updates a trip. Travellers are matched by name against the shared traveller list
     * (created if new); itinerary days are generated for every date in the trip.
     */
    suspend fun save(
        trip: TripEntity,
        travellerNames: List<String>,
        newCover: Uri? = null,
        removeCover: Boolean = false,
    ): Long {
        val uuid = trip.uuid.ifBlank { UUID.randomUUID().toString() }
        val previous = if (trip.id != 0L) trips.get(trip.id) else null
        var coverPath = previous?.coverPath ?: trip.coverPath
        var oldCoverToDelete: String? = null
        if (newCover != null) {
            coverPath = media.importImage(newCover, uuid, "cover").relativePath
            oldCoverToDelete = previous?.coverPath
        } else if (removeCover) {
            oldCoverToDelete = coverPath
            coverPath = null
        }
        val names = travellerNames.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        val id = db.withTransaction {
            val toSave = trip.copy(
                uuid = uuid,
                coverPath = coverPath,
                travellerCount = maxOf(trip.travellerCount, names.size, 1),
                updatedAt = System.currentTimeMillis(),
            )
            val id = if (trip.id == 0L) trips.insert(toSave) else { trips.update(toSave); trip.id }
            trips.clearTripTravellers(id)
            val rows = names.mapIndexed { i, n ->
                val tid = travellers.findByName(n)?.id ?: travellers.insert(TravellerEntity(name = n))
                TripTravellerEntity(id, tid, i)
            }
            trips.insertTripTravellers(rows)
            ensureItineraryDays(id, toSave.startDate, toSave.endDate)
            id
        }
        oldCoverToDelete?.let { media.delete(it) }
        return id
    }

    /**
     * Inserts a day row for each date in range that doesn't exist yet. Days that fall outside the
     * range after a date change are removed only when they are untouched (no activities, title or
     * notes); anything the user filled in is kept.
     */
    suspend fun ensureItineraryDays(tripId: Long, start: LocalDate, end: LocalDate) {
        if (end.isBefore(start)) return
        val dao = db.itineraryDao()
        dao.getDays(tripId)
            .filter { (it.date.isBefore(start) || it.date.isAfter(end)) && it.title.isBlank() && it.notes.isBlank() }
            .filter { dao.getActivitiesForDay(it.id).isEmpty() }
            .forEach { dao.deleteDay(it.id) }
        var d = start
        var guard = 0
        while (!d.isAfter(end) && guard < 366) {
            dao.insertDay(ItineraryDayEntity(tripId = tripId, date = d))
            d = d.plusDays(1)
            guard++
        }
    }

    suspend fun setArchived(id: Long, archived: Boolean) = trips.setArchived(id, archived)

    suspend fun setStatus(id: Long, status: TripStatus) {
        trips.get(id)?.let { trips.update(it.copy(status = status, updatedAt = System.currentTimeMillis())) }
    }

    /** Deletes the trip, every row that cascades from it, and all of its files. */
    suspend fun delete(id: Long) {
        val trip = trips.get(id) ?: return
        trips.delete(id)
        media.deleteTripMedia(trip.uuid)
    }

    suspend fun setRate(tripId: Long, currency: String, rate: Double?) {
        if (rate == null || rate <= 0) trips.deleteRate(tripId, currency.uppercase())
        else trips.upsertRate(CurrencyRateEntity(tripId, currency.uppercase(), rate))
    }

    // Documents ---------------------------------------------------------------------------------

    suspend fun addDocument(tripId: Long, owner: DocumentOwner, ownerId: Long?, uri: Uri): Long {
        val trip = trips.get(tripId) ?: error("Trip not found")
        val stored = media.importDocument(uri, trip.uuid)
        return trips.insertDocument(
            DocumentEntity(
                tripId = tripId, ownerType = owner, ownerId = ownerId, relativePath = stored.relativePath,
                fileName = stored.fileName, mimeType = stored.mimeType, sizeBytes = stored.sizeBytes,
            ),
        )
    }

    suspend fun deleteDocument(id: Long) {
        val doc = trips.getDocument(id) ?: return
        trips.deleteDocument(id)
        media.delete(doc.relativePath)
    }

    suspend fun deleteDocumentsFor(owner: DocumentOwner, ownerId: Long) {
        trips.getDocumentsFor(owner, ownerId).forEach { deleteDocument(it.id) }
    }

    // Shared traveller list (Settings → Manage travellers) ---------------------------------------

    fun observeAllTravellers(): Flow<List<TravellerEntity>> = travellers.observeAll()

    suspend fun addTraveller(name: String): Long? {
        val n = name.trim()
        if (n.isEmpty()) return null
        return travellers.findByName(n)?.id ?: travellers.insert(TravellerEntity(name = n))
    }

    suspend fun renameTraveller(t: TravellerEntity, name: String) {
        val n = name.trim()
        if (n.isNotEmpty()) travellers.update(t.copy(name = n))
    }

    /**
     * Returns false (and keeps the traveller) if they are still part of any trip, or if any expense,
     * split share or settlement still names them. Deleting would cascade those rows away and leave
     * the remaining balances no longer summing to zero.
     */
    suspend fun deleteTraveller(id: Long): Boolean {
        if (travellers.tripCount(id) > 0 || travellers.moneyReferenceCount(id) > 0) return false
        travellers.delete(id)
        return true
    }
}
