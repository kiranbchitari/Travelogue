package com.travelogue.app.data.db.dao

import androidx.room.*
import com.travelogue.app.data.db.entity.*
import kotlinx.coroutines.flow.Flow

data class EntryTagRow(val entryId: Long, val tagId: Long, val name: String)

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries ORDER BY dateTime DESC") fun observeAll(): Flow<List<DiaryEntryEntity>>
    @Query("SELECT * FROM diary_entries WHERE tripId = :tripId ORDER BY dateTime DESC") fun observeForTrip(tripId: Long): Flow<List<DiaryEntryEntity>>
    @Query("SELECT * FROM diary_entries WHERE tripId = :tripId") suspend fun getForTrip(tripId: Long): List<DiaryEntryEntity>
    @Query("SELECT * FROM diary_entries WHERE id = :id") fun observe(id: Long): Flow<DiaryEntryEntity?>
    @Query("SELECT * FROM diary_entries WHERE id = :id") suspend fun get(id: Long): DiaryEntryEntity?
    @Insert suspend fun insert(e: DiaryEntryEntity): Long
    @Update suspend fun update(e: DiaryEntryEntity)
    @Query("DELETE FROM diary_entries WHERE id = :id") suspend fun delete(id: Long)

    @Query("SELECT et.entryId, t.id AS tagId, t.name FROM diary_entry_tags et INNER JOIN tags t ON t.id = et.tagId")
    fun observeEntryTags(): Flow<List<EntryTagRow>>
    @Query("SELECT et.entryId, t.id AS tagId, t.name FROM diary_entry_tags et INNER JOIN tags t ON t.id = et.tagId WHERE et.entryId = :entryId")
    suspend fun getTagsFor(entryId: Long): List<EntryTagRow>
    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun findTag(name: String): TagEntity?
    @Insert suspend fun insertTag(t: TagEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertEntryTags(rows: List<DiaryEntryTagEntity>)
    @Query("DELETE FROM diary_entry_tags WHERE entryId = :entryId") suspend fun clearEntryTags(entryId: Long)
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM diary_entry_tags)") suspend fun pruneTags()
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM trip_photos ORDER BY takenAt DESC, id DESC") fun observeAll(): Flow<List<TripPhotoEntity>>
    @Query("SELECT * FROM trip_photos WHERE tripId = :tripId ORDER BY takenAt, sortOrder, id") fun observeForTrip(tripId: Long): Flow<List<TripPhotoEntity>>
    @Query("SELECT * FROM trip_photos WHERE tripId = :tripId") suspend fun getForTrip(tripId: Long): List<TripPhotoEntity>
    @Query("SELECT * FROM trip_photos WHERE ownerType = :type AND ownerId = :ownerId ORDER BY sortOrder, id") fun observeFor(type: PhotoOwner, ownerId: Long): Flow<List<TripPhotoEntity>>
    @Query("SELECT * FROM trip_photos WHERE ownerType = :type AND ownerId = :ownerId ORDER BY sortOrder, id") suspend fun getFor(type: PhotoOwner, ownerId: Long): List<TripPhotoEntity>
    @Query("SELECT * FROM trip_photos WHERE id = :id") suspend fun get(id: Long): TripPhotoEntity?
    @Insert suspend fun insert(p: TripPhotoEntity): Long
    @Update suspend fun update(p: TripPhotoEntity)
    @Query("DELETE FROM trip_photos WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE trip_photos SET isFavourite = :fav WHERE id = :id") suspend fun setFavourite(id: Long, fav: Boolean)
    @Query("UPDATE trip_photos SET caption = :caption WHERE id = :id") suspend fun setCaption(id: Long, caption: String)
    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM trip_photos") fun observePhotoBytes(): Flow<Long>
}
