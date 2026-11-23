package com.travelogue.app.ui.trips.editor

import com.travelogue.app.ui.common.read
import com.travelogue.app.ui.common.DraftSlot
import com.travelogue.app.ui.common.DraftHolder
import androidx.core.os.bundleOf
import android.os.Bundle
import com.travelogue.app.core.designsystem.components.withDraft
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.util.toAmountOrNull
import com.travelogue.app.data.db.entity.TravelType
import com.travelogue.app.data.db.entity.TripEntity
import com.travelogue.app.data.db.entity.TripStatus
import com.travelogue.app.di.AppContainer
import com.travelogue.app.domain.CoverFrame
import com.travelogue.app.domain.coverFrame
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TripFormErrors(
    val name: String? = null,
    val destination: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val travellerCount: String? = null,
    val budget: String? = null,
) {
    val any: Boolean get() = listOf(name, destination, startDate, endDate, travellerCount, budget).any { it != null }
}

class TripEditorViewModel(
    private val c: AppContainer,
    val tripId: Long,
    /** Dates to start a new trip with, e.g. a break picked in the holiday planner. */
    presetStart: LocalDate? = null,
    presetEnd: LocalDate? = null,
) : ViewModel(), DraftHolder {
    val isNew = tripId == 0L

    var loading by mutableStateOf(!isNew); private set
    var saving by mutableStateOf(false); private set
    var notFound by mutableStateOf(false); private set
    var errors by mutableStateOf(TripFormErrors()); private set

    var name by mutableStateOf("")
    var destination by mutableStateOf("")
    var country by mutableStateOf("")
    var startDate by mutableStateOf<LocalDate?>(null); private set
    var endDate by mutableStateOf<LocalDate?>(null); private set
    var travellerCount by mutableStateOf("1")
    var travellerNames by mutableStateOf<List<String>>(emptyList()); private set
    var description by mutableStateOf("")
    var notes by mutableStateOf("")
    var status by mutableStateOf(TripStatus.PLANNING); private set
    var budget by mutableStateOf("")
    var currency by mutableStateOf("INR")
    var travelType by mutableStateOf(TravelType.PERSONAL)

    var savedCoverPath by mutableStateOf<String?>(null); private set
    var pendingCover by mutableStateOf<Uri?>(null); private set
    private var removeCover = false
    /** Which part of the cover shows on cards and headers. */
    var coverFrame by mutableStateOf(CoverFrame.Center); private set
    /** The framing screen is open (it opens by itself right after a new photo is picked). */
    var framingCover by mutableStateOf(false); private set

    /** For new trips the status follows the dates until the user picks one. */
    private var statusTouched = !isNew
    private var original: TripEntity? = null

    val travellerSuggestions: StateFlow<List<String>> = c.trips.observeAllTravellers()
        .map { list -> list.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val draft = DraftSlot(::applyDraft)
    override fun restoreDraft(draft: Bundle) = this.draft.offer(draft)
    override fun saveDraft(): Bundle = if (!draft.ready || notFound) Bundle() else bundleOf(
        "name" to name, "destination" to destination, "country" to country, "startDate" to startDate, "endDate" to endDate,
        "travellerCount" to travellerCount, "travellerNames" to ArrayList(travellerNames), "description" to description, "notes" to notes,
        "status" to status, "statusTouched" to statusTouched, "budget" to budget, "currency" to currency, "travelType" to travelType,
        "removeCover" to removeCover,
        "coverX" to coverFrame.x, "coverY" to coverFrame.y, "coverZoom" to coverFrame.zoom,
    )

    private fun applyDraft(b: Bundle) {
        name = b.read("name") ?: name; destination = b.read("destination") ?: destination; country = b.read("country") ?: country
        startDate = b.read("startDate"); endDate = b.read("endDate")
        travellerCount = b.read("travellerCount") ?: travellerCount
        travellerNames = b.read<ArrayList<String>>("travellerNames") ?: travellerNames
        description = b.read("description") ?: description; notes = b.read("notes") ?: notes
        status = b.read("status") ?: status; statusTouched = b.getBoolean("statusTouched", statusTouched)
        budget = b.read("budget") ?: budget; currency = b.read("currency") ?: currency; travelType = b.read("travelType") ?: travelType
        if (b.getBoolean("removeCover")) { removeCover = true; savedCoverPath = null }
        if (b.containsKey("coverX")) coverFrame = CoverFrame.of(b.getFloat("coverX"), b.getFloat("coverY"), b.getFloat("coverZoom"))
    }

    init {
        viewModelScope.launch {
            if (isNew) {
                currency = c.settings.current().defaultCurrency
                presetStart?.let(::setStart)
                presetEnd?.takeIf { presetStart == null || !it.isBefore(presetStart) }?.let(::setEnd)
            } else {
                val t = c.trips.get(tripId)
                if (t == null) {
                    notFound = true
                } else {
                    original = t
                    name = t.name
                    destination = t.destination
                    country = t.country
                    startDate = t.startDate
                    endDate = t.endDate
                    travellerCount = t.travellerCount.toString()
                    description = t.description
                    notes = t.notes
                    status = t.status
                    budget = if (t.budget > 0) com.travelogue.app.ui.expenses.plainNumber(t.budget) else ""
                    currency = t.currency
                    travelType = t.travelType
                    savedCoverPath = t.coverPath
                    coverFrame = t.coverFrame
                    travellerNames = c.trips.getTravellers(tripId).map { it.name }
                }
            }
            loading = false
            draft.ready()
        }
    }

    fun setStart(d: LocalDate) {
        startDate = d
        if (endDate == null || endDate!!.isBefore(d)) endDate = d
        errors = errors.copy(startDate = null, endDate = null)
        autoStatus()
    }

    fun setEnd(d: LocalDate) {
        endDate = d
        if (startDate == null) startDate = d
        errors = errors.copy(endDate = null)
        autoStatus()
    }

    private fun autoStatus() {
        val s = startDate ?: return
        val e = endDate ?: return
        if (!statusTouched) status = c.trips.statusFor(s, e)
    }

    fun pickStatus(s: TripStatus) {
        statusTouched = true
        status = s
    }

    /** A name typed but not yet added as a chip; committed when the trip is saved. */
    var travellerDraft = ""

    fun setTravellers(names: List<String>) {
        travellerNames = names
        val count = travellerCount.toIntOrNull() ?: 0
        if (names.size > count) travellerCount = names.size.toString()
        errors = errors.copy(travellerCount = null)
    }

    fun pickCover(uri: Uri) {
        pendingCover = uri
        removeCover = false
        // A new photo starts centred, and the user is asked straight away what should show.
        coverFrame = CoverFrame.Center
        framingCover = true
    }

    fun clearCover() {
        pendingCover = null
        removeCover = savedCoverPath != null
        savedCoverPath = null
        coverFrame = CoverFrame.Center
        framingCover = false
    }

    fun openFraming() { if (pendingCover != null || savedCoverPath != null) framingCover = true }
    fun closeFraming() { framingCover = false }

    fun setFrame(frame: CoverFrame) {
        coverFrame = frame
        framingCover = false
    }

    private fun validate(): Boolean {
        val count = travellerCount.trim().toIntOrNull()
        val budgetValue = budget.toAmountOrNull()
        errors = TripFormErrors(
            name = if (name.isBlank()) "Give your trip a name" else null,
            destination = if (destination.isBlank()) "Where are you going?" else null,
            startDate = if (startDate == null) "Pick a start date" else null,
            endDate = when {
                endDate == null -> "Pick an end date"
                startDate != null && endDate!!.isBefore(startDate) -> "End date can't be before the start date"
                startDate != null && endDate!!.toEpochDay() - startDate!!.toEpochDay() > 365 -> "Trips can be at most a year long"
                else -> null
            },
            travellerCount = when {
                count == null || count < 1 -> "Enter at least 1 traveller"
                count > 100 -> "That's a lot of travellers — max 100"
                count < travellerNames.size -> "At least ${travellerNames.size} (names added)"
                else -> null
            },
            budget = if (budget.isNotBlank() && (budgetValue == null || budgetValue < 0)) "Enter a valid amount" else null,
        )
        return !errors.any
    }

    fun save(onSaved: (id: Long, created: Boolean) -> Unit, onError: (String) -> Unit) {
        if (saving) return
        // A name still in the box counts: commit it first so validation and the count see it.
        if (travellerNames.withDraft(travellerDraft) != travellerNames) setTravellers(travellerNames.withDraft(travellerDraft))
        if (!validate()) { onError("Please fix the highlighted fields"); return }
        saving = true
        viewModelScope.launch {
            try {
                val base = original
                val entity = TripEntity(
                    id = base?.id ?: 0,
                    uuid = base?.uuid ?: "",
                    name = name.trim(),
                    destination = destination.trim(),
                    country = country.trim(),
                    startDate = startDate!!,
                    endDate = endDate!!,
                    travellerCount = travellerCount.trim().toInt(),
                    description = description.trim(),
                    coverPath = base?.coverPath,
                    coverFocusX = coverFrame.x,
                    coverFocusY = coverFrame.y,
                    coverZoom = coverFrame.zoom,
                    status = status,
                    budget = budget.toAmountOrNull() ?: 0.0,
                    currency = currency.uppercase(),
                    travelType = travelType,
                    notes = notes.trim(),
                    archived = base?.archived ?: false,
                    createdAt = base?.createdAt ?: System.currentTimeMillis(),
                )
                val id = c.trips.save(entity, travellerNames, newCover = pendingCover, removeCover = removeCover)
                onSaved(id, isNew)
            } catch (e: Exception) {
                saving = false
                onError(e.message ?: "Couldn't save the trip")
            }
        }
    }
}
