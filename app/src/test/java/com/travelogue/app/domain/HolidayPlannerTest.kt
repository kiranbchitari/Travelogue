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
    /** Me and my partner as the planner sees them: each one's marked days and weekly days off. */
    private fun couple(mine: Set<LocalDate>, partner: Set<LocalDate>, p: HolidayPrefs = prefs) =
        listOf(DaysOff(mine, p.myWeeklyOff), DaysOff(partner, p.partnerWeeklyOff))

    private fun breaks(mine: Set<LocalDate>, partner: Set<LocalDate>, p: HolidayPrefs = prefs, labels: Map<LocalDate, String> = emptyMap()) =
        HolidayPlanner.sharedBreaks(nov(1), nov(30), couple(mine, partner, p), labels)

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
        HolidayPlanner.leaveIdeas(from, to, couple(mine, partner), IndianHolidays.between(from, to))

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

    // ---- More than two people ----

    @Test fun peopleListStartsWithMeAndPartnerThenAnyoneAdded() {
        assertEquals(listOf(HolidayPerson.ME, HolidayPerson.PARTNER), prefs.people.map { it.id })
        val mum = HolidayPerson("mum", "Mum", setOf(DayOfWeek.SUNDAY))
        val p = HolidayPrefs("Kiran", "Asha", extras = listOf(mum))
        assertEquals(listOf("Kiran", "Asha", "Mum"), p.people.map { it.name })
        assertEquals(setOf(DayOfWeek.SUNDAY), p.people[2].weeklyOff)
        assertEquals(HolidayPrefs.Weekend, p.people[1].weeklyOff)
    }

    @Test fun aBreakNeedsEveryoneOff() {
        val friday = setOf(nov(6))
        val two = listOf(DaysOff(friday), DaysOff(friday))
        // The couple have the long weekend, but a third person who has not got the Friday breaks it.
        assertEquals(listOf(SharedBreak(nov(6), nov(8))), HolidayPlanner.sharedBreaks(nov(1), nov(30), two))
        assertTrue(HolidayPlanner.sharedBreaks(nov(1), nov(30), two + DaysOff(emptySet())).isEmpty())
        assertEquals(listOf(SharedBreak(nov(6), nov(8))), HolidayPlanner.sharedBreaks(nov(1), nov(30), two + DaysOff(friday)))
    }

    @Test fun anAddedPersonsWeeklyDaysOffCount() {
        // The third person works Saturdays, so a Friday all three have off stops short of the weekend.
        val friday = setOf(nov(6))
        val sundayOnly = DaysOff(friday, setOf(DayOfWeek.SUNDAY))
        val people = listOf(DaysOff(friday), DaysOff(friday), sundayOnly)
        assertTrue(HolidayPlanner.sharedBreaks(nov(1), nov(30), people).isEmpty())
        // Once they take the Saturday too, the long weekend is back.
        val withSaturday = people.dropLast(1) + sundayOnly.copy(marked = friday + nov(7))
        assertEquals(listOf(SharedBreak(nov(6), nov(8))), HolidayPlanner.sharedBreaks(nov(1), nov(30), withSaturday))
    }

    @Test fun leavingSomeoneOutBringsTheirBreaksBack() {
        // What the "who's going" filter does: the planner is simply asked about fewer people.
        val week = (9..13).map(::nov).toSet()
        val everyone = listOf(DaysOff(week), DaysOff(week), DaysOff(setOf(nov(9))))
        assertTrue(HolidayPlanner.sharedBreaks(nov(1), nov(30), everyone, minDays = 5).isEmpty())
        assertEquals(9, HolidayPlanner.sharedBreaks(nov(1), nov(30), everyone.take(2), minDays = 5).single().days)
    }

    @Test fun aLongerMinimumLengthHidesShortBreaks() {
        val days = setOf(nov(6)) + (16..20).map(::nov)
        val people = listOf(DaysOff(days), DaysOff(days), DaysOff(days))
        assertEquals(listOf(3, 9), HolidayPlanner.sharedBreaks(nov(1), nov(30), people).map { it.days })
        assertEquals(listOf(9), HolidayPlanner.sharedBreaks(nov(1), nov(30), people, minDays = 5).map { it.days })
        assertTrue(HolidayPlanner.sharedBreaks(nov(1), nov(30), people, minDays = 10).isEmpty())
    }

    @Test fun leaveIdeasStayUntilEveryoneHasMarkedThem() {
        val from = LocalDate.of(2027, 1, 18)
        val to = LocalDate.of(2027, 1, 31)
        val holidays = IndianHolidays.between(from, to)
        val marked = setOf(LocalDate.of(2027, 1, 25), LocalDate.of(2027, 1, 26))
        val twoMarked = listOf(DaysOff(marked), DaysOff(marked), DaysOff(emptySet()))
        assertEquals(1, HolidayPlanner.leaveIdeas(from, to, twoMarked, holidays).size)
        assertTrue(HolidayPlanner.leaveIdeas(from, to, List(3) { DaysOff(marked) }, holidays).isEmpty())
    }

    @Test fun nobodyAtAllIsNeverTogether() {
        assertTrue(HolidayPlanner.sharedBreaks(nov(1), nov(30), emptyList()).isEmpty())
        assertTrue(HolidayPlanner.leaveIdeas(nov(1), nov(30), emptyList(), IndianHolidays.between(nov(1), nov(30))).isEmpty())
    }
}
