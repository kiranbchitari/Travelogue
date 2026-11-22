package com.travelogue.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.data.db.entity.*
import com.travelogue.app.di.AppContainer
import com.travelogue.app.ui.trips.TripSummary
import com.travelogue.app.ui.trips.buildTripSummaries
import com.travelogue.app.ui.trips.isPast
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Home is about the journeys themselves; spending lives on the Expenses tab. */
data class HomeState(
    val loaded: Boolean = false,
    val tripCount: Int = 0,
    val placesCount: Int = 0,
    val travelDays: Int = 0,
    val upcoming: List<TripSummary> = emptyList(),
    val recent: List<TripSummary> = emptyList(),
    val recentPhotos: List<TripPhotoEntity> = emptyList(),
)

class HomeViewModel(c: AppContainer) : ViewModel() {

    val state: StateFlow<HomeState> = combine(
        c.trips.observeTrips(), c.expenses.observeAll(), c.trips.observeAllRates(), c.journal.observeAllPlaces(), c.photos.observeAll(), ::build,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    private fun build(
        trips: List<TripEntity>,
        expenses: List<ExpenseEntity>,
        rates: List<CurrencyRateEntity>,
        places: List<PlaceEntity>,
        photos: List<TripPhotoEntity>,
    ): HomeState {
        val today = LocalDate.now()
        // Expenses and rates only feed the spend-against-budget line on each trip card.
        val summaries = buildTripSummaries(trips, expenses, rates)

        val travelDays = trips.filter { !it.startDate.isAfter(today) }.sumOf { t ->
            val end = if (t.endDate.isAfter(today)) today else t.endDate
            (end.toEpochDay() - t.startDate.toEpochDay() + 1).toInt().coerceAtLeast(0)
        }

        val visible = summaries.filter { !it.trip.archived }
        return HomeState(
            loaded = true,
            tripCount = trips.size,
            placesCount = places.size,
            travelDays = travelDays,
            upcoming = visible.filter { !it.trip.isPast(today) }.sortedBy { it.trip.startDate },
            recent = visible.filter { it.trip.isPast(today) }.sortedByDescending { it.trip.endDate }.take(5),
            recentPhotos = photos.sortedByDescending { it.createdAt }.take(12),
        )
    }
}
