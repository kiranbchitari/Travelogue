package com.travelogue.app.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.travelogue.app.data.db.AppDatabase
import com.travelogue.app.data.db.dao.EntryTagRow
import com.travelogue.app.data.db.entity.*
import kotlinx.coroutines.flow.Flow

class DiaryRepository(private val db: AppDatabase, private val photos: PhotoRepository) {
    private val dao = db.diaryDao()

    fun observeAll(): Flow<List<DiaryEntryEntity>> = dao.observeAll()
    fun observeForTrip(tripId: Long): Flow<List<DiaryEntryEntity>> = dao.observeForTrip(tripId)
    fun observe(id: Long): Flow<DiaryEntryEntity?> = dao.observe(id)
    fun observeEntryTags(): Flow<List<EntryTagRow>> = dao.observeEntryTags()
    suspend fun get(id: Long): DiaryEntryEntity? = dao.get(id)
    suspend fun getTags(entryId: Long): List<String> = dao.getTagsFor(entryId).map { it.name }

    /**
     * Saves an entry, replaces its tags and copies new photos (with optional captions) into storage.
     */
    suspend fun save(
        entry: DiaryEntryEntity,
        tags: List<String>,
        newPhotos: List<Uri> = emptyList(),
        captions: Map<Uri, String> = emptyMap(),
    ): Long {
        val id = db.withTransaction {
            val row = entry.copy(updatedAt = System.currentTimeMillis())
            val id = if (entry.id == 0L) dao.insert(row) else { dao.update(row); entry.id }
            dao.clearEntryTags(id)
            val tagIds = tags.map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.map { name ->
                dao.findTag(name)?.id ?: dao.insertTag(TagEntity(name = name))
            }
            dao.insertEntryTags(tagIds.map { DiaryEntryTagEntity(id, it) })
            dao.pruneTags()
            id
        }
        if (newPhotos.isNotEmpty()) photos.addPhotos(entry.tripId, newPhotos, PhotoOwner.DIARY, id, entry.dateTime, captions)
        return id
    }

    suspend fun delete(id: Long) {
        photos.deleteFor(PhotoOwner.DIARY, id)
        dao.delete(id)
        dao.pruneTags()
    }
}
