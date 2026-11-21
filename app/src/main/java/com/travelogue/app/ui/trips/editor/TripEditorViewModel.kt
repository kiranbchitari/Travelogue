package com.travelogue.app.ui.trips.editor

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

class TripEditorViewModel(private val c: AppContainer, val tripId: Long) : ViewModel() {
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

    /** For new trips the status follows the dates until the user picks one. */
    private var statusTouched = !isNew
    private var original: TripEntity? = null

    val travellerSuggestions: StateFlow<List<String>> = c.trips.observeAllTravellers()
        .map { list -> list.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            if (isNew) {
                currency = c.settings.current().defaultCurrency
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
                    budget = if (t.budget > 0) com.travelogue.app.core.util.Fmt.number(t.budget) else ""
                    currency = t.currency
                    travelType = t.travelType
                    savedCoverPath = t.coverPath
                    travellerNames = c.trips.getTravellers(tripId).map { it.name }
                }
            }
            loading = false
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

    fun setTravellers(names: List<String>) {
        travellerNames = names
        val count = travellerCount.toIntOrNull() ?: 0
        if (names.size > count) travellerCount = names.size.toString()
        errors = errors.copy(travellerCount = null)
    }

    fun pickCover(uri: Uri) {
        pendingCover = uri
        removeCover = false
    }

    fun clearCover() {
        pendingCover = null
        removeCover = savedCoverPath != null
        savedCoverPath = null
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
        if (saving || !validate()) return
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
                onError(e.message ?: "Couldn't save the trip")
            } finally {
                saving = false
            }
        }
    }
}
