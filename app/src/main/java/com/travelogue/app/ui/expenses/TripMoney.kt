package com.travelogue.app.ui.expenses

import com.travelogue.app.data.db.entity.*
import com.travelogue.app.di.AppContainer
import com.travelogue.app.domain.Analytics
import com.travelogue.app.domain.CurrencyConverter
import com.travelogue.app.domain.TripFinance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Everything money-related about one trip, loaded together so screens recompute consistently. */
data class TripMoney(
    val trip: TripEntity,
    val expenses: List<ExpenseEntity>,
    val splits: List<ExpenseSplitEntity>,
    val settlements: List<SettlementEntity>,
    val categories: List<ExpenseCategoryEntity>,
    val travellers: List<TravellerEntity>,
    val allTravellers: List<TravellerEntity>,
    val rates: List<CurrencyRateEntity>,
) {
    val converter: CurrencyConverter = CurrencyConverter(trip.currency, rates.associate { it.currency to it.rate })
    val categoryById: Map<Long, ExpenseCategoryEntity> = categories.associateBy { it.id }
    val travellerNames: Map<Long, String> = allTravellers.associate { it.id to it.name }

    val finance: TripFinance by lazy {
        Analytics.tripFinance(trip, expenses, splits, categories, converter, maxOf(trip.travellerCount, travellers.size, 1))
    }

    /** Foreign currencies used by this trip's expenses. */
    val foreignCurrencies: List<String> by lazy {
        expenses.map { it.currency.uppercase() }.filter { !it.equals(trip.currency, true) }.distinct().sorted()
    }

    fun convertedLabel(e: ExpenseEntity): String? {
        if (e.currency.equals(trip.currency, true)) return null
        val v = converter.toBase(e.amount, e.currency) ?: return "No rate set"
        return "≈ " + com.travelogue.app.core.util.Fmt.money(v, trip.currency)
    }
}

private data class Part1(
    val trip: TripEntity?,
    val expenses: List<ExpenseEntity>,
    val splits: List<ExpenseSplitEntity>,
    val settlements: List<SettlementEntity>,
)

private data class Part2(
    val categories: List<ExpenseCategoryEntity>,
    val travellers: List<TravellerEntity>,
    val allTravellers: List<TravellerEntity>,
    val rates: List<CurrencyRateEntity>,
)

fun AppContainer.tripMoneyFlow(tripId: Long): Flow<TripMoney?> {
    val a = combine(
        trips.observeTrip(tripId),
        expenses.observeForTrip(tripId),
        expenses.observeSplitsForTrip(tripId),
        expenses.observeSettlements(tripId),
    ) { t, e, s, st -> Part1(t, e, s, st) }
    val b = combine(
        expenses.observeCategories(),
        trips.observeTravellers(tripId),
        trips.observeAllTravellers(),
        trips.observeRates(tripId),
    ) { c, t, all, r -> Part2(c, t, all, r) }
    return combine(a, b) { p1, p2 ->
        val trip = p1.trip ?: return@combine null
        TripMoney(trip, p1.expenses, p1.splits, p1.settlements, p2.categories, p2.travellers, p2.allTravellers, p2.rates)
    }
}
