package com.travelogue.app.ui.trips.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.data.db.entity.*
import com.travelogue.app.di.AppContainer
import com.travelogue.app.domain.Analytics
import com.travelogue.app.domain.CurrencyConverter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripDetailState(
    val loaded: Boolean = false,
    val trip: TripEntity? = null,
    val travellers: List<TravellerEntity> = emptyList(),
    val total: Double = 0.0,
    val unconvertedCurrencies: Set<String> = emptySet(),
    val expenseCount: Int = 0,
    val recentExpenses: List<ExpenseEntity> = emptyList(),
    val categories: Map<Long, ExpenseCategoryEntity> = emptyMap(),
    val hotels: List<HotelEntity> = emptyList(),
    val placesCount: Int = 0,
    val photoCount: Int = 0,
    val recentDiary: List<DiaryEntryEntity> = emptyList(),
    val diaryCount: Int = 0,
)

private data class Money(
    val trip: TripEntity?,
    val expenses: List<ExpenseEntity>,
    val rates: List<CurrencyRateEntity>,
    val categories: List<ExpenseCategoryEntity>,
    val travellers: List<TravellerEntity>,
)

private data class Journal(
    val hotels: List<HotelEntity>,
    val places: List<PlaceEntity>,
    val photos: List<TripPhotoEntity>,
    val diary: List<DiaryEntryEntity>,
)

class TripDetailViewModel(private val c: AppContainer, val tripId: Long) : ViewModel() {

    val state: StateFlow<TripDetailState> = combine(
        combine(
            c.trips.observeTrip(tripId), c.expenses.observeForTrip(tripId), c.trips.observeRates(tripId),
            c.expenses.observeCategories(), c.trips.observeTravellers(tripId), ::Money,
        ),
        combine(
            c.journal.observeHotels(tripId), c.journal.observePlaces(tripId), c.photos.observeForTrip(tripId),
            c.diary.observeForTrip(tripId), ::Journal,
        ),
    ) { m, j ->
        val trip = m.trip
        if (trip == null) {
            TripDetailState(loaded = true)
        } else {
            val conv = CurrencyConverter(trip.currency, m.rates.associate { it.currency to it.rate })
            TripDetailState(
                loaded = true,
                trip = trip,
                travellers = m.travellers,
                total = Analytics.tripTotal(m.expenses, conv),
                unconvertedCurrencies = m.expenses.map { it.currency }.filterNot { conv.hasRate(it) }.toSet(),
                expenseCount = m.expenses.size,
                recentExpenses = m.expenses.take(5), // DAO orders newest first
                categories = m.categories.associateBy { it.id },
                hotels = j.hotels,
                placesCount = j.places.size,
                photoCount = j.photos.size,
                recentDiary = j.diary.take(3),
                diaryCount = j.diary.size,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripDetailState())

    fun setStatus(status: TripStatus) = viewModelScope.launch { c.trips.setStatus(tripId, status) }

    fun setArchived(archived: Boolean) = viewModelScope.launch { c.trips.setArchived(tripId, archived) }

    /** Runs in the app scope so it completes even though the screen is closed right away. */
    fun delete() = c.appScope.launch { c.trips.delete(tripId) }
}
