package com.travelogue.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.designsystem.charts.ChartBar
import com.travelogue.app.data.db.entity.*
import com.travelogue.app.data.settings.AppSettings
import com.travelogue.app.di.AppContainer
import com.travelogue.app.ui.trips.TripSummary
import com.travelogue.app.ui.trips.buildTripSummaries
import com.travelogue.app.ui.trips.converterFor
import com.travelogue.app.ui.trips.isPast
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class ExpenseLine(val expense: ExpenseEntity, val category: ExpenseCategoryEntity?, val tripName: String)

data class HomeState(
    val loaded: Boolean = false,
    val defaultCurrency: String = "INR",
    val tripCount: Int = 0,
    /** Spend across trips whose currency is the default currency. */
    val primaryTotal: Double = 0.0,
    /** Spend of trips in other currencies, by currency. */
    val otherTotals: Map<String, Double> = emptyMap(),
    val placesCount: Int = 0,
    val travelDays: Int = 0,
    val upcoming: List<TripSummary> = emptyList(),
    val recent: List<TripSummary> = emptyList(),
    val monthly: List<ChartBar> = emptyList(),
    val monthlyTotal: Double = 0.0,
    val recentExpenses: List<ExpenseLine> = emptyList(),
    val recentPhotos: List<TripPhotoEntity> = emptyList(),
)

private data class Core(
    val trips: List<TripEntity>,
    val expenses: List<ExpenseEntity>,
    val rates: List<CurrencyRateEntity>,
)

private data class Extras(
    val places: List<PlaceEntity>,
    val photos: List<TripPhotoEntity>,
    val categories: List<ExpenseCategoryEntity>,
    val settings: AppSettings,
)

class HomeViewModel(c: AppContainer) : ViewModel() {

    val state: StateFlow<HomeState> = combine(
        combine(c.trips.observeTrips(), c.expenses.observeAll(), c.trips.observeAllRates(), ::Core),
        combine(c.journal.observeAllPlaces(), c.photos.observeAll(), c.expenses.observeCategories(), c.settings.settings, ::Extras),
    ) { core, extras -> build(core, extras) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    private fun build(core: Core, extras: Extras): HomeState {
        val today = LocalDate.now()
        val currency = extras.settings.defaultCurrency
        val summaries = buildTripSummaries(core.trips, core.expenses, core.rates)

        val totalsByCurrency = summaries.groupBy { it.trip.currency.uppercase() }.mapValues { (_, list) -> list.sumOf { it.total } }
        val primary = totalsByCurrency[currency.uppercase()] ?: 0.0
        val others = totalsByCurrency.filterKeys { it != currency.uppercase() }.filterValues { it > 0 }

        val travelDays = core.trips.filter { !it.startDate.isAfter(today) }.sumOf { t ->
            val end = if (t.endDate.isAfter(today)) today else t.endDate
            (end.toEpochDay() - t.startDate.toEpochDay() + 1).toInt().coerceAtLeast(0)
        }

        val visible = summaries.filter { !it.trip.archived }
        val upcoming = visible.filter { !it.trip.isPast(today) }.sortedBy { it.trip.startDate }
        val recent = visible.filter { it.trip.isPast(today) }.sortedByDescending { it.trip.endDate }.take(5)

        // Monthly spend over the last 12 months, in the default currency.
        val tripsById = core.trips.associateBy { it.id }
        val ratesByTrip = core.rates.groupBy { it.tripId }
        val converters = core.trips.associate { it.id to converterFor(it, ratesByTrip[it.id].orEmpty()) }
        val months = (11 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }
        val perMonth = HashMap<YearMonth, Double>()
        core.expenses.forEach { e ->
            val trip = tripsById[e.tripId] ?: return@forEach
            if (!trip.currency.equals(currency, true)) return@forEach
            val ym = YearMonth.from(e.date)
            if (ym !in months) return@forEach
            val v = converters[trip.id]?.toBase(e.amount, e.currency) ?: return@forEach
            perMonth[ym] = (perMonth[ym] ?: 0.0) + v
        }
        val monthly = months.map { ChartBar(it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), perMonth[it] ?: 0.0) }

        val catById = extras.categories.associateBy { it.id }
        val recentExpenses = core.expenses
            .sortedWith(compareByDescending<ExpenseEntity> { it.date }.thenByDescending { it.time }.thenByDescending { it.createdAt })
            .take(5)
            .map { ExpenseLine(it, it.categoryId?.let(catById::get), tripsById[it.tripId]?.name.orEmpty()) }

        return HomeState(
            loaded = true,
            defaultCurrency = currency,
            tripCount = core.trips.size,
            primaryTotal = primary,
            otherTotals = others,
            placesCount = extras.places.size,
            travelDays = travelDays,
            upcoming = upcoming,
            recent = recent,
            monthly = monthly,
            monthlyTotal = monthly.sumOf { it.value },
            recentExpenses = recentExpenses,
            recentPhotos = extras.photos.sortedByDescending { it.createdAt }.take(12),
        )
    }
}
