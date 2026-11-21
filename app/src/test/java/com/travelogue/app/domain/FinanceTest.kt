package com.travelogue.app.domain

import com.travelogue.app.data.db.entity.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinanceTest {
    private val start = LocalDate.of(2026, 3, 10)
    private val trip = TripEntity(id = 1, uuid = "t", name = "Test", destination = "X", startDate = start, endDate = start.plusDays(4), travellerCount = 3, budget = 10000.0, currency = "INR")
    private val hotel = ExpenseCategoryEntity(id = 1, name = "Hotel", iconKey = "hotel")
    private val food = ExpenseCategoryEntity(id = 2, name = "Food", iconKey = "food")
    private val taxi = ExpenseCategoryEntity(id = 3, name = "Taxi", iconKey = "transport")

    private fun expense(id: Long, amount: Double, payer: Long?, cat: Long?, currency: String = "INR", day: Int = 0, split: SplitType = SplitType.EQUAL) =
        ExpenseEntity(id = id, tripId = 1, title = "e$id", amount = amount, currency = currency, categoryId = cat, date = start.plusDays(day.toLong()), paidByTravellerId = payer, splitType = split)

    @Test fun equalSharesAlwaysSumToTotal() {
        val shares = SplitCalculator.equalShares(100.0, listOf(1, 2, 3))
        assertEquals(100.0, shares.values.sum(), 0.0001)
        assertEquals(33.34, shares[1]!!, 0.0001)
        assertEquals(33.33, shares[3]!!, 0.0001)
    }

    @Test fun converterUsesManualRatesOnly() {
        val c = CurrencyConverter("INR", mapOf("eur" to 90.0))
        assertEquals(900.0, c.toBase(10.0, "EUR")!!, 0.0001)
        assertEquals(50.0, c.toBase(50.0, "inr")!!, 0.0001)
        assertNull(c.toBase(10.0, "USD"))
    }

    @Test fun balancesAndTransfersSettleEveryone() {
        // A pays 300 split 3 ways, B pays 90 split 3 ways.
        val expenses = listOf(expense(1, 300.0, 1, 2), expense(2, 90.0, 2, 2))
        val splits = listOf(1L, 2L).flatMap { eid ->
            SplitCalculator.equalShares(expenses.first { it.id == eid }.amount, listOf(1, 2, 3)).map { (t, a) -> ExpenseSplitEntity(expenseId = eid, travellerId = t, amount = a) }
        }
        val conv = CurrencyConverter("INR", emptyMap())
        val balances = SplitCalculator.balances(listOf(1, 2, 3), expenses, splits, emptyList(), conv).associateBy { it.travellerId }
        assertEquals(170.0, balances[1]!!.net, 0.01)
        assertEquals(-40.0, balances[2]!!.net, 0.01)
        assertEquals(-130.0, balances[3]!!.net, 0.01)
        assertEquals(0.0, balances.values.sumOf { it.net }, 0.01)

        val transfers = SplitCalculator.suggestTransfers(balances.values.toList())
        assertEquals(2, transfers.size)
        assertTrue(transfers.all { it.toTravellerId == 1L })
        assertEquals(170.0, transfers.sumOf { it.amount }, 0.01)

        // After C pays A 130, only B's debt remains.
        val settled = SplitCalculator.balances(listOf(1, 2, 3), expenses, splits, listOf(SettlementEntity(tripId = 1, fromTravellerId = 3, toTravellerId = 1, amount = 130.0)), conv)
        val after = SplitCalculator.suggestTransfers(settled)
        assertEquals(1, after.size)
        assertEquals(2L, after[0].fromTravellerId)
        assertEquals(40.0, after[0].amount, 0.01)
    }

    @Test fun unsplitExpensesDoNotCreateDebts() {
        val expenses = listOf(expense(1, 500.0, 1, 2, split = SplitType.NONE))
        val b = SplitCalculator.balances(listOf(1, 2), expenses, emptyList(), emptyList(), CurrencyConverter("INR", emptyMap()))
        assertTrue(b.all { it.net == 0.0 })
    }

    @Test fun tripFinanceTotalsGroupsAndUnconverted() {
        val expenses = listOf(
            expense(1, 4000.0, 1, 1, day = 0, split = SplitType.NONE),
            expense(2, 600.0, 2, 2, day = 1, split = SplitType.NONE),
            expense(3, 10.0, 1, 2, currency = "EUR", day = 1, split = SplitType.NONE),
            expense(4, 400.0, 1, 3, day = 2, split = SplitType.NONE),
            expense(5, 20.0, 1, 3, currency = "USD", day = 2, split = SplitType.NONE),
        )
        val f = Analytics.tripFinance(trip, expenses, emptyList(), listOf(hotel, food, taxi), CurrencyConverter("INR", mapOf("EUR" to 90.0)))
        assertEquals(5900.0, f.total, 0.001)
        assertEquals(mapOf("USD" to 20.0), f.unconverted)
        assertEquals(4000.0, f.accommodation, 0.001)
        assertEquals(1500.0, f.food, 0.001)
        assertEquals(400.0, f.transport, 0.001)
        assertEquals(4100.0, f.remaining, 0.001)
        assertEquals(5900.0 / 3, f.costPerPerson, 0.001)
        assertEquals(1L, f.highest!!.id)
        assertEquals(hotel, f.byCategory.first().category)
        assertEquals(3, f.byDay.size)
        // Paid-by attribution for unsplit expenses.
        assertEquals(5300.0, f.byPerson.first { it.travellerId == 1L }.total, 0.001)
    }

    @Test fun homeConverterPrefersTripRatesThenSettings() {
        val home = HomeConverter("INR", listOf(ExchangeRate("EUR", "INR", 90.0), ExchangeRate("INR", "JPY", 1.8)))
        // Already home.
        assertEquals(100.0, home.convert(100.0, "INR")!!, 0.001)
        // INR trip with its own EUR rate: the trip's rate wins, matching the trip screens.
        val inrTrip = CurrencyConverter("INR", mapOf("EUR" to 92.5))
        assertEquals(925.0, home.convert(10.0, "EUR", inrTrip)!!, 0.001)
        // EUR trip, EUR expense: trip currency → home via the Settings rate.
        val eurTrip = CurrencyConverter("EUR", emptyMap())
        assertEquals(900.0, home.convert(10.0, "EUR", eurTrip)!!, 0.001)
        // JPY via the inverse Settings pair (1 INR = 1.8 JPY).
        assertEquals(100.0, home.convert(180.0, "JPY")!!, 0.001)
        // THB trip that priced INR itself: 1 INR = 0.4 THB → 40 THB = 100 INR.
        val thbTrip = CurrencyConverter("THB", mapOf("INR" to 0.4))
        assertEquals(100.0, home.convert(40.0, "THB", thbTrip)!!, 0.001)
        // No chain of rates at all.
        assertNull(home.convert(10.0, "USD", CurrencyConverter("USD", emptyMap())))
    }

    @Test fun exchangeRatesRoundTripThroughStorageFormat() {
        val rates = listOf(ExchangeRate("EUR", "INR", 92.5), ExchangeRate("USD", "INR", 84.0))
        val decoded = com.travelogue.app.data.settings.SettingsRepository.decodeRates(com.travelogue.app.data.settings.SettingsRepository.encodeRates(rates))
        assertEquals(rates, decoded)
        assertTrue(com.travelogue.app.data.settings.SettingsRepository.decodeRates("garbage;EU>INR=1;EUR>INR=-2").isEmpty())
    }
}
