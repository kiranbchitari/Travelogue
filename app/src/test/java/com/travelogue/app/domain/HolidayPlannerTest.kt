package com.travelogue.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HolidayPlannerTest {
    private val prefs = HolidayPrefs()
    // November 2026: the 1st is a Sunday, so 6 is a Friday and 9 a Monday.
    private fun nov(day: Int) = LocalDate.of(2026, 11, day)
    private fun breaks(mine: Set<LocalDate>, partner: Set<LocalDate>, p: HolidayPrefs = prefs, labels: Map<LocalDate, String> = emptyMap()) =
        HolidayPlanner.sharedBreaks(nov(1), nov(30), mine, partner, p, labels)

    @Test fun ordinaryWeekendsAreNotBreaks() {
        assertTrue(breaks(emptySet(), emptySet()).isEmpty())
    }

    @Test fun holidayNextToWeekendMakesALongWeekendOnlyWhenBothHaveIt() {
        assertTrue(breaks(setOf(nov(6)), emptySet()).isEmpty())
        val both = breaks(setOf(nov(6)), setOf(nov(6)), labels = mapOf(nov(6) to "Founders day"))
        assertEquals(listOf(SharedBreak(nov(6), nov(8), listOf("Founders day"))), both)
        assertEquals(3, both.single().days)
    }

    @Test fun leaveBridgesTwoWeekends() {
        val week = (9..13).map(::nov).toSet()
        val b = breaks(week, week).single()
        assertEquals(nov(7), b.start)
        assertEquals(nov(15), b.end)
        assertEquals(9, b.days)
    }

    @Test fun differentWeeklyDaysOffAreRespected() {
        // Partner works Saturdays, so a shared Friday off does not reach the weekend.
        val p = prefs.copy(partnerWeeklyOff = setOf(DayOfWeek.SUNDAY))
        assertTrue(breaks(setOf(nov(6)), setOf(nov(6)), p).isEmpty())
        assertEquals(listOf(SharedBreak(nov(6), nov(8))), breaks(setOf(nov(6)), setOf(nov(6), nov(7)), p))
    }

    private fun ideas(from: LocalDate, to: LocalDate, mine: Set<LocalDate> = emptySet(), partner: Set<LocalDate> = emptySet()) =
        HolidayPlanner.leaveIdeas(from, to, mine, partner, prefs, IndianHolidays.between(from, to))

    @Test fun indianHolidaysMatchThePublishedLists() {
        val y2026 = IndianHolidays.forYear(2026)
        assertEquals(17, y2026.size)
        assertEquals("Diwali", y2026.single { it.date == LocalDate.of(2026, 11, 8) }.name)
        // Independence Day and Milad-un-Nabi share 15 August in 2027, so 17 holidays fall on 16 dates.
        val y2027 = IndianHolidays.forYear(2027)
        assertEquals(16, y2027.size)
        assertEquals("Independence Day & Milad-un-Nabi", y2027.single { it.date == LocalDate.of(2027, 8, 15) }.name)
        assertEquals(LocalDate.of(2027, 10, 29), y2027.single { it.name == "Diwali" }.date)
        // A year with no published list still has the four fixed holidays.
        assertEquals(listOf("Republic Day", "Independence Day", "Gandhi Jayanti", "Christmas"), IndianHolidays.forYear(2030).map { it.name })
    }

    @Test fun suggestsBridgingAHolidayToTheWeekend() {
        // Republic Day 2027 is a Tuesday: taking Monday the 25th gives Saturday to Tuesday.
        val idea = ideas(LocalDate.of(2027, 1, 18), LocalDate.of(2027, 1, 31)).single()
        assertEquals(LocalDate.of(2027, 1, 23), idea.start)
        assertEquals(LocalDate.of(2027, 1, 26), idea.end)
        assertEquals(listOf(LocalDate.of(2027, 1, 25)), idea.leaveDays)
        assertEquals(listOf("Republic Day"), idea.holidays.map { it.name })
        assertEquals(4, idea.days)
        assertEquals(setOf(LocalDate.of(2027, 1, 25), LocalDate.of(2027, 1, 26)), idea.toMark.keys)
    }

    @Test fun aHolidayBesideTheWeekendNeedsNoLeave() {
        // Diwali 2027 is a Friday.
        val idea = ideas(LocalDate.of(2027, 10, 25), LocalDate.of(2027, 11, 5)).single()
        assertEquals(LocalDate.of(2027, 10, 29), idea.start)
        assertEquals(LocalDate.of(2027, 10, 31), idea.end)
        assertTrue(idea.leaveDays.isEmpty())
        assertEquals(listOf("Diwali"), idea.holidays.map { it.name })
    }

    @Test fun nothingIsSuggestedOnceTheBreakIsMarked() {
        val from = LocalDate.of(2027, 1, 18)
        val to = LocalDate.of(2027, 1, 31)
        val marked = setOf(LocalDate.of(2027, 1, 25), LocalDate.of(2027, 1, 26))
        assertTrue(ideas(from, to, marked, marked).isEmpty())
        // If only one of them has marked it, the suggestion stays.
        assertEquals(1, ideas(from, to, marked, emptySet()).size)
        // Ordinary weeks with no holiday never produce a suggestion.
        assertTrue(ideas(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28)).isEmpty())
    }

    @Test fun runsAreCutAtTheWindowAndLabelsAreNotRepeated() {
        val days = (27..30).map(::nov).toSet()
        val b = breaks(days, days, labels = days.associateWith { "Leave" }).single()
        assertEquals(nov(27), b.start)
        assertEquals(nov(30), b.end)
        assertEquals(listOf("Leave"), b.labels)
    }
}
