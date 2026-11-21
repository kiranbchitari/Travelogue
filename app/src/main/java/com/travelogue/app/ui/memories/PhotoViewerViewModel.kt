package com.travelogue.app.ui.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.data.db.entity.PhotoOwner
import com.travelogue.app.data.db.entity.TripPhotoEntity
import com.travelogue.app.data.repository.DiaryRepository
import com.travelogue.app.data.repository.JournalRepository
import com.travelogue.app.data.repository.PhotoRepository
import com.travelogue.app.data.repository.TripRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ViewerPhoto(
    val photo: TripPhotoEntity,
    val tripName: String,
    /** e.g. "Diary entry · Sunrise at Tiger Hill" */
    val source: String,
)

class PhotoViewerViewModel(
    trips: TripRepository,
    diary: DiaryRepository,
    journal: JournalRepository,
    private val photos: PhotoRepository,
    tripId: Long,
    favouritesOnly: Boolean,
) : ViewModel() {
    /**
     * The set of photos is frozen when the viewer opens, so un-hearting a photo in the favourites
     * view doesn't yank it out from under the pager. Deleted photos still disappear.
     */
    private var frozenIds: List<Long>? = null

    val photosState: StateFlow<List<ViewerPhoto>?> = combine(
        if (tripId == 0L) photos.observeAll() else photos.observeForTrip(tripId),
        trips.observeTrips(),
        diary.observeAll(),
        journal.observeAllHotels(),
        journal.observeAllPlaces(),
    ) { list, tripList, entries, hotels, places ->
        val ids = frozenIds ?: (if (favouritesOnly) list.filter { it.isFavourite } else list).map { it.id }.also { frozenIds = it }
        val byId = list.associateBy { it.id }
        val tripNames = tripList.associate { it.id to it.name }
        val entryTitles = entries.associate { it.id to it.title }
        val hotelNames = hotels.associate { it.id to it.name }
        val placeNames = places.associate { it.id to it.name }
        ids.mapNotNull { byId[it] }.map { p ->
            val name = when (p.ownerType) {
                PhotoOwner.DIARY -> entryTitles[p.ownerId]
                PhotoOwner.HOTEL -> hotelNames[p.ownerId]
                PhotoOwner.PLACE -> placeNames[p.ownerId]
                else -> null
            }
            ViewerPhoto(p, tripNames[p.tripId].orEmpty(), listOfNotNull(p.ownerType.sourceLabel(), name).joinToString(" · "))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun toggleFavourite(p: TripPhotoEntity) = viewModelScope.launch { photos.setFavourite(p.id, !p.isFavourite) }
    fun setCaption(id: Long, caption: String) = viewModelScope.launch { photos.setCaption(id, caption) }
    fun delete(id: Long) = viewModelScope.launch { photos.delete(id) }
}
