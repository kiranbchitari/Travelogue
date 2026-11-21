package com.travelogue.app.ui.trips

import com.travelogue.app.data.db.entity.CurrencyRateEntity
import com.travelogue.app.data.db.entity.ExpenseEntity
import com.travelogue.app.data.db.entity.TripEntity
import com.travelogue.app.data.db.entity.TripStatus
import com.travelogue.app.domain.Analytics
import com.travelogue.app.domain.CurrencyConverter
import java.time.LocalDate

/** A trip plus its spend in the trip's own currency (amounts without a manual rate are skipped). */
data class TripSummary(
    val trip: TripEntity,
    val total: Double,
    val expenseCount: Int,
    val hasUnconverted: Boolean,
)

fun converterFor(trip: TripEntity, rates: List<CurrencyRateEntity>): CurrencyConverter =
    CurrencyConverter(trip.currency, rates.filter { it.tripId == trip.id }.associate { it.currency to it.rate })

fun buildTripSummaries(
    trips: List<TripEntity>,
    expenses: List<ExpenseEntity>,
    rates: List<CurrencyRateEntity>,
): List<TripSummary> {
    val byTrip = expenses.groupBy { it.tripId }
    val ratesByTrip = rates.groupBy { it.tripId }
    return trips.map { t ->
        val conv = converterFor(t, ratesByTrip[t.id].orEmpty())
        val ex = byTrip[t.id].orEmpty()
        TripSummary(t, Analytics.tripTotal(ex, conv), ex.size, ex.any { !conv.hasRate(it.currency) })
    }
}

/** Finished: explicitly completed or the end date has passed. */
fun TripEntity.isPast(today: LocalDate = LocalDate.now()): Boolean =
    status == TripStatus.COMPLETED || endDate.isBefore(today)

fun TripEntity.isOngoing(today: LocalDate = LocalDate.now()): Boolean =
    !isPast(today) && !today.isBefore(startDate) && !today.isAfter(endDate)

/** "Day 3 of 7", "In 12 days", "Planning", "Completed". */
fun TripEntity.phaseLabel(today: LocalDate = LocalDate.now()): String = when {
    isPast(today) -> "Completed"
    isOngoing(today) -> "Day ${today.toEpochDay() - startDate.toEpochDay() + 1} of $days"
    status == TripStatus.PLANNING -> "Planning · ${com.travelogue.app.core.util.Fmt.countdown(startDate, today)}"
    else -> com.travelogue.app.core.util.Fmt.countdown(startDate, today)
}
