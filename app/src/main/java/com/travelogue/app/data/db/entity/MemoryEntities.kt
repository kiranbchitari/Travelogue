@file:UseSerializers(LocalDateSerializer::class, LocalTimeSerializer::class, LocalDateTimeSerializer::class)

package com.travelogue.app.data.db.entity

import com.travelogue.app.data.db.LocalDateSerializer
import com.travelogue.app.data.db.LocalDateTimeSerializer
import com.travelogue.app.data.db.LocalTimeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "diary_entries",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId"), Index("dateTime")],
)
@Serializable
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val title: String,
    val body: String = "",
    val dateTime: LocalDateTime,
    val location: String = "",
    val mood: Mood? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
@Serializable
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "diary_entry_tags",
    primaryKeys = ["entryId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = DiaryEntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
@Serializable
data class DiaryEntryTagEntity(val entryId: Long, val tagId: Long)

/**
 * A photograph stored in app-private storage. Diary, hotel, place and gallery photos all live here so
 * the trip gallery is a single query. [ownerId] points at the owning row for [ownerType].
 */
@Entity(
    tableName = "trip_photos",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId"), Index(value = ["ownerType", "ownerId"]), Index("isFavourite")],
)
@Serializable
data class TripPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val ownerType: PhotoOwner = PhotoOwner.GENERAL,
    val ownerId: Long? = null,
    val relativePath: String,
    val caption: String = "",
    /** When the photo was taken / the date it belongs to in the story. */
    val takenAt: LocalDateTime,
    val isFavourite: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
