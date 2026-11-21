package com.travelogue.app.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.data.db.entity.TripStatus
import com.travelogue.app.di.AppContainer
import kotlinx.coroutines.flow.*
import java.time.LocalDate

enum class TripFilter(val label: String) {
    ALL("All"), PLANNING("Planning"), UPCOMING("Upcoming"), ONGOING("Ongoing"), COMPLETED("Completed"), ARCHIVED("Archived")
}

data class TripsState(
    val loaded: Boolean = false,
    val totalCount: Int = 0,
    val trips: List<TripSummary> = emptyList(),
    val query: String = "",
    val filter: TripFilter = TripFilter.ALL,
)

class TripsViewModel(c: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(TripFilter.ALL)

    private val summaries = combine(c.trips.observeTrips(), c.expenses.observeAll(), c.trips.observeAllRates()) { t, e, r ->
        buildTripSummaries(t, e, r)
    }

    val state: StateFlow<TripsState> = combine(summaries, query, filter) { all, q, f ->
        val today = LocalDate.now()
        val needle = q.trim()
        val list = all.filter { s ->
            val t = s.trip
            val matchesFilter = when (f) {
                TripFilter.ALL -> !t.archived
                TripFilter.ARCHIVED -> t.archived
                TripFilter.COMPLETED -> !t.archived && t.isPast(today)
                TripFilter.ONGOING -> !t.archived && !t.isPast(today) && (t.status == TripStatus.ONGOING || t.isOngoing(today))
                TripFilter.UPCOMING -> !t.archived && !t.isPast(today) && t.status == TripStatus.UPCOMING && !t.isOngoing(today)
                TripFilter.PLANNING -> !t.archived && t.status == TripStatus.PLANNING && !t.isPast(today)
            }
            val matchesQuery = needle.isEmpty() || listOf(t.name, t.destination, t.country, t.description).any { it.contains(needle, true) }
            matchesFilter && matchesQuery
        }.sortedWith(
            // Upcoming/ongoing first (soonest first), then past trips (most recent first).
            compareBy<TripSummary> { it.trip.isPast(today) }
                .thenBy { if (it.trip.isPast(today)) -it.trip.endDate.toEpochDay() else it.trip.startDate.toEpochDay() },
        )
        TripsState(loaded = true, totalCount = all.size, trips = list, query = q, filter = f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripsState())

    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: TripFilter) { filter.value = f }
}
