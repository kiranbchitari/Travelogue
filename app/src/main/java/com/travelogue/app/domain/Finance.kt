package com.travelogue.app.domain

import com.travelogue.app.data.db.entity.ExpenseCategoryEntity
import com.travelogue.app.data.db.entity.ExpenseEntity
import com.travelogue.app.data.db.entity.ExpenseSplitEntity
import com.travelogue.app.data.db.entity.SettlementEntity
import com.travelogue.app.data.db.entity.SplitType
import com.travelogue.app.data.db.entity.TripEntity
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round

private const val EPS = 0.005

fun Double.roundMoney(): Double = round(this * 100) / 100

/**
 * Converts amounts into a trip's currency using rates the user typed in. There are no automatic
 * exchange-rate lookups: an amount in a currency without a rate converts to null and is reported
 * separately instead of being silently wrong.
 */
class CurrencyConverter(val baseCurrency: String, rates: Map<String, Double>) {
    private val rates = rates.mapKeys { it.key.uppercase() }

    fun toBase(amount: Double, currency: String): Double? {
        val c = currency.uppercase()
        if (c == baseCurrency.uppercase()) return amount
        val r = rates[c] ?: return null
        return amount * r
    }

    fun hasRate(currency: String) = currency.equals(baseCurrency, true) || rates.containsKey(currency.uppercase())

    /** 1 unit of [currency] in the base currency, or null when no manual rate exists. */
    fun rateFor(currency: String): Double? = if (currency.equals(baseCurrency, true)) 1.0 else rates[currency.uppercase()]
}

/** A manual exchange rate entered in Settings: 1 [from] = [rate] [to]. */
data class ExchangeRate(val from: String, val to: String, val rate: Double)

/**
 * Converts any expense into the user's default ("home") currency for cross-trip totals and charts.
 *
 * Order of preference, so global numbers agree with each trip's own screens:
 * 1. already in the home currency;
 * 2. the trip's own rates into the trip currency, then trip currency → home (a Settings rate, or the
 *    trip's own rate for the home currency, inverted);
 * 3. a Settings rate straight from the expense currency to home.
 * Returns null when no chain of manual rates exists. Nothing is ever looked up online.
 */
class HomeConverter(val home: String, rates: List<ExchangeRate>) {
    private val toHome: Map<String, Double> = buildMap {
        rates.filter { it.rate > 0 && it.to.equals(home, true) }.forEach { put(it.from.uppercase(), it.rate) }
        rates.filter { it.rate > 0 && it.from.equals(home, true) }.forEach { if (!containsKey(it.to.uppercase())) put(it.to.uppercase(), 1 / it.rate) }
    }

    fun rate(currency: String): Double? = if (currency.equals(home, true)) 1.0 else toHome[currency.uppercase()]

    fun convert(amount: Double, currency: String, trip: CurrencyConverter? = null): Double? {
        if (currency.equals(home, true)) return amount
        if (trip != null) {
            val inTrip = trip.toBase(amount, currency)
            if (inTrip != null) {
                rate(trip.baseCurrency)?.let { return inTrip * it }
                trip.rateFor(home)?.let { r -> if (r > 0) return inTrip / r }
            }
        }
        return rate(currency)?.let { amount * it }
    }
}

// ---------------------------------------------------------------------------------------------
// Splits
// ---------------------------------------------------------------------------------------------

object SplitCalculator {
    /** Equal split that always sums exactly to [amount]; the remainder cent goes to the first people. */
    fun equalShares(amount: Double, travellerIds: List<Long>): Map<Long, Double> {
        if (travellerIds.isEmpty()) return emptyMap()
        val cents = Math.round(amount * 100)
        val base = cents / travellerIds.size
        var remainder = cents - base * travellerIds.size
        return travellerIds.associateWith {
            val extra = if (remainder > 0) { remainder--; 1 } else 0
            (base + extra) / 100.0
        }
    }

    data class Balance(val travellerId: Long, val paid: Double, val share: Double, val settledOut: Double, val settledIn: Double) {
        /** Positive = others owe this person; negative = this person owes. */
        val net: Double get() = (paid - share + settledOut - settledIn).roundMoney()
    }

    data class Transfer(val fromTravellerId: Long, val toTravellerId: Long, val amount: Double)

    /**
     * Net balance for each traveller, in the trip currency. Only split expenses with a payer take part;
     * settlements move money from the debtor to the creditor.
     */
    fun balances(
        travellerIds: List<Long>,
        expenses: List<ExpenseEntity>,
        splits: List<ExpenseSplitEntity>,
        settlements: List<SettlementEntity>,
        converter: CurrencyConverter,
    ): List<Balance> {
        val paid = HashMap<Long, Double>()
        val share = HashMap<Long, Double>()
        val splitsByExpense = splits.groupBy { it.expenseId }
        for (e in expenses) {
            if (e.splitType == SplitType.NONE || e.paidByTravellerId == null) continue
            val rows = splitsByExpense[e.id].orEmpty()
            if (rows.isEmpty()) continue
            val total = converter.toBase(e.amount, e.currency) ?: continue
            paid[e.paidByTravellerId] = (paid[e.paidByTravellerId] ?: 0.0) + total
            for (s in rows) {
                val part = converter.toBase(s.amount, e.currency) ?: continue
                share[s.travellerId] = (share[s.travellerId] ?: 0.0) + part
            }
        }
        val out = HashMap<Long, Double>()
        val inn = HashMap<Long, Double>()
        for (s in settlements) {
            out[s.fromTravellerId] = (out[s.fromTravellerId] ?: 0.0) + s.amount
            inn[s.toTravellerId] = (inn[s.toTravellerId] ?: 0.0) + s.amount
        }
        val ids = (travellerIds + paid.keys + share.keys + out.keys + inn.keys).distinct()
        return ids.map { Balance(it, paid[it] ?: 0.0, share[it] ?: 0.0, out[it] ?: 0.0, inn[it] ?: 0.0) }
    }

    /** Minimal-ish set of payments that clears all balances (greedy largest-first matching). */
    fun suggestTransfers(balances: List<Balance>): List<Transfer> {
        val debtors = balances.filter { it.net < -EPS }.map { it.travellerId to -it.net }.sortedByDescending { it.second }.toMutableList()
        val creditors = balances.filter { it.net > EPS }.map { it.travellerId to it.net }.sortedByDescending { it.second }.toMutableList()
        val result = mutableListOf<Transfer>()
        var i = 0
        var j = 0
        val d = debtors.map { it.second }.toMutableList()
        val c = creditors.map { it.second }.toMutableList()
        while (i < debtors.size && j < creditors.size) {
            val amt = min(d[i], c[j]).roundMoney()
            if (amt > EPS) result += Transfer(debtors[i].first, creditors[j].first, amt)
            d[i] -= amt
            c[j] -= amt
            if (d[i] <= EPS) i++
            if (c[j] <= EPS) j++
        }
        return result
    }
}

// ---------------------------------------------------------------------------------------------
// Analytics
// ---------------------------------------------------------------------------------------------

/** Groups used by the financial summary. Matched on the category's icon key so renames don't break it. */
object CategoryGroups {
    val ACCOMMODATION = setOf("hotel")
    val TRANSPORT = setOf("transport", "fuel", "parking")
    val FOOD = setOf("food", "restaurant")
}

data class CategoryTotal(val category: ExpenseCategoryEntity?, val total: Double, val count: Int)
data class DayTotal(val date: LocalDate, val total: Double)
data class PersonTotal(val travellerId: Long, val total: Double)

data class TripFinance(
    val currency: String,
    val budget: Double,
    val total: Double,
    val expenseCount: Int,
    /** Expenses whose currency has no manual rate; excluded from [total]. */
    val unconverted: Map<String, Double>,
    val byCategory: List<CategoryTotal>,
    val byDay: List<DayTotal>,
    val byPerson: List<PersonTotal>,
    val highest: ExpenseEntity?,
    val averageDaily: Double,
    val costPerPerson: Double,
    val accommodation: Double,
    val transport: Double,
    val food: Double,
) {
    val remaining: Double get() = budget - total
    val budgetUsed: Float get() = if (budget <= 0) 0f else (total / budget).toFloat()
    val hasUnconverted: Boolean get() = unconverted.isNotEmpty()

    companion object {
        fun empty(currency: String, budget: Double = 0.0) = TripFinance(
            currency, budget, 0.0, 0, emptyMap(), emptyList(), emptyList(), emptyList(), null, 0.0, 0.0, 0.0, 0.0, 0.0,
        )
    }
}

object Analytics {
    fun tripFinance(
        trip: TripEntity,
        expenses: List<ExpenseEntity>,
        splits: List<ExpenseSplitEntity>,
        categories: List<ExpenseCategoryEntity>,
        converter: CurrencyConverter,
        travellerCount: Int = trip.travellerCount,
    ): TripFinance {
        if (expenses.isEmpty()) return TripFinance.empty(trip.currency, trip.budget)
        val catById = categories.associateBy { it.id }
        val unconverted = HashMap<String, Double>()
        val converted = expenses.mapNotNull { e ->
            val v = converter.toBase(e.amount, e.currency)
            if (v == null) { unconverted[e.currency] = (unconverted[e.currency] ?: 0.0) + e.amount; null } else e to v
        }
        val total = converted.sumOf { it.second }
        val byCategory = converted.groupBy { it.first.categoryId }
            .map { (cid, rows) -> CategoryTotal(cid?.let(catById::get), rows.sumOf { it.second }, rows.size) }
            .sortedByDescending { it.total }
        val byDay = converted.groupBy { it.first.date }.map { (d, rows) -> DayTotal(d, rows.sumOf { it.second }) }.sortedBy { it.date }

        // Individual spending: what each person consumed. Split expenses use the split shares;
        // unsplit expenses count fully for whoever paid.
        val splitsByExpense = splits.groupBy { it.expenseId }
        val person = HashMap<Long, Double>()
        for ((e, v) in converted) {
            val rows = splitsByExpense[e.id].orEmpty()
            if (e.splitType != SplitType.NONE && rows.isNotEmpty()) {
                val ratio = if (e.amount == 0.0) 0.0 else v / e.amount
                rows.forEach { person[it.travellerId] = (person[it.travellerId] ?: 0.0) + it.amount * ratio }
            } else if (e.paidByTravellerId != null) {
                person[e.paidByTravellerId] = (person[e.paidByTravellerId] ?: 0.0) + v
            }
        }
        fun groupTotal(keys: Set<String>) = converted.filter { (e, _) -> e.categoryId?.let(catById::get)?.iconKey in keys }.sumOf { it.second }

        val today = LocalDate.now()
        val elapsedDays = when {
            today.isBefore(trip.startDate) -> trip.days
            today.isAfter(trip.endDate) -> trip.days
            else -> (today.toEpochDay() - trip.startDate.toEpochDay() + 1).toInt()
        }.coerceAtLeast(1)

        return TripFinance(
            currency = trip.currency,
            budget = trip.budget,
            total = total,
            expenseCount = expenses.size,
            unconverted = unconverted,
            byCategory = byCategory,
            byDay = byDay,
            byPerson = person.map { PersonTotal(it.key, it.value) }.sortedByDescending { it.total },
            highest = converted.maxByOrNull { it.second }?.first,
            averageDaily = total / elapsedDays,
            costPerPerson = total / travellerCount.coerceAtLeast(1),
            accommodation = groupTotal(CategoryGroups.ACCOMMODATION),
            transport = groupTotal(CategoryGroups.TRANSPORT),
            food = groupTotal(CategoryGroups.FOOD),
        )
    }

    /** Simple total for a trip in its own currency (amounts without a rate are skipped). */
    fun tripTotal(expenses: List<ExpenseEntity>, converter: CurrencyConverter): Double =
        expenses.sumOf { converter.toBase(it.amount, it.currency) ?: 0.0 }

    fun nearlyEqual(a: Double, b: Double) = abs(a - b) < EPS
}
