package com.travelogue.app.domain

import com.travelogue.app.data.db.entity.HolidayOwner
import java.time.DayOfWeek
import java.time.LocalDate

/** Who the two calendars belong to and which weekdays each of them never works. */
data class HolidayPrefs(
    val myName: String = DEFAULT_MY_NAME,
    val partnerName: String = DEFAULT_PARTNER_NAME,
    val myWeeklyOff: Set<DayOfWeek> = Weekend,
    val partnerWeeklyOff: Set<DayOfWeek> = Weekend,
    /** Show India's public holidays on the calendar and use them when suggesting leave. */
    val showIndianHolidays: Boolean = true,
) {
    fun name(owner: HolidayOwner): String = if (owner == HolidayOwner.ME) myName else partnerName
    fun weeklyOff(owner: HolidayOwner): Set<DayOfWeek> = if (owner == HolidayOwner.ME) myWeeklyOff else partnerWeeklyOff

    companion object {
        const val DEFAULT_MY_NAME = "Me"
        const val DEFAULT_PARTNER_NAME = "Wife"
        const val MAX_NAME = 24
        val Weekend: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

/** A run of consecutive days that both people have off. */
data class SharedBreak(val start: LocalDate, val end: LocalDate, val labels: List<String> = emptyList()) {
    val days: Int get() = (end.toEpochDay() - start.toEpochDay()).toInt() + 1
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * A stretch both people could have off by taking a little leave: [leaveDays] are the working days in
 * between to book, and [holidays] are public holidays inside it that are not yet marked for both.
 */
data class LeaveIdea(
    val start: LocalDate,
    val end: LocalDate,
    val leaveDays: List<LocalDate>,
    val holidays: List<PublicHoliday>,
) {
    val days: Int get() = (end.toEpochDay() - start.toEpochDay()).toInt() + 1

    /** Every day that still has to be marked for the stretch to become real, with a name for it. */
    val toMark: Map<LocalDate, String> get() = holidays.associate { it.date to it.name } + leaveDays.associateWith { LEAVE_LABEL }

    companion object {
        const val LEAVE_LABEL = "Leave"
    }
}

object HolidayPlanner {
    /** Shorter runs are just an ordinary weekend. */
    const val MIN_BREAK_DAYS = 3

    fun isOff(date: LocalDate, marked: Set<LocalDate>, weeklyOff: Set<DayOfWeek>): Boolean =
        date in marked || date.dayOfWeek in weeklyOff

    /**
     * Every run of at least [minDays] consecutive days between [from] and [to] (inclusive) on which
     * both people are off, whether through a marked holiday or a weekly day off. [labels] names the
     * marked days so a break can be shown as "Diwali" rather than only as dates.
     */
    fun sharedBreaks(
        from: LocalDate,
        to: LocalDate,
        mine: Set<LocalDate>,
        partner: Set<LocalDate>,
        prefs: HolidayPrefs,
        labels: Map<LocalDate, String> = emptyMap(),
        minDays: Int = MIN_BREAK_DAYS,
    ): List<SharedBreak> {
        val out = ArrayList<SharedBreak>()
        var runStart: LocalDate? = null
        var day = from
        fun close(last: LocalDate) {
            val start = runStart ?: return
            runStart = null
            if (last.toEpochDay() - start.toEpochDay() + 1 < minDays) return
            val names = generateSequence(start) { d -> d.plusDays(1).takeIf { !it.isAfter(last) } }
                .mapNotNull { labels[it]?.trim()?.takeIf(String::isNotEmpty) }
                .distinctBy { it.lowercase() }
                .toList()
            out += SharedBreak(start, last, names)
        }
        while (!day.isAfter(to)) {
            val both = isOff(day, mine, prefs.myWeeklyOff) && isOff(day, partner, prefs.partnerWeeklyOff)
            if (both) { if (runStart == null) runStart = day } else close(day.minusDays(1))
            day = day.plusDays(1)
        }
        close(to)
        return out
    }

    /** Most leave days a suggestion may ask for. */
    const val MAX_LEAVE_DAYS = 2

    /** A stretch has to be at least this long to be worth suggesting, by leave days spent. */
    private val minDaysForLeave = intArrayOf(3, 4, 5)

    /**
     * Suggestions for turning days off into longer breaks between [from] and [to].
     *
     * A public holiday counts as a day both could have off. Runs of such days that sit one or two
     * working days apart are joined, and the working days become the leave to take. A suggestion
     * that needs nothing marked is already a break and is left out. The best non-overlapping
     * suggestions (most days off beyond the leave spent, longer ones first) are returned in date order.
     */
    fun leaveIdeas(
        from: LocalDate,
        to: LocalDate,
        mine: Set<LocalDate>,
        partner: Set<LocalDate>,
        prefs: HolidayPrefs,
        publicHolidays: Map<LocalDate, PublicHoliday>,
        max: Int = 12,
    ): List<LeaveIdea> {
        fun bothOff(d: LocalDate) = isOff(d, mine, prefs.myWeeklyOff) && isOff(d, partner, prefs.partnerWeeklyOff)

        // Runs of days both have, or with a public holiday could have, off.
        val runs = ArrayList<Pair<LocalDate, LocalDate>>()
        var runStart: LocalDate? = null
        var day = from
        while (!day.isAfter(to)) {
            val free = bothOff(day) || day in publicHolidays
            if (free && runStart == null) runStart = day
            if (!free && runStart != null) { runs += runStart to day.minusDays(1); runStart = null }
            day = day.plusDays(1)
        }
        runStart?.let { runs += it to to }

        val candidates = ArrayList<LeaveIdea>()
        for (i in runs.indices) {
            val leave = ArrayList<LocalDate>()
            var j = i
            while (true) {
                val start = runs[i].first
                val end = runs[j].second
                val length = (end.toEpochDay() - start.toEpochDay()).toInt() + 1
                val unmarked = (start.toEpochDay()..end.toEpochDay()).map(LocalDate::ofEpochDay)
                    .filter { it !in leave && !bothOff(it) }.mapNotNull(publicHolidays::get)
                if (length >= minDaysForLeave[leave.size] && (leave.isNotEmpty() || unmarked.isNotEmpty())) {
                    candidates += LeaveIdea(start, end, leave.toList(), unmarked)
                }
                val next = runs.getOrNull(j + 1) ?: break
                val gap = (end.toEpochDay() + 1 until next.first.toEpochDay()).map(LocalDate::ofEpochDay)
                if (leave.size + gap.size > MAX_LEAVE_DAYS) break
                leave += gap
                j++
            }
        }

        val picked = ArrayList<LeaveIdea>()
        candidates.sortedWith(compareByDescending<LeaveIdea> { it.days - it.leaveDays.size }.thenByDescending { it.days }.thenBy { it.start })
            .forEach { c ->
                if (picked.size < max && picked.none { !c.end.isBefore(it.start) && !c.start.isAfter(it.end) }) picked += c
            }
        return picked.sortedBy { it.start }
    }
}
