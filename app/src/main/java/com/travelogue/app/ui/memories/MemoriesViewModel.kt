package com.travelogue.app.ui.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.data.db.entity.PhotoOwner
import com.travelogue.app.data.db.entity.TripEntity
import com.travelogue.app.data.db.entity.TripPhotoEntity
import com.travelogue.app.data.repository.DiaryRepository
import com.travelogue.app.data.repository.PhotoRepository
import com.travelogue.app.data.repository.TripRepository
import com.travelogue.app.ui.diary.DiaryCardModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.time.YearMonth

data class TripGalleryCard(
    val trip: TripEntity,
    val photoCount: Int,
    val favouriteCount: Int,
    val coverPath: String?,
    val preview: List<TripPhotoEntity>,
)

data class MonthGroup(val month: YearMonth, val photos: List<TripPhotoEntity>)

data class MemoriesState(
    val moments: List<MonthGroup> = emptyList(),
    val favourites: List<TripPhotoEntity> = emptyList(),
    val galleries: List<TripGalleryCard> = emptyList(),
    val journal: List<DiaryCardModel> = emptyList(),
    val photoCount: Int = 0,
    val loaded: Boolean = false,
)

class MemoriesViewModel(trips: TripRepository, diary: DiaryRepository, photos: PhotoRepository) : ViewModel() {
    val state: StateFlow<MemoriesState> = combine(
        photos.observeAll(),
        trips.observeTrips(),
        diary.observeAll(),
        diary.observeEntryTags(),
    ) { all, tripList, entries, tags ->
        val tripsById = tripList.associateBy { it.id }
        val byTrip = all.groupBy { it.tripId }
        val diaryPhotos = all.filter { it.ownerType == PhotoOwner.DIARY }.groupBy { it.ownerId }
        val tagsBy = tags.groupBy({ it.entryId }, { it.name })
        MemoriesState(
            moments = all.groupBy { YearMonth.from(it.takenAt) }.map { MonthGroup(it.key, it.value) }.sortedByDescending { it.month },
            favourites = all.filter { it.isFavourite },
            galleries = tripList.filter { !it.archived || byTrip[it.id].orEmpty().isNotEmpty() }.map { t ->
                val ph = byTrip[t.id].orEmpty()
                TripGalleryCard(
                    trip = t,
                    photoCount = ph.size,
                    favouriteCount = ph.count { it.isFavourite },
                    coverPath = t.coverPath ?: ph.firstOrNull { it.isFavourite }?.relativePath ?: ph.firstOrNull()?.relativePath,
                    preview = (ph.filter { it.isFavourite } + ph).distinctBy { it.id }.take(3),
                )
            }.sortedWith(compareByDescending<TripGalleryCard> { it.photoCount > 0 }.thenByDescending { it.trip.startDate }),
            journal = entries.take(60).map { e ->
                DiaryCardModel(e, diaryPhotos[e.id].orEmpty(), tagsBy[e.id].orEmpty(), tripsById[e.tripId]?.name)
            },
            photoCount = all.size,
            loaded = true,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MemoriesState())
}
