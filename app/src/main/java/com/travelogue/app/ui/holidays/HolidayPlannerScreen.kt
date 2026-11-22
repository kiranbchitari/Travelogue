package com.travelogue.app.ui.holidays

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Palette
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.entity.HolidayEntity
import com.travelogue.app.data.db.entity.HolidayOwner
import com.travelogue.app.data.repository.HolidayRepository
import com.travelogue.app.domain.HolidayPlanner
import com.travelogue.app.domain.HolidayPrefs
import com.travelogue.app.domain.LeaveIdea
import com.travelogue.app.domain.PublicHoliday
import com.travelogue.app.domain.SharedBreak
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private const val MAX_BREAKS_SHOWN = 6
private const val HOLIDAYS_COLLAPSED = 5
private const val IDEAS_COLLAPSED = 4

/** Weekdays in the order this locale starts its week. */
private fun orderedWeekdays(): List<DayOfWeek> {
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    return (0L..6L).map { first.plus(it) }
}

/** Each person has a colour of their own; time together borrows the app's warm accent. */
@Composable
private fun ownerColor(owner: HolidayOwner): Color {
    val dark = Travel.colors.isDark
    return when (owner) {
        HolidayOwner.ME -> if (dark) Color(0xFF8C98FF) else Color(0xFF3D4EDB)
        HolidayOwner.PARTNER -> if (dark) Color(0xFFF29AB0) else Color(0xFFD6457A)
    }
}

private fun listNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    2 -> "${names[0]} and ${names[1]}"
    else -> "${names[0]}, ${names[1]} and ${names.size - 2} more"
}

/**
 * Two calendars on one grid: the user's days off and their partner's. Stretches of three or more
 * days both have off run across the grid as a warm band, and are listed underneath with what the
 * planner can make of the year ahead: leave worth taking and India's public holidays.
 */
@Composable
fun HolidayPlannerScreen(nav: Nav) {
    val vm = appViewModel { HolidaysViewModel(this) }
    val s by vm.state.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = HolidaysViewModel.MONTHS_BACK) { HolidaysViewModel.MONTH_COUNT }
    val scope = rememberCoroutineScope()
    var editingDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var showPrefs by rememberSaveable { mutableStateOf(false) }
    var allHolidays by rememberSaveable { mutableStateOf(false) }
    var allIdeas by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(vm) {
        vm.refreshToday()
        onPauseOrDispose { }
    }

    val month = vm.firstMonth.plusMonths(pager.currentPage.toLong())
    fun showMonth(m: YearMonth) {
        val page = ((m.year - vm.firstMonth.year) * 12 + (m.monthValue - vm.firstMonth.monthValue)).coerceIn(0, HolidaysViewModel.MONTH_COUNT - 1)
        scope.launch { pager.animateScrollToPage(page) }
    }
    val sectionPadding = Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxxl, bottom = Space.md)
    val listCard = Modifier.padding(horizontal = Space.gutter).fillMaxWidth()
    val listPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm)

    ScreenScaffold(
        "Holiday planner",
        onBack = nav::back,
        subtitle = "${s.prefs.myName} & ${s.prefs.partnerName}",
        actions = { CircleIconButton(Icons.Rounded.Tune, { showPrefs = true }, contentDescription = "Names and weekly days off") },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + Space.sm, bottom = padding.calculateBottomPadding() + Space.huge),
        ) {
            // Sections come and go as days are marked; keys keep the scroll position on the same section.
            item(key = "calendar") {
                SurfaceCard(Modifier.padding(horizontal = Space.gutter).fillMaxWidth(), shape = Shapes.xl, contentPadding = PaddingValues(Space.md)) {
                    MonthHeader(
                        month = month,
                        s = s,
                        showToday = pager.currentPage != HolidaysViewModel.MONTHS_BACK,
                        canGoBack = pager.currentPage > 0,
                        canGoForward = pager.currentPage < HolidaysViewModel.MONTH_COUNT - 1,
                        onPrevious = { showMonth(month.minusMonths(1)) },
                        onNext = { showMonth(month.plusMonths(1)) },
                        onToday = { showMonth(YearMonth.from(s.today)) },
                    )
                    Spacer(Modifier.height(Space.lg))
                    WeekdayHeader()
                    Spacer(Modifier.height(Space.xs))
                    HorizontalPager(pager, key = { it }) { page ->
                        MonthGrid(vm.firstMonth.plusMonths(page.toLong()), s, onDay = { editingDay = it.toEpochDay() })
                    }
                    Spacer(Modifier.height(Space.sm))
                    Legend(s)
                }
            }

            val markedThisMonth = (s.mine.keys + s.partner.keys).filter { YearMonth.from(it) == month }.sorted()
            if (markedThisMonth.isNotEmpty()) {
                item(key = "marked") {
                    SectionHeader("Marked in ${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}", modifier = sectionPadding)
                    SurfaceCard(listCard, contentPadding = listPadding) {
                        markedThisMonth.forEachIndexed { i, d ->
                            if (i > 0) Hairline()
                            MarkedDayRow(d, s, onClick = { editingDay = d.toEpochDay() })
                        }
                    }
                }
            }

            item(key = "breaks") {
                SectionHeader(
                    "Time off together",
                    subtitle = "Stretches of ${HolidayPlanner.MIN_BREAK_DAYS} or more days you both have off",
                    modifier = sectionPadding,
                )
                val upcoming = s.upcomingBreaks.take(MAX_BREAKS_SHOWN)
                SurfaceCard(listCard, contentPadding = listPadding) {
                    if (upcoming.isEmpty()) {
                        Text(
                            "Nothing yet. Tap a day on the calendar to mark who is off. " +
                                "When a day you both have off sits next to a weekend, the long weekend appears here.",
                            style = Travel.type.body, color = Travel.colors.inkSoft, modifier = Modifier.padding(vertical = Space.sm),
                        )
                    }
                    upcoming.forEachIndexed { i, b ->
                        if (i > 0) Hairline()
                        ActionRow(
                            title = Fmt.dateRange(b.start, b.end),
                            detail = (listOf(Fmt.days(b.days)) + b.labels).joinToString(" · "),
                            action = "Plan trip",
                            onClick = { showMonth(YearMonth.from(b.start)) },
                            onAction = { nav.planTrip(maxOf(b.start, s.today), b.end) },
                        )
                    }
                }
            }

            item(key = "insights") {
                SectionHeader("Insights", subtitle = "The next 12 months at a glance", modifier = sectionPadding)
                InsightsCard(s, onDay = { editingDay = it.toEpochDay() }, onShow = { showMonth(YearMonth.from(it)) }, modifier = listCard)
            }

            if (s.insights.ideas.isNotEmpty()) {
                item(key = "ideas") {
                    SectionHeader("Leave ideas", subtitle = "A little leave that opens up a long break", modifier = sectionPadding)
                    val ideas = s.insights.ideas
                    SurfaceCard(listCard, contentPadding = listPadding) {
                        (if (allIdeas) ideas else ideas.take(IDEAS_COLLAPSED)).forEachIndexed { i, idea ->
                            if (i > 0) Hairline()
                            ActionRow(
                                title = "${Fmt.days(idea.days)} off · ${Fmt.dateRange(idea.start, idea.end)}",
                                detail = ideaDetail(idea),
                                action = "Mark",
                                onClick = { showMonth(YearMonth.from(idea.start)) },
                                onAction = { vm.accept(idea); showMonth(YearMonth.from(idea.start)) },
                            )
                        }
                        if (ideas.size > IDEAS_COLLAPSED) ShowAllRow(allIdeas, ideas.size) { allIdeas = !allIdeas }
                    }
                }
            }

            val holidays = s.upcomingPublicHolidays
            if (holidays.isNotEmpty()) {
                item(key = "indian-holidays") {
                    SectionHeader("Indian holidays", subtitle = "Tap who gets each one off", modifier = sectionPadding)
                    SurfaceCard(listCard, contentPadding = listPadding) {
                        (if (allHolidays) holidays else holidays.take(HOLIDAYS_COLLAPSED)).forEachIndexed { i, h ->
                            if (i > 0) Hairline()
                            PublicHolidayRow(h, s, onShow = { showMonth(YearMonth.from(h.date)) }, onToggle = { owner -> vm.toggle(owner, h.date, h.name) })
                        }
                        if (holidays.size > HOLIDAYS_COLLAPSED) ShowAllRow(allHolidays, holidays.size) { allHolidays = !allHolidays }
                    }
                    Text(
                        "Central government holiday list. Eid, Muharram and Milad-un-Nabi follow the moon and can move by a day.",
                        style = Travel.type.bodySmall, color = Travel.colors.inkFaint,
                        modifier = Modifier.padding(horizontal = Space.gutter + Space.xs).padding(top = Space.sm),
                    )
                }
            }
        }
    }

    // A sheet restored after the app was killed must wait for the saved days and names to load:
    // filled from the empty starting state, its Save would overwrite them.
    editingDay?.takeIf { s.loaded }?.let { epochDay ->
        val date = LocalDate.ofEpochDay(epochDay)
        DaySheet(date, s, onDismiss = { editingDay = null }) { until, mark, clear, label ->
            vm.setDays(date, until, mark, clear, label)
            editingDay = null
        }
    }
    if (showPrefs && s.loaded) {
        PrefsSheet(s.prefs, onDismiss = { showPrefs = false }) { vm.savePrefs(it); showPrefs = false }
    }
}

private fun ideaDetail(idea: LeaveIdea): String {
    val holidays = listNames(idea.holidays.map { it.name })
    val leave = idea.leaveDays.joinToString(" and ") { Fmt.weekdayShort(it) }
    return when {
        idea.leaveDays.isEmpty() -> "No leave needed if you both get $holidays off"
        holidays.isEmpty() -> "Take $leave as leave"
        else -> "Take $leave as leave, with $holidays off"
    }
}

// =============================================================================================
// Calendar
// =============================================================================================

@Composable
private fun MonthHeader(
    month: YearMonth,
    s: HolidaysState,
    showToday: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val together = s.breakDays.count { YearMonth.from(it) == month }
    val holidays = s.publicHolidays.keys.count { YearMonth.from(it) == month }
    val summary = listOfNotNull(
        month.year.toString(),
        together.takeIf { it > 0 }?.let { "${Fmt.days(it)} together" },
        holidays.takeIf { it > 0 }?.let { if (it == 1) "1 holiday" else "$it holidays" },
    ).joinToString(" · ")
    Column(Modifier.padding(start = Space.sm, top = Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).voice(),
                style = Travel.type.headline, color = Travel.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (showToday) {
                PillAction("Today", onToday)
                Spacer(Modifier.width(Space.sm))
            }
            CircleIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, { if (canGoBack) onPrevious() }, contentDescription = "Previous month", size = 38.dp)
            Spacer(Modifier.width(Space.sm))
            CircleIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, { if (canGoForward) onNext() }, contentDescription = "Next month", size = 38.dp)
        }
        // On its own line: beside the buttons there is not always room for it.
        Text(summary, style = Travel.type.bodySmall, color = Travel.colors.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PillAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = Travel.type.overline, color = Travel.colors.ink, maxLines = 1,
        modifier = modifier.clip(Shapes.pill).border(1.dp, Travel.colors.line, Shapes.pill).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

@Composable
private fun WeekdayHeader() {
    val days = remember { orderedWeekdays() }
    Row(Modifier.fillMaxWidth()) {
        days.forEach {
            Text(
                it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                style = Travel.type.overline, color = Travel.colors.inkFaint, textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Always six rows, so every month is the same height and the pager does not jump. The spare cells
 * carry the neighbouring months' days, faded.
 */
@Composable
private fun MonthGrid(month: YearMonth, s: HolidaysState, onDay: (LocalDate) -> Unit) {
    val firstDayOfWeek = remember { orderedWeekdays().first() }
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    Column(Modifier.fillMaxWidth()) {
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val date = first.plusDays((week * 7 + column - lead).toLong())
                    if (YearMonth.from(date) == month) {
                        DayCell(date, column, s, onDay, Modifier.weight(1f))
                    } else {
                        Box(Modifier.weight(1f).height(DayCellHeight), contentAlignment = Alignment.Center) {
                            Text(date.dayOfMonth.toString(), style = Travel.type.label, color = Travel.colors.inkFaint.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}

private val DayCellHeight = 50.dp
private val DayDiscSize = 42.dp

/**
 * One day: a round disc tinted for whoever is off, coloured dots for marked days, a filled number
 * for today and an emoji for a public holiday. Days inside a shared break sit on a band that joins
 * up with their neighbours.
 */
@Composable
private fun DayCell(date: LocalDate, column: Int, s: HolidaysState, onClick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val c = Travel.colors
    val mine = date in s.mine
    val partner = date in s.partner
    val meOff = s.isOff(HolidayOwner.ME, date)
    val partnerOff = s.isOff(HolidayOwner.PARTNER, date)
    val inBreak = date in s.breakDays
    val holiday = s.publicHolidays[date]
    val isToday = date == s.today
    val myColor = ownerColor(HolidayOwner.ME)
    val partnerColor = ownerColor(HolidayOwner.PARTNER)

    val disc = when {
        inBreak -> Color.Transparent
        meOff && partnerOff -> c.accentSoft
        mine -> myColor.copy(alpha = 0.16f)
        partner -> partnerColor.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val description = buildString {
        append(Fmt.weekday(date))
        if (holiday != null) append(", ${holiday.name}")
        if (mine) append(", ${s.prefs.myName} off")
        if (partner) append(", ${s.prefs.partnerName} off")
        if (inBreak) append(", time off together")
    }
    Box(modifier.height(DayCellHeight), contentAlignment = Alignment.Center) {
        if (inBreak) {
            // Square off the side that continues into the next day so the band reads as one piece.
            val startsHere = column == 0 || date.dayOfMonth == 1 || date.minusDays(1) !in s.breakDays
            val endsHere = column == 6 || date.dayOfMonth == date.lengthOfMonth() || date.plusDays(1) !in s.breakDays
            val start = if (startsHere) 50 else 0
            val end = if (endsHere) 50 else 0
            Box(Modifier.fillMaxWidth().height(DayDiscSize).background(c.goldSoft, RoundedCornerShape(start, end, end, start)))
        }
        Column(
            Modifier
                .widthIn(max = DayDiscSize).fillMaxWidth().aspectRatio(1f)
                .clip(CircleShape)
                .background(disc)
                .clickable(role = Role.Button) { onClick(date) }
                .clearAndSetSemantics { contentDescription = description; role = Role.Button },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(26.dp).then(if (isToday) Modifier.background(c.accent, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                Text(
                    date.dayOfMonth.toString(),
                    style = Travel.type.label,
                    color = when {
                        isToday -> c.onAccent
                        date.isBefore(s.today) -> c.inkFaint
                        else -> c.ink
                    },
                )
            }
            Row(Modifier.padding(top = 1.dp).height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (mine) Box(Modifier.size(5.dp).background(myColor, CircleShape))
                if (partner) Box(Modifier.size(5.dp).background(partnerColor, CircleShape))
            }
        }
        if (holiday != null) Text(holiday.emoji, fontSize = 11.sp, lineHeight = 13.sp, modifier = Modifier.align(Alignment.TopEnd))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(s: HolidaysState) {
    val c = Travel.colors
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        HolidayOwner.entries.forEach { owner ->
            LegendItem(s.prefs.name(owner)) { Box(Modifier.size(8.dp).background(ownerColor(owner), CircleShape)) }
        }
        LegendItem("Together ${HolidayPlanner.MIN_BREAK_DAYS}+ days") { Box(Modifier.size(width = 20.dp, height = 10.dp).background(c.goldSoft, Shapes.pill)) }
        if (s.prefs.showIndianHolidays) LegendItem("Indian holiday") { Text("🪔", fontSize = 11.sp) }
    }
}

@Composable
private fun LegendItem(text: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Spacer(Modifier.width(6.dp))
        Text(text, style = Travel.type.bodySmall, color = Travel.colors.inkSoft, maxLines = 1)
    }
}

// =============================================================================================
// Lists under the calendar
// =============================================================================================

/** Last row of a long list: expands it, or folds it back. */
@Composable
private fun ShowAllRow(expanded: Boolean, count: Int, onToggle: () -> Unit) {
    Hairline()
    Text(
        if (expanded) "SHOW FEWER" else "SHOW ALL $count",
        style = Travel.type.overline, color = Travel.colors.ink, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(Shapes.sm).clickable(role = Role.Button, onClick = onToggle).padding(vertical = Space.lg),
    )
}

/** A two-line row that opens something when tapped and carries one pill action. */
@Composable
private fun ActionRow(title: String, detail: String, action: String, onClick: () -> Unit, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(Shapes.sm).clickable(onClick = onClick).padding(vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Travel.type.titleSmall, color = Travel.colors.ink)
            Text(detail, style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        }
        Spacer(Modifier.width(Space.md))
        PillAction(action, onAction)
    }
}

@Composable
private fun MarkedDayRow(date: LocalDate, s: HolidaysState, onClick: () -> Unit) {
    val label = s.label(date)
    Row(Modifier.fillMaxWidth().clip(Shapes.sm).clickable(onClick = onClick).padding(vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.weekdayShort(date), style = Travel.type.titleSmall, color = Travel.colors.ink)
            if (label.isNotEmpty()) Text(label, style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            HolidayOwner.entries.forEach { owner ->
                if (date in s.marks(owner)) Tag(s.prefs.name(owner), color = ownerColor(owner))
            }
        }
    }
}

/** Round toggle carrying a person's initial: filled in their colour when they have the day off. */
@Composable
private fun PersonToggle(owner: HolidayOwner, name: String, on: Boolean, onClick: () -> Unit, size: Dp = 36.dp) {
    val c = Travel.colors
    val color = ownerColor(owner)
    val fill by animateColorAsState(if (on) color else Color.Transparent, label = "personFill")
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, if (on) color else c.line, CircleShape)
            .clickable(role = Role.Switch, onClick = onClick)
            .clearAndSetSemantics { contentDescription = if (on) "$name is off, tap to clear" else "Mark $name off" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            style = Travel.type.label,
            // Person colours are deep in the light theme and pale in the dark one.
            color = if (!on) c.inkFaint else if (c.isDark) Palette.Ink else Color.White,
        )
    }
}

@Composable
private fun PublicHolidayRow(h: PublicHoliday, s: HolidaysState, onShow: () -> Unit, onToggle: (HolidayOwner) -> Unit) {
    val c = Travel.colors
    val onSharedWeekend = h.date.dayOfWeek in s.prefs.myWeeklyOff && h.date.dayOfWeek in s.prefs.partnerWeeklyOff
    Row(Modifier.fillMaxWidth().clip(Shapes.sm).clickable(onClick = onShow).padding(vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) { Text(h.emoji, fontSize = 18.sp) }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(h.name, style = Travel.type.titleSmall, color = c.ink)
            Text(
                Fmt.weekdayShort(h.date) + (if (h.date.year != s.today.year) " ${h.date.year}" else "") + if (onSharedWeekend) " · already a day off" else "",
                style = Travel.type.bodySmall, color = c.inkFaint,
            )
        }
        Spacer(Modifier.width(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            HolidayOwner.entries.forEach { owner ->
                PersonToggle(owner, s.prefs.name(owner), on = h.date in s.marks(owner), onClick = { onToggle(owner) })
            }
        }
    }
}

// =============================================================================================
// Insights
// =============================================================================================

@Composable
private fun InsightsCard(s: HolidaysState, onDay: (LocalDate) -> Unit, onShow: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val c = Travel.colors
    val i = s.insights
    SurfaceCard(modifier, contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Figure(i.myDays.toString(), "${s.prefs.myName} off", ownerColor(HolidayOwner.ME), Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
            Figure(i.partnerDays.toString(), "${s.prefs.partnerName} off", ownerColor(HolidayOwner.PARTNER), Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
            Figure(i.daysTogether.toString(), "Together", c.gold, Modifier.weight(1f))
        }
        Hairline()
        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
            val next = i.nextBreak
            val longest = i.longestBreak
            if (i.myDays + i.partnerDays == 0) {
                Insight(
                    "👋", "Start with the holidays you already know",
                    if (s.prefs.showIndianHolidays) "Tap the round buttons beside each Indian holiday below, or any day on the calendar." else "Tap any day on the calendar to mark who is off.",
                )
            }
            if (next != null) {
                val wait = (next.start.toEpochDay() - s.today.toEpochDay()).toInt()
                Insight(
                    "🧳",
                    when {
                        wait <= 0 -> "You are both off right now"
                        wait == 1 -> "Time off together starts tomorrow"
                        else -> "Next time off together in $wait days"
                    },
                    (listOf(Fmt.dateRange(next.start, next.end), Fmt.days(next.days)) + next.labels).joinToString(" · "),
                    onClick = { onShow(next.start) },
                )
            } else if (i.myDays + i.partnerDays > 0) {
                Insight("🧩", "No long break together yet", "Your days off do not line up into ${HolidayPlanner.MIN_BREAK_DAYS} days in a row. The leave ideas below can help.")
            }
            if (longest != null && longest != next) {
                Insight(
                    "🏆", "Longest stretch together: ${Fmt.days(longest.days)}",
                    (listOf(Fmt.dateRange(longest.start, longest.end)) + longest.labels).joinToString(" · "),
                    onClick = { onShow(longest.start) },
                )
            }
            i.soloDays.firstOrNull()?.let { first ->
                Insight(
                    "🙋",
                    if (i.soloDays.size == 1) "1 day only one of you has off" else "${i.soloDays.size} days only one of you has off",
                    "Next is ${Fmt.weekdayShort(first.date)}, when only ${s.prefs.name(first.owner)} is off. Tap to change it.",
                    onClick = { onDay(first.date) },
                )
            }
            if (i.weekendHolidays.isNotEmpty()) {
                val n = i.weekendHolidays.size
                Insight(
                    "😴",
                    if (n == 1) "1 holiday lands on your weekend" else "$n holidays land on your weekend",
                    "${listNames(i.weekendHolidays.map { it.name })} fall on a day you both already have off, so they add no extra time.",
                )
            }
        }
    }
}

@Composable
private fun Figure(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = Space.lg, horizontal = Space.xs), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Travel.type.number.copy(fontSize = 28.sp, lineHeight = 32.sp), color = color, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Overline(label)
    }
}

@Composable
private fun Insight(emoji: String, title: String, detail: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(Shapes.sm).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = Space.md),
        verticalAlignment = Alignment.Top,
    ) {
        Text(emoji, fontSize = 20.sp, modifier = Modifier.width(34.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Travel.type.titleSmall, color = Travel.colors.ink)
            Text(detail, style = Travel.type.bodySmall, color = Travel.colors.inkSoft)
        }
    }
}

// =============================================================================================
// Sheets
// =============================================================================================

/** Marks one day, or a stretch starting on it, as off for either person or both. */
@Composable
private fun DaySheet(
    date: LocalDate,
    s: HolidaysState,
    onDismiss: () -> Unit,
    onSave: (until: LocalDate, mark: Set<HolidayOwner>, clear: Set<HolidayOwner>, label: String) -> Unit,
) {
    val holiday = s.publicHolidays[date]
    // How the day stood when the sheet opened: only a switch the user turns off clears anything.
    val wasOff = remember(date) { HolidayOwner.entries.filter { date in s.marks(it) }.toSet() }
    var meOff by remember(date) { mutableStateOf(HolidayOwner.ME in wasOff) }
    var partnerOff by remember(date) { mutableStateOf(HolidayOwner.PARTNER in wasOff) }
    var label by remember(date) { mutableStateOf(s.label(date).ifEmpty { holiday?.name.orEmpty().take(HolidayEntity.MAX_LABEL) }) }
    var until by remember(date) { mutableStateOf<LocalDate?>(null) }
    val untilError = until?.let {
        when {
            it.isBefore(date) -> "Pick ${Fmt.dayMonth(date)} or a later day"
            it.toEpochDay() - date.toEpochDay() >= HolidayRepository.MAX_RANGE_DAYS -> "Pick a stretch of up to a year"
            else -> null
        }
    }
    fun weeklyNote(owner: HolidayOwner) =
        if (date.dayOfWeek in s.prefs.weeklyOff(owner)) "Already off every ${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}" else null

    TravelSheet(onDismiss, title = Fmt.weekday(date)) {
        if (holiday != null) {
            Text("${holiday.emoji}  ${holiday.name} · Indian public holiday", style = Travel.type.body, color = Travel.colors.inkSoft)
            Spacer(Modifier.height(Space.lg))
        }
        Overline("Who is off?")
        Spacer(Modifier.height(Space.sm))
        SwitchRow(s.prefs.myName, meOff, { meOff = it }, description = weeklyNote(HolidayOwner.ME))
        Spacer(Modifier.height(Space.sm))
        SwitchRow(s.prefs.partnerName, partnerOff, { partnerOff = it }, description = weeklyNote(HolidayOwner.PARTNER))
        Spacer(Modifier.height(Space.lg))
        FormField(
            "Name (optional)", label, { label = it.take(HolidayEntity.MAX_LABEL) },
            placeholder = "Diwali, annual leave…",
        )
        Spacer(Modifier.height(Space.md))
        DateField(
            "Until (optional)", until, { until = it },
            error = untilError, placeholder = "Just this day", onClear = { until = null },
        )
        Spacer(Modifier.height(Space.xl))
        PrimaryButton(
            "Save",
            {
                val mark = buildSet { if (meOff) add(HolidayOwner.ME); if (partnerOff) add(HolidayOwner.PARTNER) }
                onSave(until ?: date, mark, wasOff - mark, label)
            },
            Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Check,
            enabled = untilError == null,
        )
    }
}

/** Names for the two calendars, the weekdays each person never works, and the public holiday layer. */
@Composable
private fun PrefsSheet(prefs: HolidayPrefs, onDismiss: () -> Unit, onSave: (HolidayPrefs) -> Unit) {
    var myName by remember { mutableStateOf(prefs.myName) }
    var partnerName by remember { mutableStateOf(prefs.partnerName) }
    var myWeekly by remember { mutableStateOf(prefs.myWeeklyOff) }
    var partnerWeekly by remember { mutableStateOf(prefs.partnerWeeklyOff) }
    var indianHolidays by remember { mutableStateOf(prefs.showIndianHolidays) }

    TravelSheet(onDismiss, title = "Names and days off") {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            FormField("Your name", myName, { myName = it.take(HolidayPrefs.MAX_NAME) }, capitalization = KeyboardCapitalization.Words)
            Spacer(Modifier.height(Space.sm))
            Overline("Your weekly days off")
            Spacer(Modifier.height(Space.sm))
            WeekdayToggles(myWeekly) { myWeekly = it }
            Spacer(Modifier.height(Space.xl))
            FormField("Partner's name", partnerName, { partnerName = it.take(HolidayPrefs.MAX_NAME) }, capitalization = KeyboardCapitalization.Words)
            Spacer(Modifier.height(Space.sm))
            Overline("Their weekly days off")
            Spacer(Modifier.height(Space.sm))
            WeekdayToggles(partnerWeekly) { partnerWeekly = it }
            Spacer(Modifier.height(Space.xl))
            SwitchRow("Indian public holidays", indianHolidays, { indianHolidays = it }, description = "Show them on the calendar and in leave ideas")
        }
        Spacer(Modifier.height(Space.xl))
        PrimaryButton(
            "Save",
            { onSave(HolidayPrefs(myName.trim(), partnerName.trim(), myWeekly, partnerWeekly, indianHolidays)) },
            Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Check,
        )
    }
}

@Composable
private fun WeekdayToggles(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val c = Travel.colors
    val days = remember { orderedWeekdays() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { day ->
            val on = day in selected
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(Shapes.pill)
                    .background(if (on) c.accent else c.card)
                    .border(1.dp, if (on) c.accent else c.line, Shapes.pill)
                    .clickable { onChange(if (on) selected - day else selected + day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = Travel.type.bodySmall, color = if (on) c.onAccent else c.inkSoft, maxLines = 1,
                )
            }
        }
    }
}

/** Entry point shown on the home screen, with the next stretch both people have off. */
@Composable
fun HolidayHomeCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val vm = appViewModel(key = "holiday-card") { HolidaysViewModel(this) }
    val s by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.refreshToday()
        onPauseOrDispose { }
    }
    val next = s.upcomingBreaks.firstOrNull()
    SurfaceCard(modifier.fillMaxWidth(), onClick = onOpen, contentPadding = PaddingValues(Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.CalendarMonth, Travel.colors.accent)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("Holiday planner", style = Travel.type.titleSmall, color = Travel.colors.ink)
                Text(
                    when {
                        next != null -> "Next time off together: ${Fmt.dateRange(next.start, next.end)} · ${Fmt.days(next.days)}"
                        else -> "Mark your days off and your partner's to find time to travel together"
                    },
                    style = Travel.type.bodySmall, color = Travel.colors.inkFaint,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Travel.colors.inkFaint)
        }
    }
}
