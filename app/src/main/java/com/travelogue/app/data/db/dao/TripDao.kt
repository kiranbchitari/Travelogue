package com.travelogue.app.data.db.dao

import androidx.room.*
import com.travelogue.app.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startDate DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observe(id: Long): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): TripEntity?

    @Query("SELECT * FROM trips ORDER BY startDate DESC")
    suspend fun getAll(): List<TripEntity>

    @Insert suspend fun insert(trip: TripEntity): Long
    @Update suspend fun update(trip: TripEntity)
    @Query("DELETE FROM trips WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE trips SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long = System.currentTimeMillis())
    @Query("UPDATE trips SET coverPath = :path, coverFocusX = 0.5, coverFocusY = 0.5, coverZoom = 1.0, updatedAt = :now WHERE id = :id")
    suspend fun setCover(id: Long, path: String?, now: Long = System.currentTimeMillis())

    // Travellers on a trip
    @Query("SELECT t.* FROM travellers t INNER JOIN trip_travellers tt ON tt.travellerId = t.id WHERE tt.tripId = :tripId ORDER BY tt.sortOrder, t.name")
    fun observeTravellers(tripId: Long): Flow<List<TravellerEntity>>

    @Query("SELECT t.* FROM travellers t INNER JOIN trip_travellers tt ON tt.travellerId = t.id WHERE tt.tripId = :tripId ORDER BY tt.sortOrder, t.name")
    suspend fun getTravellers(tripId: Long): List<TravellerEntity>

    @Query("SELECT * FROM trip_travellers")
    fun observeAllTripTravellers(): Flow<List<TripTravellerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTripTravellers(rows: List<TripTravellerEntity>)
    @Query("DELETE FROM trip_travellers WHERE tripId = :tripId") suspend fun clearTripTravellers(tripId: Long)

    // Currency rates
    @Query("SELECT * FROM currency_rates WHERE tripId = :tripId") fun observeRates(tripId: Long): Flow<List<CurrencyRateEntity>>
    @Query("SELECT * FROM currency_rates") fun observeAllRates(): Flow<List<CurrencyRateEntity>>
    @Query("SELECT * FROM currency_rates WHERE tripId = :tripId") suspend fun getRates(tripId: Long): List<CurrencyRateEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRate(rate: CurrencyRateEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRates(rates: List<CurrencyRateEntity>)
    @Query("DELETE FROM currency_rates WHERE tripId = :tripId AND currency = :currency") suspend fun deleteRate(tripId: Long, currency: String)

    // Documents
    @Query("SELECT * FROM documents WHERE tripId = :tripId ORDER BY createdAt DESC") fun observeDocuments(tripId: Long): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM documents WHERE tripId = :tripId") suspend fun getDocuments(tripId: Long): List<DocumentEntity>
    @Query("SELECT * FROM documents WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt") fun observeDocumentsFor(ownerType: DocumentOwner, ownerId: Long): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM documents WHERE ownerType = :ownerType AND ownerId = :ownerId") suspend fun getDocumentsFor(ownerType: DocumentOwner, ownerId: Long): List<DocumentEntity>
    @Query("SELECT * FROM documents WHERE id = :id") suspend fun getDocument(id: Long): DocumentEntity?
    @Insert suspend fun insertDocument(doc: DocumentEntity): Long
    @Update suspend fun updateDocument(doc: DocumentEntity)
    @Query("DELETE FROM documents WHERE id = :id") suspend fun deleteDocument(id: Long)
    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM documents") fun observeDocumentBytes(): Flow<Long>
}

@Dao
interface TravellerDao {
    @Query("SELECT * FROM travellers ORDER BY name COLLATE NOCASE") fun observeAll(): Flow<List<TravellerEntity>>
    @Query("SELECT * FROM travellers ORDER BY name COLLATE NOCASE") suspend fun getAll(): List<TravellerEntity>
    @Query("SELECT * FROM travellers WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun findByName(name: String): TravellerEntity?
    @Insert suspend fun insert(t: TravellerEntity): Long
    @Update suspend fun update(t: TravellerEntity)
    @Query("DELETE FROM travellers WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT COUNT(*) FROM trip_travellers WHERE travellerId = :id") suspend fun tripCount(id: Long): Int

    /** Split shares, settlements and payments that still name this traveller. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM expense_splits WHERE travellerId = :id) + " +
            "(SELECT COUNT(*) FROM settlements WHERE fromTravellerId = :id OR toTravellerId = :id) + " +
            "(SELECT COUNT(*) FROM expenses WHERE paidByTravellerId = :id)",
    )
    suspend fun moneyReferenceCount(id: Long): Int
}
