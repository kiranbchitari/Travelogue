package com.travelogue.app.ui.holidays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.data.db.entity.HolidayEntity
import com.travelogue.app.data.db.entity.HolidayOwner
import com.travelogue.app.di.AppContainer
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

/** A marked day that only one of the two has off. */
data class SoloDay(val date: LocalDate, val owner: HolidayOwner)

/** What the planner can say about the twelve months from today. */
data class PlannerInsights(
    val myDays: Int = 0,
    val partnerDays: Int = 0,
    val breaks: List<SharedBreak> = emptyList(),
    /** Days inside [breaks] that fall within the twelve months, not counting any already gone. */
    val daysTogether: Int = 0,
    val soloDays: List<SoloDay> = emptyList(),
    /** Public holidays that land on a day both already have off every week. */
    val weekendHolidays: List<PublicHoliday> = emptyList(),
    val ideas: List<LeaveIdea> = emptyList(),
) {
    val nextBreak: SharedBreak? get() = breaks.firstOrNull()
    val longestBreak: SharedBreak? get() = breaks.maxByOrNull { it.days }
}

data class HolidaysState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val prefs: HolidayPrefs = HolidayPrefs(),
    val mine: Map<LocalDate, HolidayEntity> = emptyMap(),
    val partner: Map<LocalDate, HolidayEntity> = emptyMap(),
    /** Every stretch both people have off inside the calendar's range, oldest first. */
    val breaks: List<SharedBreak> = emptyList(),
    val breakDays: Set<LocalDate> = emptySet(),
    /** India's public holidays across the calendar's range; empty when switched off. */
    val publicHolidays: Map<LocalDate, PublicHoliday> = emptyMap(),
    val insights: PlannerInsights = PlannerInsights(),
) {
    val upcomingBreaks: List<SharedBreak> get() = breaks.filter { !it.end.isBefore(today) }
    val upcomingPublicHolidays: List<PublicHoliday>
        get() = publicHolidays.values.filter { !it.date.isBefore(today) && it.date.isBefore(today.plusYears(1)) }.sortedBy { it.date }

    fun marks(owner: HolidayOwner): Map<LocalDate, HolidayEntity> = if (owner == HolidayOwner.ME) mine else partner
    fun isOff(owner: HolidayOwner, date: LocalDate): Boolean = HolidayPlanner.isOff(date, marks(owner).keys, prefs.weeklyOff(owner))
    fun label(date: LocalDate): String = mine[date]?.label?.takeIf { it.isNotBlank() } ?: partner[date]?.label.orEmpty()
}

class HolidaysViewModel(private val c: AppContainer) : ViewModel() {
    /** First month the calendar can show; page 0 of the pager. */
    val firstMonth: YearMonth = YearMonth.now().minusMonths(MONTHS_BACK.toLong())

    private val today = MutableStateFlow(LocalDate.now())

    /** Call when the screen comes back to the front, so a planner left open overnight moves on a day. */
    fun refreshToday() { today.value = LocalDate.now() }

    val state: StateFlow<HolidaysState> = combine(c.holidays.observeAll(), c.settings.holidayPrefs, today, ::build)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HolidaysState())

    private fun build(rows: List<HolidayEntity>, prefs: HolidayPrefs, today: LocalDate): HolidaysState {
        val rangeStart = firstMonth.atDay(1)
        val rangeEnd = firstMonth.plusMonths(MONTH_COUNT - 1L).atEndOfMonth()
        val mine = rows.filter { it.owner == HolidayOwner.ME }.associateBy { it.date }
        val partner = rows.filter { it.owner == HolidayOwner.PARTNER }.associateBy { it.date }
        val labels = (mine.keys + partner.keys).associateWith { d -> mine[d]?.label?.takeIf { it.isNotBlank() } ?: partner[d]?.label.orEmpty() }
        val breaks = HolidayPlanner.sharedBreaks(rangeStart, rangeEnd, mine.keys, partner.keys, prefs, labels)
        val breakDays = HashSet<LocalDate>()
        breaks.forEach { b -> for (d in b.start.toEpochDay()..b.end.toEpochDay()) breakDays += LocalDate.ofEpochDay(d) }
        val publicHolidays = if (prefs.showIndianHolidays) IndianHolidays.between(rangeStart, rangeEnd) else emptyMap()

        // Insights look one year ahead.
        val horizon = today.plusYears(1).minusDays(1)
        fun ahead(d: LocalDate) = !d.isBefore(today) && !d.isAfter(horizon)
        fun off(owner: HolidayOwner, d: LocalDate) =
            HolidayPlanner.isOff(d, if (owner == HolidayOwner.ME) mine.keys else partner.keys, prefs.weeklyOff(owner))
        val solo = (mine.keys.filter(::ahead).filter { !off(HolidayOwner.PARTNER, it) }.map { SoloDay(it, HolidayOwner.ME) } +
            partner.keys.filter(::ahead).filter { !off(HolidayOwner.ME, it) }.map { SoloDay(it, HolidayOwner.PARTNER) }).sortedBy { it.date }
        val breaksAhead = breaks.filter { !it.end.isBefore(today) && !it.start.isAfter(horizon) }
        val insights = PlannerInsights(
            myDays = mine.keys.count(::ahead),
            partnerDays = partner.keys.count(::ahead),
            breaks = breaksAhead,
            daysTogether = breaksAhead.sumOf { (minOf(it.end, horizon).toEpochDay() - maxOf(it.start, today).toEpochDay() + 1).toInt() },
            soloDays = solo,
            weekendHolidays = publicHolidays.values.filter { ahead(it.date) && it.date.dayOfWeek in prefs.myWeeklyOff && it.date.dayOfWeek in prefs.partnerWeeklyOff }
                .sortedBy { it.date },
            ideas = HolidayPlanner.leaveIdeas(today, horizon, mine.keys, partner.keys, prefs, publicHolidays),
        )
        return HolidaysState(
            loaded = true, today = today, prefs = prefs, mine = mine, partner = partner,
            breaks = breaks, breakDays = breakDays, publicHolidays = publicHolidays, insights = insights,
        )
    }

    /** Saves the day sheet; see [com.travelogue.app.data.repository.HolidayRepository.setDays]. */
    fun setDays(start: LocalDate, end: LocalDate, mark: Set<HolidayOwner>, clear: Set<HolidayOwner>, label: String) =
        viewModelScope.launch { c.holidays.setDays(start, end, mark, clear, label) }

    /** Flips one person's day off on [date], naming it [label] when it is switched on. */
    fun toggle(owner: HolidayOwner, date: LocalDate, label: String) = viewModelScope.launch {
        c.holidays.setDay(owner, date, off = date !in state.value.marks(owner), label = label)
    }

    /** Marks everything a leave suggestion needs, for both people. */
    fun accept(idea: LeaveIdea) = viewModelScope.launch { c.holidays.markForBoth(idea.toMark) }

    fun savePrefs(prefs: HolidayPrefs) = viewModelScope.launch {
        c.settings.setHolidayPrefs(
            prefs.copy(
                myName = prefs.myName.ifBlank { HolidayPrefs.DEFAULT_MY_NAME },
                partnerName = prefs.partnerName.ifBlank { HolidayPrefs.DEFAULT_PARTNER_NAME },
            ),
        )
    }

    companion object {
        /** The calendar spans a year back and three years ahead. */
        const val MONTHS_BACK = 12
        const val MONTH_COUNT = 48
    }
}
