package com.travelogue.app.ui.diary

import com.travelogue.app.ui.common.read
import com.travelogue.app.ui.common.DraftSlot
import com.travelogue.app.ui.common.DraftHolder
import androidx.core.os.bundleOf
import android.os.Bundle
import com.travelogue.app.core.designsystem.components.withDraft
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.storage.StorageFullException
import com.travelogue.app.data.db.entity.*
import com.travelogue.app.data.repository.DiaryRepository
import com.travelogue.app.data.repository.PhotoRepository
import com.travelogue.app.data.repository.TripRepository
import com.travelogue.app.ui.memories.defaultTripDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** A diary entry with everything a card needs. */
data class DiaryCardModel(
    val entry: DiaryEntryEntity,
    val photos: List<TripPhotoEntity>,
    val tags: List<String>,
    val tripName: String? = null,
)

// ---------------------------------------------------------------------------------------------
// Trip diary tab
// ---------------------------------------------------------------------------------------------

data class TripDiaryState(
    val trip: TripEntity? = null,
    val entries: List<DiaryCardModel> = emptyList(),
    val photoCount: Int = 0,
    val loaded: Boolean = false,
)

class TripDiaryViewModel(
    trips: TripRepository,
    diary: DiaryRepository,
    photos: PhotoRepository,
    tripId: Long,
) : ViewModel() {
    val state: StateFlow<TripDiaryState> = combine(
        trips.observeTrip(tripId),
        diary.observeForTrip(tripId),
        photos.observeForTrip(tripId),
        diary.observeEntryTags(),
    ) { trip, entries, ph, tags ->
        val byEntry = ph.filter { it.ownerType == PhotoOwner.DIARY }.groupBy { it.ownerId }
        val tagsBy = tags.groupBy({ it.entryId }, { it.name })
        TripDiaryState(
            trip = trip,
            entries = entries.map { DiaryCardModel(it, byEntry[it.id].orEmpty(), tagsBy[it.id].orEmpty()) },
            photoCount = ph.size,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripDiaryState())
}

// ---------------------------------------------------------------------------------------------
// Diary editor
// ---------------------------------------------------------------------------------------------

class DiaryEditorViewModel(
    private val trips: TripRepository,
    private val diary: DiaryRepository,
    private val photos: PhotoRepository,
    val tripId: Long,
    val entryId: Long,
) : ViewModel(), DraftHolder {
    val isNew = entryId == 0L

    var loading by mutableStateOf(true); private set
    var saving by mutableStateOf(false); private set
    var dirty by mutableStateOf(false); private set
    var finished by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var titleError by mutableStateOf<String?>(null); private set
    var dateError by mutableStateOf<String?>(null); private set

    var title by mutableStateOf(""); private set
    var body by mutableStateOf(""); private set
    var date by mutableStateOf<LocalDate?>(null); private set
    var time by mutableStateOf<LocalTime?>(null); private set
    var location by mutableStateOf(""); private set
    var mood by mutableStateOf<Mood?>(null); private set
    var tags by mutableStateOf<List<String>>(emptyList()); private set

    val pending = mutableStateListOf<Uri>()
    val pendingCaptions = mutableStateMapOf<Uri, String>()
    val savedCaptionEdits = mutableStateMapOf<Long, String>()
    val removedSaved = mutableStateListOf<Long>()

    var trip by mutableStateOf<TripEntity?>(null); private set
    private var existing: DiaryEntryEntity? = null

    val savedPhotos: StateFlow<List<TripPhotoEntity>> =
        (if (isNew) flowOf(emptyList()) else photos.observeFor(PhotoOwner.DIARY, entryId))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Existing tags ranked by how often they are used. */
    val tagSuggestions: StateFlow<List<String>> = diary.observeEntryTags()
        .map { rows -> rows.groupingBy { it.name }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val draft = DraftSlot(::applyDraft)
    override fun restoreDraft(draft: Bundle) = this.draft.offer(draft)
    override fun saveDraft(): Bundle = if (!draft.ready || finished) Bundle() else bundleOf(
        "title" to title, "body" to body, "date" to date, "time" to time, "location" to location, "mood" to mood,
        "tags" to ArrayList(tags), "removedSaved" to ArrayList(removedSaved), "captions" to HashMap(savedCaptionEdits), "dirty" to dirty,
    )

    private fun applyDraft(b: Bundle) {
        title = b.read("title") ?: title; body = b.read("body") ?: body; date = b.read("date"); time = b.read("time")
        location = b.read("location") ?: location; mood = b.read("mood")
        tags = b.read<ArrayList<String>>("tags") ?: tags
        b.read<ArrayList<Long>>("removedSaved")?.let { ids -> removedSaved.clear(); removedSaved.addAll(ids) }
        b.read<HashMap<Long, String>>("captions")?.let { savedCaptionEdits.putAll(it) }
        dirty = b.getBoolean("dirty", dirty)
    }

    init {
        viewModelScope.launch {
            val t = trips.get(tripId)
            trip = t
            if (!isNew) {
                val e = diary.get(entryId)
                if (e != null) {
                    existing = e
                    title = e.title
                    body = e.body
                    date = e.dateTime.toLocalDate()
                    time = e.dateTime.toLocalTime()
                    location = e.location
                    mood = e.mood
                    tags = diary.getTags(entryId)
                } else {
                    message = "This entry no longer exists"
                    finished = true
                }
            } else {
                date = defaultTripDate(t)
                time = LocalTime.now().withSecond(0).withNano(0)
            }
            loading = false
            draft.ready()
        }
    }

    fun updateTitle(v: String) { title = v; titleError = null; dirty = true }
    fun updateBody(v: String) { body = v; dirty = true }
    fun updateDate(v: LocalDate) { date = v; dateError = null; dirty = true }
    fun updateTime(v: LocalTime?) { time = v; dirty = true }
    fun updateLocation(v: String) { location = v; dirty = true }
    fun updateMood(v: Mood?) { mood = v; dirty = true }
    fun updateTags(v: List<String>) { tags = v; dirty = true }

    /** Tag text typed but not yet added as a chip; included on save. */
    private var tagDraft = ""
    fun updateTagDraft(v: String) { tagDraft = v; if (v.isNotEmpty()) dirty = true }

    fun addPhotos(uris: List<Uri>) {
        uris.filterNot { it in pending }.forEach { pending += it }
        dirty = true
    }

    fun removePending(uri: Uri) { pending -= uri; pendingCaptions -= uri; dirty = true }
    fun removeSaved(photo: TripPhotoEntity) { removedSaved += photo.id; dirty = true }
    fun setPendingCaption(uri: Uri, caption: String) { pendingCaptions[uri] = caption; dirty = true }
    fun setSavedCaption(id: Long, caption: String) { savedCaptionEdits[id] = caption; dirty = true }

    fun save() {
        if (saving) return
        val d = date
        var ok = true
        if (title.isBlank()) { titleError = "Give this memory a title"; ok = false }
        else if (title.length > 120) { titleError = "Keep the title under 120 characters"; ok = false }
        if (d == null) { dateError = "Pick a date"; ok = false }
        if (!ok || d == null) return
        saving = true
        viewModelScope.launch {
            try {
                val dateTime = d.atTime(time ?: LocalTime.NOON)
                val base = existing ?: DiaryEntryEntity(tripId = tripId, title = "", dateTime = dateTime)
                val entry = base.copy(
                    title = title.trim(),
                    body = body.trimEnd(),
                    dateTime = dateTime,
                    location = location.trim(),
                    mood = mood,
                )
                diary.save(entry, tags.withDraft(tagDraft), pending.toList(), pendingCaptions.filterValues { it.isNotBlank() }.mapValues { it.value.trim() })
                removedSaved.forEach { photos.delete(it) }
                savedCaptionEdits.filterKeys { it !in removedSaved }.forEach { (id, c) -> photos.setCaption(id, c) }
                dirty = false
                finished = true
            } catch (e: StorageFullException) {
                saving = false
                message = e.message
            } catch (e: Exception) {
                saving = false
                message = "Couldn't save the entry: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun delete() {
        if (isNew) { finished = true; return }
        viewModelScope.launch {
            runCatching { diary.delete(entryId) }
                .onSuccess { dirty = false; finished = true }
                .onFailure { message = "Couldn't delete: ${it.message}" }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Diary detail
// ---------------------------------------------------------------------------------------------

data class DiaryDetailState(
    val entry: DiaryEntryEntity? = null,
    val trip: TripEntity? = null,
    val photos: List<TripPhotoEntity> = emptyList(),
    val tags: List<String> = emptyList(),
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryDetailViewModel(
    trips: TripRepository,
    private val diary: DiaryRepository,
    photos: PhotoRepository,
    private val entryId: Long,
) : ViewModel() {
    val state: StateFlow<DiaryDetailState> = diary.observe(entryId).flatMapLatest { e ->
        if (e == null) flowOf(DiaryDetailState(loaded = true))
        else combine(
            trips.observeTrip(e.tripId),
            photos.observeFor(PhotoOwner.DIARY, entryId),
            diary.observeEntryTags(),
        ) { t, p, tags -> DiaryDetailState(e, t, p, tags.filter { it.entryId == entryId }.map { it.name }, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DiaryDetailState())

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { diary.delete(entryId) }
            onDone()
        }
    }
}
