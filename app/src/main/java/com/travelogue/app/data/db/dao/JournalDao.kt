package com.travelogue.app.data.db.dao

import androidx.room.*
import com.travelogue.app.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HotelDao {
    @Query("SELECT * FROM hotels ORDER BY checkIn") fun observeAll(): Flow<List<HotelEntity>>
    @Query("SELECT * FROM hotels WHERE tripId = :tripId ORDER BY checkIn") fun observeForTrip(tripId: Long): Flow<List<HotelEntity>>
    @Query("SELECT * FROM hotels WHERE tripId = :tripId") suspend fun getForTrip(tripId: Long): List<HotelEntity>
    @Query("SELECT * FROM hotels WHERE id = :id") fun observe(id: Long): Flow<HotelEntity?>
    @Query("SELECT * FROM hotels WHERE id = :id") suspend fun get(id: Long): HotelEntity?
    @Insert suspend fun insert(h: HotelEntity): Long
    @Update suspend fun update(h: HotelEntity)
    @Query("DELETE FROM hotels WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface ItineraryDao {
    @Query("SELECT * FROM itinerary_days WHERE tripId = :tripId ORDER BY date") fun observeDays(tripId: Long): Flow<List<ItineraryDayEntity>>
    @Query("SELECT * FROM itinerary_days WHERE tripId = :tripId ORDER BY date") suspend fun getDays(tripId: Long): List<ItineraryDayEntity>
    @Query("SELECT * FROM itinerary_days WHERE id = :id") suspend fun getDay(id: Long): ItineraryDayEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDay(d: ItineraryDayEntity): Long
    @Update suspend fun updateDay(d: ItineraryDayEntity)
    @Query("DELETE FROM itinerary_days WHERE id = :id") suspend fun deleteDay(id: Long)

    @Query("SELECT * FROM itinerary_activities ORDER BY sortOrder") fun observeAllActivities(): Flow<List<ItineraryActivityEntity>>
    @Query("SELECT * FROM itinerary_activities WHERE tripId = :tripId ORDER BY sortOrder, startTime") fun observeActivities(tripId: Long): Flow<List<ItineraryActivityEntity>>
    @Query("SELECT * FROM itinerary_activities WHERE tripId = :tripId") suspend fun getActivities(tripId: Long): List<ItineraryActivityEntity>
    @Query("SELECT * FROM itinerary_activities WHERE dayId = :dayId ORDER BY sortOrder") suspend fun getActivitiesForDay(dayId: Long): List<ItineraryActivityEntity>
    @Query("SELECT * FROM itinerary_activities WHERE id = :id") suspend fun getActivity(id: Long): ItineraryActivityEntity?
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM itinerary_activities WHERE dayId = :dayId") suspend fun maxSort(dayId: Long): Int
    @Insert suspend fun insertActivity(a: ItineraryActivityEntity): Long
    @Update suspend fun updateActivity(a: ItineraryActivityEntity)
    @Update suspend fun updateActivities(a: List<ItineraryActivityEntity>)
    @Query("DELETE FROM itinerary_activities WHERE id = :id") suspend fun deleteActivity(id: Long)
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY visitDate DESC, createdAt DESC") fun observeAll(): Flow<List<PlaceEntity>>
    @Query("SELECT * FROM places WHERE tripId = :tripId ORDER BY visitDate, createdAt") fun observeForTrip(tripId: Long): Flow<List<PlaceEntity>>
    @Query("SELECT * FROM places WHERE tripId = :tripId") suspend fun getForTrip(tripId: Long): List<PlaceEntity>
    @Query("SELECT * FROM places WHERE id = :id") fun observe(id: Long): Flow<PlaceEntity?>
    @Query("SELECT * FROM places WHERE id = :id") suspend fun get(id: Long): PlaceEntity?
    @Insert suspend fun insert(p: PlaceEntity): Long
    @Update suspend fun update(p: PlaceEntity)
    @Query("DELETE FROM places WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings WHERE tripId = :tripId ORDER BY departure, createdAt") fun observeForTrip(tripId: Long): Flow<List<BookingEntity>>
    @Query("SELECT * FROM bookings WHERE tripId = :tripId") suspend fun getForTrip(tripId: Long): List<BookingEntity>
    @Query("SELECT * FROM bookings WHERE id = :id") suspend fun get(id: Long): BookingEntity?
    @Insert suspend fun insert(b: BookingEntity): Long
    @Update suspend fun update(b: BookingEntity)
    @Query("DELETE FROM bookings WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE tripId = :tripId ORDER BY name COLLATE NOCASE") fun observeForTrip(tripId: Long): Flow<List<TravelContactEntity>>
    @Query("SELECT * FROM contacts WHERE tripId = :tripId") suspend fun getForTrip(tripId: Long): List<TravelContactEntity>
    @Query("SELECT * FROM contacts WHERE id = :id") suspend fun get(id: Long): TravelContactEntity?
    @Insert suspend fun insert(c: TravelContactEntity): Long
    @Update suspend fun update(c: TravelContactEntity)
    @Query("DELETE FROM contacts WHERE id = :id") suspend fun delete(id: Long)
}
