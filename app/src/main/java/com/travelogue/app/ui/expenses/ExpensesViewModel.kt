package com.travelogue.app.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.data.db.entity.*
import com.travelogue.app.di.AppContainer
import com.travelogue.app.domain.CurrencyConverter
import com.travelogue.app.domain.ExchangeRate
import com.travelogue.app.domain.HomeConverter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.YearMonth

enum class DateRange(val label: String) {
    THIS_MONTH("This month"), LAST_3_MONTHS("Last 3 months"), THIS_YEAR("This year"), ALL("All time"), CUSTOM("Custom")
}

data class GlobalFilters(
    val tripId: Long? = null,
    val categoryId: Long? = null,
    val travellerId: Long? = null,
    val range: DateRange = DateRange.ALL,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
)

/**
 * An expense with its trip, its value in the trip's currency and its value in the default currency
 * (each null when no manual rate chain exists).
 */
data class GlobalExpense(val expense: ExpenseEntity, val trip: TripEntity, val converted: Double?, val inHome: Double?)

data class CurrencyTotal(val currency: String, val total: Double)
data class CategoryAmount(val category: ExpenseCategoryEntity?, val total: Double)
data class PeriodAmount(val label: String, val total: Double)

data class ExpensesUi(
    val loading: Boolean = true,
    val trips: List<TripEntity> = emptyList(),
    val categories: List<ExpenseCategoryEntity> = emptyList(),
    val travellerOptions: List<TravellerEntity> = emptyList(),
    val travellerNames: Map<Long, String> = emptyMap(),
    val anyExpenses: Boolean = false,
    val filtered: List<GlobalExpense> = emptyList(),
    val totals: List<CurrencyTotal> = emptyList(),
    /** The default currency; every total and chart on this screen is in it. */
    val primaryCurrency: String = "INR",
    val homeTotal: Double = 0.0,
    val unconvertedCount: Int = 0,
    /** Original-currency amounts that have no rate into the default currency. */
    val unconverted: List<CurrencyTotal> = emptyList(),
    val byCategory: List<CategoryAmount> = emptyList(),
    val periods: List<PeriodAmount> = emptyList(),
    val periodsAreDays: Boolean = false,
    val filters: GlobalFilters = GlobalFilters(),
) {
    val primaryTotal: Double get() = homeTotal
    val categoryById: Map<Long, ExpenseCategoryEntity> get() = categories.associateBy { it.id }
}

private data class Base(
    val trips: List<TripEntity>,
    val expenses: List<ExpenseEntity>,
    val categories: List<ExpenseCategoryEntity>,
    val travellers: List<TravellerEntity>,
)

private data class Extra(
    val tripTravellers: List<TripTravellerEntity>,
    val rates: List<CurrencyRateEntity>,
    val splits: List<ExpenseSplitEntity>,
    val defaultCurrency: String,
    val exchangeRates: List<ExchangeRate>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(c: AppContainer) : ViewModel() {
    private val filters = MutableStateFlow(GlobalFilters())

    private val allSplits: Flow<List<ExpenseSplitEntity>> = c.trips.observeTrips().flatMapLatest { trips ->
        if (trips.isEmpty()) flowOf(emptyList())
        else combine(trips.map { c.expenses.observeSplitsForTrip(it.id) }) { arr -> arr.flatMap { it } }
    }

    private val base = combine(c.trips.observeTrips(), c.expenses.observeAll(), c.expenses.observeCategories(), c.trips.observeAllTravellers()) { t, e, cat, tr ->
        Base(t, e, cat, tr)
    }
    private val extra = combine(c.trips.observeAllTripTravellers(), c.trips.observeAllRates(), allSplits, c.settings.settings) { tt, r, s, st ->
        Extra(tt, r, s, st.defaultCurrency, st.exchangeRates)
    }

    val state: StateFlow<ExpensesUi> = combine(base, extra, filters) { b, x, f -> build(b, x, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExpensesUi())

    fun update(transform: (GlobalFilters) -> GlobalFilters) { filters.value = transform(filters.value) }

    private fun build(b: Base, x: Extra, f: GlobalFilters): ExpensesUi {
        val tripById = b.trips.associateBy { it.id }
        val ratesByTrip = x.rates.groupBy { it.tripId }
        val converters = b.trips.associate { t ->
            t.id to CurrencyConverter(t.currency, ratesByTrip[t.id].orEmpty().associate { it.currency to it.rate })
        }
        val splitsByExpense = x.splits.groupBy { it.expenseId }
        val home = HomeConverter(x.defaultCurrency.uppercase(), x.exchangeRates)

        val today = LocalDate.now()
        val (from, to) = when (f.range) {
            DateRange.THIS_MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
            DateRange.LAST_3_MONTHS -> today.minusMonths(2).withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
            DateRange.THIS_YEAR -> today.withDayOfYear(1) to today.withDayOfYear(today.lengthOfYear())
            DateRange.ALL -> null to null
            DateRange.CUSTOM -> f.customStart to f.customEnd
        }

        val filtered = b.expenses.mapNotNull { e ->
            val trip = tripById[e.tripId] ?: return@mapNotNull null
            if (f.tripId != null && e.tripId != f.tripId) return@mapNotNull null
            if (f.categoryId != null && e.categoryId != f.categoryId) return@mapNotNull null
            if (from != null && e.date.isBefore(from)) return@mapNotNull null
            if (to != null && e.date.isAfter(to)) return@mapNotNull null
            if (f.travellerId != null) {
                val involved = e.paidByTravellerId == f.travellerId ||
                    (e.splitType != SplitType.NONE && splitsByExpense[e.id].orEmpty().any { it.travellerId == f.travellerId })
                if (!involved) return@mapNotNull null
            }
            val conv = converters[trip.id]
            GlobalExpense(e, trip, conv?.toBase(e.amount, e.currency), home.convert(e.amount, e.currency, conv))
        }

        val totals = filtered.filter { it.converted != null }
            .groupBy { it.trip.currency.uppercase() }
            .map { (cur, rows) -> CurrencyTotal(cur, rows.sumOf { it.converted ?: 0.0 }) }
        val primary = home.home
        val sortedTotals = totals.sortedByDescending { it.total }

        // Every trip, brought into the default currency.
        val inPrimary = filtered.filter { it.inHome != null }
        val unconverted = filtered.filter { it.inHome == null }
            .groupBy { it.expense.currency.uppercase() }
            .map { (cur, rows) -> CurrencyTotal(cur, rows.sumOf { it.expense.amount }) }
            .sortedByDescending { it.total }
        val catById = b.categories.associateBy { it.id }
        val byCategory = inPrimary.groupBy { it.expense.categoryId }
            .map { (cid, rows) -> CategoryAmount(cid?.let(catById::get), rows.sumOf { it.inHome ?: 0.0 }) }
            .filter { it.total > 0 }
            .sortedByDescending { it.total }

        // Periods: days within a single month, otherwise months (latest 12).
        val months = inPrimary.map { YearMonth.from(it.expense.date) }.distinct().sorted()
        val singleMonth = f.range == DateRange.THIS_MONTH || months.size == 1
        val periods: List<PeriodAmount>
        if (inPrimary.isEmpty()) {
            periods = emptyList()
        } else if (singleMonth) {
            val ym = if (f.range == DateRange.THIS_MONTH) YearMonth.from(today) else months.first()
            val byDay = inPrimary.groupBy { it.expense.date.dayOfMonth }.mapValues { (_, r) -> r.sumOf { it.inHome ?: 0.0 } }
            periods = (1..ym.lengthOfMonth()).map { d -> PeriodAmount(d.toString(), byDay[d] ?: 0.0) }
        } else {
            val first = months.first()
            val last = months.last()
            val all = generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.toList().takeLast(12)
            val byMonth = inPrimary.groupBy { YearMonth.from(it.expense.date) }.mapValues { (_, r) -> r.sumOf { it.inHome ?: 0.0 } }
            periods = all.map { ym ->
                val label = ym.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) +
                    if (all.first().year != all.last().year && ym.monthValue == 1) " ’${ym.year % 100}" else ""
                PeriodAmount(label, byMonth[ym] ?: 0.0)
            }
        }

        val involvedTravellerIds = x.tripTravellers
            .filter { f.tripId == null || it.tripId == f.tripId }
            .map { it.travellerId }.toSet()
        val travellerOptions = b.travellers.filter { it.id in involvedTravellerIds }

        return ExpensesUi(
            loading = false,
            trips = b.trips,
            categories = b.categories,
            travellerOptions = travellerOptions,
            travellerNames = b.travellers.associate { it.id to it.name },
            anyExpenses = b.expenses.isNotEmpty(),
            filtered = filtered.sortedWith(compareByDescending<GlobalExpense> { it.expense.date }.thenByDescending { it.expense.time }.thenByDescending { it.expense.id }),
            totals = sortedTotals,
            primaryCurrency = primary,
            homeTotal = inPrimary.sumOf { it.inHome ?: 0.0 },
            unconvertedCount = filtered.count { it.inHome == null },
            unconverted = unconverted,
            byCategory = byCategory,
            periods = periods,
            periodsAreDays = singleMonth,
            filters = f,
        )
    }
}
