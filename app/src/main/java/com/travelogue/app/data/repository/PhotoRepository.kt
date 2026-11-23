package com.travelogue.app.data.repository

import android.net.Uri
import com.travelogue.app.core.storage.MediaStorage
import com.travelogue.app.data.db.AppDatabase
import com.travelogue.app.data.db.entity.PhotoOwner
import com.travelogue.app.data.db.entity.TripPhotoEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/** All TripPhoto operations. Files are copied into app storage before the row is written. */
class PhotoRepository(private val db: AppDatabase, private val media: MediaStorage) {
    private val dao = db.photoDao()

    fun observeAll(): Flow<List<TripPhotoEntity>> = dao.observeAll()
    fun observeForTrip(tripId: Long): Flow<List<TripPhotoEntity>> = dao.observeForTrip(tripId)
    fun observeFor(owner: PhotoOwner, ownerId: Long): Flow<List<TripPhotoEntity>> = dao.observeFor(owner, ownerId)
    suspend fun getFor(owner: PhotoOwner, ownerId: Long): List<TripPhotoEntity> = dao.getFor(owner, ownerId)

    /** Copies each picked image into app storage and records it. Returns the new photo ids. */
    suspend fun addPhotos(
        tripId: Long,
        uris: List<Uri>,
        owner: PhotoOwner = PhotoOwner.GENERAL,
        ownerId: Long? = null,
        takenAt: LocalDateTime = LocalDateTime.now(),
        captions: Map<Uri, String> = emptyMap(),
    ): List<Long> {
        val trip = db.tripDao().get(tripId) ?: error("Trip not found")
        val start = dao.getForTrip(tripId).size
        return uris.mapIndexed { i, uri ->
            val stored = media.importImage(uri, trip.uuid, owner.folder)
            media.recordOrDiscard(stored.relativePath) {
                dao.insert(
                    TripPhotoEntity(
                        tripId = tripId, ownerType = owner, ownerId = ownerId, relativePath = stored.relativePath,
                        caption = captions[uri].orEmpty(), takenAt = takenAt, width = stored.width, height = stored.height,
                        sizeBytes = stored.sizeBytes, sortOrder = start + i,
                    ),
                )
            }
        }
    }

    /** Records a file that is already inside app storage (camera capture). */
    suspend fun addStoredPhoto(tripId: Long, relativePath: String, owner: PhotoOwner, ownerId: Long?, takenAt: LocalDateTime): Long {
        val f = media.file(relativePath)
        return dao.insert(
            TripPhotoEntity(tripId = tripId, ownerType = owner, ownerId = ownerId, relativePath = relativePath, takenAt = takenAt, sizeBytes = f.length()),
        )
    }

    suspend fun setFavourite(id: Long, fav: Boolean) = dao.setFavourite(id, fav)
    suspend fun setCaption(id: Long, caption: String) = dao.setCaption(id, caption.trim())
    suspend fun update(photo: TripPhotoEntity) = dao.update(photo)

    suspend fun delete(id: Long) {
        val p = dao.get(id) ?: return
        dao.delete(id)
        media.delete(p.relativePath)
    }

    suspend fun deleteFor(owner: PhotoOwner, ownerId: Long) {
        dao.getFor(owner, ownerId).forEach { delete(it.id) }
    }

    /** Re-attaches photos created before their owner row existed (e.g. while composing a new entry). */
    suspend fun reassign(photoIds: List<Long>, owner: PhotoOwner, ownerId: Long) {
        photoIds.forEach { pid -> dao.get(pid)?.let { dao.update(it.copy(ownerType = owner, ownerId = ownerId)) } }
    }
}
