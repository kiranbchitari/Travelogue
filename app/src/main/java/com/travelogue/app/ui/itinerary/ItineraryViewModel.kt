package com.travelogue.app.ui.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.data.db.entity.ActivityStatus
import com.travelogue.app.data.db.entity.ItineraryActivityEntity
import com.travelogue.app.data.db.entity.ItineraryDayEntity
import com.travelogue.app.data.db.entity.TripEntity
import com.travelogue.app.data.repository.JournalRepository
import com.travelogue.app.data.repository.TripRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ItineraryState(
    val loading: Boolean = true,
    val trip: TripEntity? = null,
    val days: List<ItineraryDayEntity> = emptyList(),
    val activitiesByDay: Map<Long, List<ItineraryActivityEntity>> = emptyMap(),
) {
    fun activities(dayId: Long): List<ItineraryActivityEntity> = activitiesByDay[dayId].orEmpty()
    fun dayNumber(day: ItineraryDayEntity): Int = days.indexOfFirst { it.id == day.id } + 1
}

class ItineraryViewModel(
    trips: TripRepository,
    private val journal: JournalRepository,
    val tripId: Long,
) : ViewModel() {
    private val messages = Channel<String>(Channel.BUFFERED)
    val message = messages.receiveAsFlow()

    val state: StateFlow<ItineraryState> = combine(
        trips.observeTrip(tripId),
        journal.observeDays(tripId),
        journal.observeActivities(tripId),
    ) { trip, days, acts ->
        ItineraryState(
            loading = false,
            trip = trip,
            days = days.sortedBy { it.date },
            activitiesByDay = acts.groupBy { it.dayId }.mapValues { (_, list) -> list.sortedBy { it.sortOrder } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ItineraryState())

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            messages.send(e.message ?: "Something went wrong")
        }
    }

    fun setStatus(id: Long, status: ActivityStatus) = launch { journal.setActivityStatus(id, status) }

    fun toggleCompleted(activity: ItineraryActivityEntity) = launch {
        val next = if (activity.status == ActivityStatus.COMPLETED) ActivityStatus.PLANNED else ActivityStatus.COMPLETED
        journal.setActivityStatus(activity.id, next)
    }

    fun nudge(id: Long, delta: Int) = launch { journal.nudgeActivity(id, delta) }

    /** Persists a drag-and-drop order for one day. */
    fun reorder(dayId: Long, orderedIds: List<Long>) = launch { journal.reorderActivities(dayId, orderedIds) }

    fun move(id: Long, toDayId: Long, dayLabel: String) = launch {
        journal.moveActivity(id, toDayId)
        messages.send("Moved to $dayLabel")
    }

    fun delete(id: Long) = launch { journal.deleteActivity(id) }

    /** Adds a day after the last one (or on the trip start if there are none). */
    fun addDay(onAdded: (Long) -> Unit) = launch {
        val s = state.value
        val last = s.days.maxOfOrNull { it.date }
        val date = last?.plusDays(1) ?: s.trip?.startDate ?: LocalDate.now()
        val id = journal.addDay(tripId, date)
        if (id > 0) onAdded(id) else messages.send("That day already exists")
    }

    fun deleteDay(id: Long) = launch { journal.deleteDay(id) }

    fun updateDay(day: ItineraryDayEntity) = launch { journal.updateDay(day) }
}
