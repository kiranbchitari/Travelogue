package com.travelogue.app.ui.holidays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.data.db.entity.HolidayEntity
import com.travelogue.app.di.AppContainer
import com.travelogue.app.domain.DaysOff
import com.travelogue.app.domain.HolidayPerson
import com.travelogue.app.domain.HolidayPlanner
import com.travelogue.app.domain.HolidayPrefs
import com.travelogue.app.domain.IndianHolidays
import com.travelogue.app.domain.LeaveIdea
import com.travelogue.app.domain.PublicHoliday
import com.travelogue.app.domain.SharedBreak
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** A marked day that some of the people going have off and others do not. */
data class PartDay(val date: LocalDate, val off: List<HolidayPerson>, val working: List<HolidayPerson>)

/** Narrows "time off together": who is left out of it, and how long a stretch has to be to count. */
data class PlanFilter(val leftOut: Set<String> = emptySet(), val minDays: Int = HolidayPlanner.MIN_BREAK_DAYS) {
    companion object {
        val LENGTHS = listOf(HolidayPlanner.MIN_BREAK_DAYS, 5, 7)
    }
}

/** What the planner can say about the twelve months from today. */
data class PlannerInsights(
    /** Marked days ahead for each person, by id. */
    val daysOff: Map<String, Int> = emptyMap(),
    val breaks: List<SharedBreak> = emptyList(),
    /** Days inside [breaks] that fall within the twelve months, not counting any already gone. */
    val daysTogether: Int = 0,
    val partDays: List<PartDay> = emptyList(),
    /** Public holidays that land on a day everyone going already has off every week. */
    val weekendHolidays: List<PublicHoliday> = emptyList(),
    val ideas: List<LeaveIdea> = emptyList(),
) {
    val markedDays: Int get() = daysOff.values.sum()
    val nextBreak: SharedBreak? get() = breaks.firstOrNull()
    val longestBreak: SharedBreak? get() = breaks.maxByOrNull { it.days }
}

data class HolidaysState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val prefs: HolidayPrefs = HolidayPrefs(),
    /** Each person's marked days, by person id. */
    val marks: Map<String, Map<LocalDate, HolidayEntity>> = emptyMap(),
    /** Who "together" means: everyone, unless the filter leaves some people out. */
    val going: List<HolidayPerson> = prefs.people,
    /** How many days in a row count as time off together. */
    val minDays: Int = HolidayPlanner.MIN_BREAK_DAYS,
    /** Every stretch the people going have off inside the calendar's range, oldest first. */
    val breaks: List<SharedBreak> = emptyList(),
    val breakDays: Set<LocalDate> = emptySet(),
    /** India's public holidays across the calendar's range; empty when switched off. */
    val publicHolidays: Map<LocalDate, PublicHoliday> = emptyMap(),
    val insights: PlannerInsights = PlannerInsights(),
) {
    val people: List<HolidayPerson> get() = prefs.people
    val upcomingBreaks: List<SharedBreak> get() = breaks.filter { !it.end.isBefore(today) }
    val upcomingPublicHolidays: List<PublicHoliday>
        get() = publicHolidays.values.filter { !it.date.isBefore(today) && it.date.isBefore(today.plusYears(1)) }.sortedBy { it.date }

    fun marks(person: HolidayPerson): Map<LocalDate, HolidayEntity> = marks[person.id].orEmpty()
    fun isOff(person: HolidayPerson, date: LocalDate): Boolean = date in marks(person) || date.dayOfWeek in person.weeklyOff
    /** True when everyone going is off, through a marked day or a weekly day off. */
    fun allOff(date: LocalDate): Boolean = going.all { isOff(it, date) }
    /** The first name anyone gave the day. */
    fun label(date: LocalDate): String = people.firstNotNullOfOrNull { p -> marks(p)[date]?.label?.takeIf { it.isNotBlank() } }.orEmpty()
}

class HolidaysViewModel(private val c: AppContainer) : ViewModel() {
    /** First month the calendar can show; page 0 of the pager. */
    val firstMonth: YearMonth = YearMonth.now().minusMonths(MONTHS_BACK.toLong())

    private val today = MutableStateFlow(LocalDate.now())

    /** Kept only while the planner is open: it always starts with everyone and the shortest length. */
    private val filter = MutableStateFlow(PlanFilter())

    /** Call when the screen comes back to the front, so a planner left open overnight moves on a day. */
    fun refreshToday() { today.value = LocalDate.now() }

    val state: StateFlow<HolidaysState> = combine(c.holidays.observeAll(), c.settings.holidayPrefs, today, filter, ::build)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HolidaysState())

    private fun build(rows: List<HolidayEntity>, prefs: HolidayPrefs, today: LocalDate, filter: PlanFilter): HolidaysState {
        val rangeStart = firstMonth.atDay(1)
        val rangeEnd = firstMonth.plusMonths(MONTH_COUNT - 1L).atEndOfMonth()
        val people = prefs.people
        // Days marked for someone who is no longer in the planner are ignored.
        val byOwner = rows.groupBy { it.owner }
        val marks = people.associate { p -> p.id to byOwner[p.id].orEmpty().associateBy { it.date } }
        fun days(p: HolidayPerson) = marks.getValue(p.id).keys
        // "Together" takes at least two people; a filter that leaves fewer falls back to everyone.
        val going = people.filter { it.id !in filter.leftOut }.takeIf { it.size >= 2 } ?: people
        val goingDays = going.map { DaysOff(days(it), it.weeklyOff) }
        fun off(p: HolidayPerson, d: LocalDate) = d in days(p) || d.dayOfWeek in p.weeklyOff

        val labels = HashMap<LocalDate, String>()
        people.forEach { p -> marks.getValue(p.id).values.forEach { h -> if (h.label.isNotBlank()) labels.putIfAbsent(h.date, h.label) } }
        val breaks = HolidayPlanner.sharedBreaks(rangeStart, rangeEnd, goingDays, labels, filter.minDays)
        val breakDays = HashSet<LocalDate>()
        breaks.forEach { b -> for (d in b.start.toEpochDay()..b.end.toEpochDay()) breakDays += LocalDate.ofEpochDay(d) }
        val publicHolidays = if (prefs.showIndianHolidays) IndianHolidays.between(rangeStart, rangeEnd) else emptyMap()

        // Insights look one year ahead.
        val horizon = today.plusYears(1).minusDays(1)
        fun ahead(d: LocalDate) = !d.isBefore(today) && !d.isAfter(horizon)
        val partDays = going.flatMap(::days).distinct().filter(::ahead).sorted().mapNotNull { d ->
            val (off, working) = going.partition { off(it, d) }
            if (working.isEmpty()) null else PartDay(d, off, working)
        }
        val breaksAhead = breaks.filter { !it.end.isBefore(today) && !it.start.isAfter(horizon) }
        val insights = PlannerInsights(
            daysOff = people.associate { p -> p.id to days(p).count(::ahead) },
            breaks = breaksAhead,
            daysTogether = breaksAhead.sumOf { (minOf(it.end, horizon).toEpochDay() - maxOf(it.start, today).toEpochDay() + 1).toInt() },
            partDays = partDays,
            weekendHolidays = publicHolidays.values.filter { h -> ahead(h.date) && going.all { h.date.dayOfWeek in it.weeklyOff } }.sortedBy { it.date },
            ideas = HolidayPlanner.leaveIdeas(today, horizon, goingDays, publicHolidays).filter { it.days >= filter.minDays },
        )
        return HolidaysState(
            loaded = true, today = today, prefs = prefs, marks = marks, going = going, minDays = filter.minDays,
            breaks = breaks, breakDays = breakDays, publicHolidays = publicHolidays, insights = insights,
        )
    }

    /** Counts a person in or out of "together". At least two people always stay in. */
    fun toggleGoing(person: HolidayPerson) {
        val s = state.value
        val isGoing = s.going.any { it.id == person.id }
        if (isGoing && s.going.size <= 2) return
        // Rebuilt from who is going now, so ids of people since removed do not pile up.
        val leftOut = s.people.map { it.id }.filter { id -> s.going.none { it.id == id } }.toSet()
        filter.value = filter.value.copy(leftOut = if (isGoing) leftOut + person.id else leftOut - person.id)
    }

    fun setMinDays(days: Int) { filter.value = filter.value.copy(minDays = days) }

    /** Saves the day sheet; see [com.travelogue.app.data.repository.HolidayRepository.setDays]. */
    fun setDays(start: LocalDate, end: LocalDate, mark: Set<String>, clear: Set<String>, label: String) =
        viewModelScope.launch { c.holidays.setDays(start, end, mark, clear, label) }

    /** Flips one person's day off on [date], naming it [label] when it is switched on. */
    fun toggle(person: HolidayPerson, date: LocalDate, label: String) = viewModelScope.launch {
        c.holidays.setDay(person.id, date, off = date !in state.value.marks(person), label = label)
    }

    /** Marks everything a leave suggestion needs, for everyone going. */
    fun accept(idea: LeaveIdea) = viewModelScope.launch { c.holidays.markFor(state.value.going.map { it.id }, idea.toMark) }

    /** Saves names and weekly days off. Anyone no longer in [prefs] is removed along with their marked days. */
    fun savePrefs(prefs: HolidayPrefs) = viewModelScope.launch {
        val kept = prefs.extras.map { it.id }.toSet()
        val removed = state.value.prefs.extras.filter { it.id !in kept }
        c.settings.setHolidayPrefs(
            prefs.copy(
                myName = prefs.myName.ifBlank { HolidayPrefs.DEFAULT_MY_NAME },
                partnerName = prefs.partnerName.ifBlank { HolidayPrefs.DEFAULT_PARTNER_NAME },
            ),
        )
        removed.forEach { c.holidays.removePerson(it.id) }
    }

    companion object {
        /** The calendar spans a year back and three years ahead. */
        const val MONTHS_BACK = 12
        const val MONTH_COUNT = 48
    }
}
