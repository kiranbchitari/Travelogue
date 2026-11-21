package com.travelogue.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.designsystem.charts.ChartBar
import com.travelogue.app.data.db.entity.*
import com.travelogue.app.data.settings.AppSettings
import com.travelogue.app.di.AppContainer
import com.travelogue.app.domain.HomeConverter
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
    /** Spend across every trip, converted to the default currency. */
    val primaryTotal: Double = 0.0,
    /** Amounts with no exchange rate into the default currency yet, in their original currency. */
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

        val tripsById = core.trips.associateBy { it.id }
        val ratesByTrip = core.rates.groupBy { it.tripId }
        val converters = core.trips.associate { it.id to converterFor(it, ratesByTrip[it.id].orEmpty()) }
        val home = HomeConverter(currency.uppercase(), extras.settings.exchangeRates)
        val inHome = core.expenses.mapNotNull { e ->
            if (tripsById[e.tripId] == null) null else e to home.convert(e.amount, e.currency, converters[e.tripId])
        }
        val primary = inHome.sumOf { it.second ?: 0.0 }
        val others = inHome.filter { it.second == null }.groupBy { it.first.currency.uppercase() }
            .mapValues { (_, rows) -> rows.sumOf { it.first.amount } }

        val travelDays = core.trips.filter { !it.startDate.isAfter(today) }.sumOf { t ->
            val end = if (t.endDate.isAfter(today)) today else t.endDate
            (end.toEpochDay() - t.startDate.toEpochDay() + 1).toInt().coerceAtLeast(0)
        }

        val visible = summaries.filter { !it.trip.archived }
        val upcoming = visible.filter { !it.trip.isPast(today) }.sortedBy { it.trip.startDate }
        val recent = visible.filter { it.trip.isPast(today) }.sortedByDescending { it.trip.endDate }.take(5)

        // Monthly spend over the last 12 months: every trip, in the default currency.
        val months = (11 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }
        val perMonth = HashMap<YearMonth, Double>()
        inHome.forEach { (e, v) ->
            val ym = YearMonth.from(e.date)
            if (v == null || ym !in months) return@forEach
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
