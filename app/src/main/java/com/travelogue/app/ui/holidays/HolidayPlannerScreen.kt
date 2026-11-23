package com.travelogue.app.ui.holidays

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.Add
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
import com.travelogue.app.data.repository.HolidayRepository
import com.travelogue.app.domain.HolidayPerson
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

/**
 * Where each person's colour sits in the theme's chart colours, by their place in the list. The
 * first three chart colours are the theme's own accent, gold and secondary, which the calendar
 * already uses for today and for time together, so people take theirs from the ones after.
 */
private val PersonChartSlots = intArrayOf(3, 7, 9, 5, 6, 4)

/** Each person has a colour of their own; time together borrows the app's warm accent. */
@Composable
private fun personColor(index: Int): Color {
    val chart = Travel.colors.chart
    return chart[PersonChartSlots[index % PersonChartSlots.size] % chart.size]
}

/** "you both" for two people, "you all" for more: how the planner speaks to the people going. */
private fun youAll(s: HolidaysState): String = if (s.going.size > 2) "you all" else "you both"

private fun listNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    2 -> "${names[0]} and ${names[1]}"
    else -> "${names[0]}, ${names[1]} and ${names.size - 2} more"
}

/** Who is in the planner, for the line under the title: "Kiran & Asha", "Kiran, Asha & Mum". */
private fun titleNames(names: List<String>): String = when {
    names.size <= 2 -> names.joinToString(" & ")
    names.size == 3 -> "${names[0]}, ${names[1]} & ${names[2]}"
    else -> "${names[0]}, ${names[1]} & ${names.size - 2} more"
}

/**
 * Everyone's calendars on one grid: the user's days off, their partner's, and those of anyone they
 * add. Stretches of three or more days everyone has off run across the grid as a warm band, and are listed underneath with what the
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
        subtitle = titleNames(s.people.map { it.name }),
        actions = { CircleIconButton(Icons.Rounded.Tune, { showPrefs = true }, contentDescription = "People and weekly days off") },
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

            val markedThisMonth = s.marks.values.flatMap { it.keys }.distinct().filter { YearMonth.from(it) == month }.sorted()
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
                    subtitle = "Stretches of ${s.minDays} or more days " +
                        if (s.going.size == s.people.size) "${youAll(s)} have off" else "${listNames(s.going.map { it.name })} have off",
                    modifier = sectionPadding,
                )
                PlanFilterRow(s, onPerson = vm::toggleGoing, onLength = vm::setMinDays)
                val upcoming = s.upcomingBreaks.take(MAX_BREAKS_SHOWN)
                SurfaceCard(listCard, contentPadding = listPadding) {
                    if (upcoming.isEmpty()) {
                        Text(
                            if (s.minDays > HolidayPlanner.MIN_BREAK_DAYS) {
                                "No stretch of ${s.minDays} days or more yet. Try a shorter length, or look at the leave ideas below."
                            } else {
                                "Nothing yet. Tap a day on the calendar to mark who is off. " +
                                    "When a day ${youAll(s)} have off sits next to a weekend, the long weekend appears here."
                            },
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
                                detail = ideaDetail(idea, youAll(s)),
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
                            PublicHolidayRow(h, s, onShow = { showMonth(YearMonth.from(h.date)) }, onToggle = { person -> vm.toggle(person, h.date, h.name) })
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

private fun ideaDetail(idea: LeaveIdea, youAll: String): String {
    val holidays = listNames(idea.holidays.map { it.name })
    val leave = idea.leaveDays.joinToString(" and ") { Fmt.weekdayShort(it) }
    return when {
        idea.leaveDays.isEmpty() -> "No leave needed if $youAll get $holidays off"
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
 * One day: a round disc tinted for whoever is off, a coloured dot for each person with it marked, a filled number
 * for today and an emoji for a public holiday. Days inside a shared break sit on a band that joins
 * up with their neighbours.
 */
@Composable
private fun DayCell(date: LocalDate, column: Int, s: HolidaysState, onClick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val c = Travel.colors
    // Everyone with the day marked, with their place in the list (which decides their colour).
    val marked = s.people.withIndex().filter { date in s.marks(it.value) }
    val inBreak = date in s.breakDays
    val holiday = s.publicHolidays[date]
    val isToday = date == s.today
    val disc = when {
        inBreak -> Color.Transparent
        s.allOff(date) -> c.accentSoft
        marked.isNotEmpty() -> personColor(marked.first().index).copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val description = buildString {
        append(Fmt.weekday(date))
        if (holiday != null) append(", ${holiday.name}")
        marked.forEach { append(", ${it.value.name} off") }
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
            // The disc is round, so it is narrow down here: five or six dots have to be smaller to fit.
            val crowded = marked.size > 4
            Row(
                Modifier.padding(top = 1.dp).height(5.dp),
                horizontalArrangement = Arrangement.spacedBy(if (crowded) 1.dp else 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                marked.forEach { Box(Modifier.size(if (crowded) 4.dp else 5.dp).background(personColor(it.index), CircleShape)) }
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
        s.people.forEachIndexed { i, person ->
            LegendItem(person.name) { Box(Modifier.size(8.dp).background(personColor(i), CircleShape)) }
        }
        LegendItem("Together ${s.minDays}+ days") { Box(Modifier.size(width = 20.dp, height = 10.dp).background(c.goldSoft, Shapes.pill)) }
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
        val marked = s.people.withIndex().filter { date in s.marks(it.value) }
        Row(horizontalArrangement = Arrangement.spacedBy(if (marked.size > 2) Space.xs else Space.sm)) {
            marked.forEach { (i, person) ->
                // Full names for one or two people; more than that only fit as initials.
                if (marked.size > 2) InitialAvatar(person.name, size = 26.dp, color = personColor(i)) else Tag(person.name, color = personColor(i))
            }
        }
    }
}

/**
 * Narrows "time off together" so it answers "when can we go?": who is coming (offered once there
 * are more than two people to choose from) and how long a stretch has to be.
 */
@Composable
private fun PlanFilterRow(s: HolidaysState, onPerson: (HolidayPerson) -> Unit, onLength: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (s.people.size > 2) {
            s.people.forEachIndexed { i, person ->
                val going = s.going.any { it.id == person.id }
                ChoicePill(
                    person.name, going, { onPerson(person) }, leadingDot = personColor(i),
                    modifier = Modifier.semantics { contentDescription = if (going) "${person.name} is coming, tap to leave out" else "Include ${person.name}" },
                )
            }
            Box(Modifier.size(width = 1.dp, height = 24.dp).background(Travel.colors.line))
        }
        PlanFilter.LENGTHS.forEach { days -> ChoicePill("$days+ days", s.minDays == days, { onLength(days) }) }
    }
}

/**
 * Round toggle carrying a person's initial: filled in their colour when they have the day off.
 * With [tinted] the initial keeps that colour while off too, which tells apart people whose names
 * start with the same letter.
 */
@Composable
private fun PersonToggle(name: String, color: Color, on: Boolean, onClick: () -> Unit, size: Dp = 36.dp, tinted: Boolean = false) {
    val c = Travel.colors
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
            color = if (!on) (if (tinted) color else c.inkFaint) else if (c.isDark) Palette.Ink else Color.White,
        )
    }
}

@Composable
private fun PublicHolidayRow(h: PublicHoliday, s: HolidaysState, onShow: () -> Unit, onToggle: (HolidayPerson) -> Unit) {
    val c = Travel.colors
    val onSharedWeekend = s.going.all { h.date.dayOfWeek in it.weeklyOff }
    // Up to three round buttons sit beside the holiday's name; more than that get a line of their own.
    val beside = s.people.size <= 3
    val toggles: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            s.people.forEachIndexed { i, person ->
                PersonToggle(person.name, personColor(i), on = h.date in s.marks(person), onClick = { onToggle(person) }, tinted = s.people.size > 2)
            }
        }
    }
    Column(Modifier.fillMaxWidth().clip(Shapes.sm).clickable(onClick = onShow).padding(vertical = Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) { Text(h.emoji, fontSize = 18.sp) }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(h.name, style = Travel.type.titleSmall, color = c.ink)
                Text(
                    Fmt.weekdayShort(h.date) + (if (h.date.year != s.today.year) " ${h.date.year}" else "") + if (onSharedWeekend) " · already a day off" else "",
                    style = Travel.type.bodySmall, color = c.inkFaint,
                )
            }
            if (beside) {
                Spacer(Modifier.width(Space.sm))
                toggles()
            }
        }
        if (!beside) {
            Spacer(Modifier.height(Space.sm))
            Box(Modifier.padding(start = 40.dp + Space.md)) { toggles() }
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
        // One figure each plus "together". They share the width until there are too many to read,
        // and from then on the row scrolls sideways.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val figures = s.people.size + 1
            val figureWidth = maxOf((maxWidth - (figures - 1).dp) / figures, 96.dp)
            Row(Modifier.horizontalScroll(rememberScrollState()).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                s.people.forEachIndexed { index, person ->
                    Figure((i.daysOff[person.id] ?: 0).toString(), "${person.name} off", personColor(index), Modifier.width(figureWidth))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
                }
                Figure(i.daysTogether.toString(), "Together", c.gold, Modifier.width(figureWidth))
            }
        }
        Hairline()
        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
            val next = i.nextBreak
            val longest = i.longestBreak
            if (i.markedDays == 0) {
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
                        wait <= 0 -> "You are ${if (s.going.size > 2) "all" else "both"} off right now"
                        wait == 1 -> "Time off together starts tomorrow"
                        else -> "Next time off together in $wait days"
                    },
                    (listOf(Fmt.dateRange(next.start, next.end), Fmt.days(next.days)) + next.labels).joinToString(" · "),
                    onClick = { onShow(next.start) },
                )
            } else if (i.markedDays > 0) {
                Insight("🧩", "No long break together yet", "Your days off do not line up into ${s.minDays} days in a row. The leave ideas below can help.")
            }
            if (longest != null && longest != next) {
                Insight(
                    "🏆", "Longest stretch together: ${Fmt.days(longest.days)}",
                    (listOf(Fmt.dateRange(longest.start, longest.end)) + longest.labels).joinToString(" · "),
                    onClick = { onShow(longest.start) },
                )
            }
            i.partDays.firstOrNull()?.let { first ->
                val n = i.partDays.size
                val two = s.going.size == 2
                Insight(
                    "🙋",
                    when {
                        two -> if (n == 1) "1 day only one of you has off" else "$n days only one of you has off"
                        else -> if (n == 1) "1 day not all of you have off" else "$n days not all of you have off"
                    },
                    if (two) {
                        "Next is ${Fmt.weekdayShort(first.date)}, when only ${first.off.first().name} is off. Tap to change it."
                    } else {
                        "Next is ${Fmt.weekdayShort(first.date)}, when ${listNames(first.working.map { it.name })} ${if (first.working.size == 1) "is" else "are"} not off. Tap to change it."
                    },
                    onClick = { onDay(first.date) },
                )
            }
            if (i.weekendHolidays.isNotEmpty()) {
                val n = i.weekendHolidays.size
                Insight(
                    "😴",
                    if (n == 1) "1 holiday lands on your weekend" else "$n holidays land on your weekend",
                    "${listNames(i.weekendHolidays.map { it.name })} fall on a day ${youAll(s)} already have off, so they add no extra time.",
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

/** Marks one day, or a stretch starting on it, as off for any of the people in the planner. */
@Composable
private fun DaySheet(
    date: LocalDate,
    s: HolidaysState,
    onDismiss: () -> Unit,
    onSave: (until: LocalDate, mark: Set<String>, clear: Set<String>, label: String) -> Unit,
) {
    val holiday = s.publicHolidays[date]
    // How the day stood when the sheet opened: only a switch the user turns off clears anything.
    val wasOff = remember(date) { s.people.filter { date in s.marks(it) }.map { it.id }.toSet() }
    var off by remember(date) { mutableStateOf(wasOff) }
    var label by remember(date) { mutableStateOf(s.label(date).ifEmpty { holiday?.name.orEmpty().take(HolidayEntity.MAX_LABEL) }) }
    var until by remember(date) { mutableStateOf<LocalDate?>(null) }
    val untilError = until?.let {
        when {
            it.isBefore(date) -> "Pick ${Fmt.dayMonth(date)} or a later day"
            it.toEpochDay() - date.toEpochDay() >= HolidayRepository.MAX_RANGE_DAYS -> "Pick a stretch of up to a year"
            else -> null
        }
    }
    fun weeklyNote(person: HolidayPerson) =
        if (date.dayOfWeek in person.weeklyOff) "Already off every ${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}" else null

    TravelSheet(onDismiss, title = Fmt.weekday(date), fullHeight = true) {
        // Scrolls, so the Save button stays in reach however many people there are.
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (holiday != null) {
                Text("${holiday.emoji}  ${holiday.name} · Indian public holiday", style = Travel.type.body, color = Travel.colors.inkSoft)
                Spacer(Modifier.height(Space.lg))
            }
            Overline("Who is off?")
            s.people.forEach { person ->
                Spacer(Modifier.height(Space.sm))
                SwitchRow(person.name, person.id in off, { on -> off = if (on) off + person.id else off - person.id }, description = weeklyNote(person))
            }
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
        }
        Spacer(Modifier.height(Space.xl))
        PrimaryButton(
            "Save",
            { onSave(until ?: date, off, wasOff - off, label) },
            Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Check,
            enabled = untilError == null,
        )
    }
}

/**
 * Everyone in the planner: their names, the weekdays each of them never works, and the public
 * holiday layer. People beyond the first two are added and removed here; nothing changes until Save.
 */
@Composable
private fun PrefsSheet(prefs: HolidayPrefs, onDismiss: () -> Unit, onSave: (HolidayPrefs) -> Unit) {
    var people by remember { mutableStateOf(prefs.people) }
    var indianHolidays by remember { mutableStateOf(prefs.showIndianHolidays) }
    val removed = prefs.extras.filter { old -> people.none { it.id == old.id } }
    val scroll = rememberScrollState()
    var added by remember { mutableStateOf(0) }
    // Bring a newly added person's fields into view.
    LaunchedEffect(added) {
        if (added > 0) {
            withFrameNanos { }
            scroll.animateScrollTo(scroll.maxValue)
        }
    }
    fun update(index: Int, change: (HolidayPerson) -> HolidayPerson) {
        people = people.mapIndexed { i, p -> if (i == index) change(p) else p }
    }

    TravelSheet(onDismiss, title = "People and days off", fullHeight = true) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)) {
            people.forEachIndexed { i, person ->
                if (i >= 2) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(personColor(i), CircleShape))
                        Spacer(Modifier.width(Space.sm))
                        Overline("Person ${i + 1}", Modifier.weight(1f))
                        PillAction("Remove", { people = people.filterIndexed { at, _ -> at != i } })
                    }
                    Spacer(Modifier.height(Space.sm))
                }
                FormField(
                    when (i) { 0 -> "Your name"; 1 -> "Partner's name"; else -> "Name" },
                    person.name, { name -> update(i) { it.copy(name = name.take(HolidayPrefs.MAX_NAME)) } },
                    capitalization = KeyboardCapitalization.Words,
                )
                Spacer(Modifier.height(Space.sm))
                Overline(if (i == 0) "Your weekly days off" else "Their weekly days off")
                Spacer(Modifier.height(Space.sm))
                WeekdayToggles(person.weeklyOff) { days -> update(i) { it.copy(weeklyOff = days) } }
                Spacer(Modifier.height(Space.xl))
            }
            if (people.size < HolidayPrefs.MAX_PEOPLE) {
                SecondaryButton(
                    "Add person",
                    { people = people + HolidayPerson(HolidayPerson.newId(), ""); added++ },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Add,
                )
                Spacer(Modifier.height(Space.xl))
            }
            SwitchRow("Indian public holidays", indianHolidays, { indianHolidays = it }, description = "Show them on the calendar and in leave ideas")
        }
        if (removed.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            Text(
                "Saving removes ${listNames(removed.map { it.name })} and the days off marked for them.",
                style = Travel.type.bodySmall, color = Travel.colors.danger,
            )
        }
        Spacer(Modifier.height(Space.xl))
        PrimaryButton(
            "Save",
            {
                val named = people.map { it.copy(name = it.name.trim()) }
                onSave(HolidayPrefs(named[0].name, named[1].name, named[0].weeklyOff, named[1].weeklyOff, indianHolidays, named.drop(2)))
            },
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
