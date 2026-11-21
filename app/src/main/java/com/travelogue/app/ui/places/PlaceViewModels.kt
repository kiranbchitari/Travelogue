package com.travelogue.app.ui.places

import com.travelogue.app.ui.common.read
import com.travelogue.app.ui.common.DraftSlot
import com.travelogue.app.ui.common.DraftHolder
import androidx.core.os.bundleOf
import android.os.Bundle
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.util.Intents
import com.travelogue.app.core.util.toAmountOrNull
import com.travelogue.app.data.db.entity.PhotoOwner
import com.travelogue.app.data.db.entity.PlaceCategory
import com.travelogue.app.data.db.entity.PlaceEntity
import com.travelogue.app.data.db.entity.TripPhotoEntity
import com.travelogue.app.data.repository.JournalRepository
import com.travelogue.app.data.repository.PhotoRepository
import com.travelogue.app.data.repository.TripRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

// ---------------------------------------------------------------------------------------------
// Map helpers (no Maps API: hand off to whatever maps app is installed)
// ---------------------------------------------------------------------------------------------

val PlaceEntity.hasMapTarget: Boolean
    get() = mapsUrl.isNotBlank() || (latitude != null && longitude != null) || address.isNotBlank()

/** Opens the best available location for a place: saved link, then coordinates, then address. */
fun openPlaceInMaps(context: Context, place: PlaceEntity) {
    when {
        place.mapsUrl.isNotBlank() -> Intents.openUrl(context, place.mapsUrl)
        place.latitude != null && place.longitude != null -> Intents.openCoordinates(context, place.latitude, place.longitude, place.name)
        place.address.isNotBlank() -> Intents.searchAddress(context, place.address)
        else -> Intents.searchAddress(context, place.name)
    }
}

// ---------------------------------------------------------------------------------------------
// Trip places tab
// ---------------------------------------------------------------------------------------------

data class PlaceItem(val place: PlaceEntity, val coverPath: String?, val photoCount: Int)

class TripPlacesViewModel(journal: JournalRepository, photos: PhotoRepository, tripId: Long) : ViewModel() {
    val places: StateFlow<List<PlaceItem>?> = combine(journal.observePlaces(tripId), photos.observeForTrip(tripId)) { places, allPhotos ->
        val byPlace = allPhotos.filter { it.ownerType == PhotoOwner.PLACE }.groupBy { it.ownerId }
        places.map { p ->
            val ph = byPlace[p.id].orEmpty().sortedBy { it.sortOrder }
            PlaceItem(p, ph.firstOrNull()?.relativePath, ph.size)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

// ---------------------------------------------------------------------------------------------
// Place detail
// ---------------------------------------------------------------------------------------------

class PlaceDetailViewModel(
    private val journal: JournalRepository,
    private val photoRepo: PhotoRepository,
    placeId: Long,
) : ViewModel() {
    /** null while loading; Result with null place once deleted / missing. */
    val place: StateFlow<Loaded<PlaceEntity?>> = journal.observePlace(placeId).map { Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Loaded(null, loading = true))

    val photos: StateFlow<List<TripPhotoEntity>> = photoRepo.observeFor(PhotoOwner.PLACE, placeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            journal.deletePlace(id)
            onDone()
        }
    }
}

data class Loaded<T>(val value: T, val loading: Boolean = false)

// ---------------------------------------------------------------------------------------------
// Place editor
// ---------------------------------------------------------------------------------------------

data class PlaceForm(
    val name: String = "",
    val address: String = "",
    val mapsUrl: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val category: PlaceCategory = PlaceCategory.ATTRACTION,
    val visitDate: LocalDate? = null,
    val description: String = "",
    val rating: Int = 0,
    val notes: String = "",
) : java.io.Serializable

data class PlaceErrors(
    val name: String? = null,
    val mapsUrl: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
) {
    val any: Boolean get() = listOfNotNull(name, mapsUrl, latitude, longitude).isNotEmpty()
}

fun PlaceForm.validate(): PlaceErrors {
    val url = mapsUrl.trim()
    val urlErr = if (url.isNotEmpty() && !(url.startsWith("http://", true) || url.startsWith("https://", true))) "Link must start with http:// or https://" else null
    val latS = latitude.trim()
    val lngS = longitude.trim()
    var latErr: String? = null
    var lngErr: String? = null
    if (latS.isNotEmpty() || lngS.isNotEmpty()) {
        val lat = latS.toDoubleOrNull()
        val lng = lngS.toDoubleOrNull()
        latErr = when {
            latS.isEmpty() -> "Enter both latitude and longitude"
            lat == null -> "Not a valid number"
            lat !in -90.0..90.0 -> "Latitude must be between -90 and 90"
            else -> null
        }
        lngErr = when {
            lngS.isEmpty() -> "Enter both latitude and longitude"
            lng == null -> "Not a valid number"
            lng !in -180.0..180.0 -> "Longitude must be between -180 and 180"
            else -> null
        }
    }
    return PlaceErrors(
        name = if (name.isBlank()) "Give this place a name" else null,
        mapsUrl = urlErr,
        latitude = latErr,
        longitude = lngErr,
    )
}

class PlaceEditorViewModel(
    private val journal: JournalRepository,
    private val photoRepo: PhotoRepository,
    private val trips: TripRepository,
    val tripId: Long,
    val placeId: Long,
) : ViewModel(), DraftHolder {
    val isNew = placeId == 0L
    var form by mutableStateOf(PlaceForm())
        private set
    var loaded by mutableStateOf(false)
        private set
    var showErrors by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set
    val pending = mutableStateListOf<Uri>()
    private var original: PlaceEntity? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** Saved photos removed in this edit. They are only deleted for real when the edit is saved. */
    private val removedSaved = kotlinx.coroutines.flow.MutableStateFlow<Set<Long>>(emptySet())

    val savedPhotos: StateFlow<List<TripPhotoEntity>> =
        (if (isNew) flowOf(emptyList()) else photoRepo.observeFor(PhotoOwner.PLACE, placeId))
            .combine(removedSaved) { list, gone -> list.filter { it.id !in gone } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val draft = DraftSlot(::applyDraft)
    override fun restoreDraft(draft: Bundle) = this.draft.offer(draft)
    override fun saveDraft(): Bundle =
        if (!draft.ready || finished) Bundle() else bundleOf("form" to form, "removedSaved" to HashSet(removedSaved.value))

    private fun applyDraft(b: Bundle) {
        form = b.read("form") ?: form
        removedSaved.value = b.read<HashSet<Long>>("removedSaved") ?: removedSaved.value
    }

    init {
        viewModelScope.launch {
            if (isNew) {
                val trip = trips.get(tripId)
                val today = LocalDate.now()
                val date = when {
                    trip == null -> today
                    today.isBefore(trip.startDate) || today.isAfter(trip.endDate) -> trip.startDate
                    else -> today
                }
                form = form.copy(visitDate = date)
            } else {
                journal.getPlace(placeId)?.let { p ->
                    original = p
                    form = PlaceForm(
                        name = p.name, address = p.address, mapsUrl = p.mapsUrl,
                        latitude = p.latitude?.toString().orEmpty(), longitude = p.longitude?.toString().orEmpty(),
                        category = p.category, visitDate = p.visitDate, description = p.description, rating = p.rating, notes = p.notes,
                    )
                }
            }
            loaded = true
            draft.ready()
        }
    }

    val errors: PlaceErrors get() = if (showErrors) form.validate() else PlaceErrors()

    fun update(transform: PlaceForm.() -> PlaceForm) { form = form.transform() }

    fun addPhotos(uris: List<Uri>) { uris.forEach { if (it !in pending) pending.add(it) } }
    fun removePending(uri: Uri) { pending.remove(uri) }
    fun removeSaved(photo: TripPhotoEntity) { removedSaved.value += photo.id }

    fun save() {
        if (saving) return
        showErrors = true
        val f = form
        if (f.validate().any) {
            _messages.tryEmit("Please fix the highlighted fields")
            return
        }
        saving = true
        viewModelScope.launch {
            try {
                val base = original ?: PlaceEntity(tripId = tripId, name = "")
                val row = base.copy(
                    name = f.name.trim(),
                    address = f.address.trim(),
                    mapsUrl = f.mapsUrl.trim(),
                    latitude = f.latitude.trim().toAmountOrNull(),
                    longitude = f.longitude.trim().toAmountOrNull(),
                    category = f.category,
                    visitDate = f.visitDate,
                    description = f.description.trim(),
                    rating = f.rating,
                    notes = f.notes.trim(),
                )
                journal.savePlace(row, pending.toList())
                removedSaved.value.forEach { photoRepo.delete(it) }
                pending.clear()
                finished = true
            } catch (e: Exception) {
                saving = false
                _messages.tryEmit(e.message ?: "Couldn't save this place")
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            journal.deletePlace(placeId)
            finished = true
        }
    }
}
