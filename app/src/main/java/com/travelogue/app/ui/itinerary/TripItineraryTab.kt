package com.travelogue.app.ui.itinerary

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.entity.ActivityStatus
import com.travelogue.app.data.db.entity.ItineraryActivityEntity
import com.travelogue.app.data.db.entity.ItineraryDayEntity
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav
import java.time.LocalDate

@Composable
fun TripItineraryTab(nav: Nav, tripId: Long, modifier: Modifier = Modifier) {
    val vm = appViewModel(key = "itinerary-$tripId") { ItineraryViewModel(trips, journal, tripId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(vm) { vm.message.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }

    var selectedDayId by rememberSaveable { mutableStateOf<Long?>(null) }
    val days = state.days
    val selected = days.firstOrNull { it.id == selectedDayId }
        ?: days.firstOrNull { it.date == LocalDate.now() }
        ?: days.firstOrNull()
    val currency = state.trip?.currency ?: "INR"

    var actionsFor by remember { mutableStateOf<ItineraryActivityEntity?>(null) }
    var moveFor by remember { mutableStateOf<ItineraryActivityEntity?>(null) }
    var deleteFor by remember { mutableStateOf<ItineraryActivityEntity?>(null) }
    var editDay by remember { mutableStateOf<ItineraryDayEntity?>(null) }
    var deleteDay by remember { mutableStateOf<ItineraryDayEntity?>(null) }

    val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Space.lg, bottom = bottomPad),
        ) {
            if (state.loading) {
                item { LoadingState() }
            } else if (days.isEmpty() || selected == null) {
                item {
                    EmptyState(
                        Icons.Rounded.CalendarMonth,
                        "No days planned yet",
                        "Add a day to start shaping your itinerary, one activity at a time.",
                        actionLabel = "Add a day",
                        onAction = { vm.addDay { selectedDayId = it } },
                    )
                }
            } else {
                item(key = "strip") {
                    DayStrip(days, selected.id, { state.activities(it) }) { selectedDayId = it.id }
                    Spacer(Modifier.height(Space.xxl))
                }
                val activities = state.activities(selected.id)
                item(key = "header-${selected.id}") {
                    DayHeader(selected, state.dayNumber(selected), activities, currency) { editDay = selected }
                }
                if (activities.isEmpty()) {
                    item(key = "empty-${selected.id}") {
                        EmptyState(
                            Icons.Rounded.WbSunny,
                            "A free day",
                            "Nothing planned yet. Add sights, meals or simply a long walk.",
                            actionLabel = "Add activity",
                            onAction = { nav.activityEditor(tripId, dayId = selected.id) },
                        )
                    }
                } else {
                    item { Spacer(Modifier.height(Space.sm)) }
                    itemsIndexed(activities, key = { _, a -> a.id }) { i, a ->
                        TimelineActivity(
                            activity = a,
                            isFirst = i == 0,
                            isLast = i == activities.lastIndex,
                            currency = currency,
                            onClick = { nav.activityEditor(tripId, a.id, a.dayId) },
                            onToggle = { vm.toggleCompleted(a) },
                            onMore = { actionsFor = a },
                        )
                    }
                }
                item(key = "day-actions") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.xxl),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        SecondaryButton("Add a day", { vm.addDay { selectedDayId = it } }, Modifier.weight(1f), icon = Icons.Rounded.Add)
                        if (activities.isEmpty()) {
                            SecondaryButton(
                                "Delete day",
                                { deleteDay = selected },
                                Modifier.weight(1f),
                                icon = Icons.Rounded.DeleteOutline,
                                color = Travel.colors.danger,
                            )
                        }
                    }
                }
            }
        }

        if (selected != null) {
            AccentFab(
                Icons.Rounded.Add,
                { nav.activityEditor(tripId, dayId = selected.id) },
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(Space.gutter),
                contentDescription = "Add activity",
                label = "Activity",
            )
        }
    }

    // Activity options
    actionsFor?.let { a ->
        val list = state.activities(a.dayId)
        val index = list.indexOfFirst { it.id == a.id }
        TravelSheet(onDismiss = { actionsFor = null }, title = a.name) {
            SheetAction(Icons.Rounded.Edit, "Edit activity", { actionsFor = null; nav.activityEditor(tripId, a.id, a.dayId) })
            if (a.status != ActivityStatus.COMPLETED) {
                SheetAction(Icons.Rounded.CheckCircle, "Mark completed", { vm.setStatus(a.id, ActivityStatus.COMPLETED); actionsFor = null }, Travel.colors.success)
            }
            if (a.status != ActivityStatus.SKIPPED) {
                SheetAction(Icons.Rounded.DoNotDisturbOn, "Mark skipped", { vm.setStatus(a.id, ActivityStatus.SKIPPED); actionsFor = null })
            }
            if (a.status != ActivityStatus.PLANNED) {
                SheetAction(Icons.Rounded.RadioButtonUnchecked, "Mark planned", { vm.setStatus(a.id, ActivityStatus.PLANNED); actionsFor = null })
            }
            SheetAction(Icons.Rounded.ArrowUpward, "Move up", { vm.nudge(a.id, -1); actionsFor = null }, enabled = index > 0)
            SheetAction(Icons.Rounded.ArrowDownward, "Move down", { vm.nudge(a.id, 1); actionsFor = null }, enabled = index in 0 until list.lastIndex)
            SheetAction(Icons.Rounded.SwapVert, "Move to another day", { moveFor = a; actionsFor = null }, enabled = days.size > 1)
            SheetAction(Icons.Rounded.DeleteOutline, "Delete", { deleteFor = a; actionsFor = null }, Travel.colors.danger)
        }
    }

    moveFor?.let { a ->
        TravelSheet(onDismiss = { moveFor = null }, title = "Move to…") {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                days.forEachIndexed { i, d ->
                    val current = d.id == a.dayId
                    val count = state.activities(d.id).size
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(Shapes.md)
                            .clickable(enabled = !current) {
                                vm.move(a.id, d.id, "Day ${i + 1}")
                                moveFor = null
                                selectedDayId = d.id
                            }
                            .padding(horizontal = Space.sm, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Day ${i + 1}" + if (d.title.isNotBlank()) " · ${d.title}" else "",
                                style = Travel.type.bodyLarge,
                                color = if (current) Travel.colors.inkFaint else Travel.colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(Fmt.weekdayShort(d.date), style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
                        }
                        Text(
                            if (current) "Current" else if (count == 0) "Free" else "$count planned",
                            style = Travel.type.bodySmall,
                            color = Travel.colors.inkFaint,
                        )
                    }
                }
            }
        }
    }

    editDay?.let { d ->
        DayEditSheet(d, state.dayNumber(d), onDismiss = { editDay = null }) { updated ->
            vm.updateDay(updated)
            editDay = null
        }
    }

    deleteFor?.let { a ->
        ConfirmDialog(
            title = "Delete activity?",
            message = "\"${a.name}\" and its photo will be removed from the itinerary.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(a.id) },
            onDismiss = { deleteFor = null },
        )
    }

    deleteDay?.let { d ->
        ConfirmDialog(
            title = "Delete this day?",
            message = "${Fmt.weekday(d.date)} will be removed from the itinerary.",
            confirmLabel = "Delete day",
            onConfirm = {
                vm.deleteDay(d.id)
                selectedDayId = days.firstOrNull { it.id != d.id }?.id
            },
            onDismiss = { deleteDay = null },
        )
    }
}

@Composable
private fun DayHeader(
    day: ItineraryDayEntity,
    number: Int,
    activities: List<ItineraryActivityEntity>,
    currency: String,
    onEdit: () -> Unit,
) {
    val c = Travel.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter)) {
        Row(
            Modifier.fillMaxWidth().clip(Shapes.sm).clickable(onClick = onEdit),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Overline("Day $number · ${Fmt.weekday(day.date)}")
                Spacer(Modifier.height(4.dp))
                Text(
                    day.title.ifBlank { "Day $number" },
                    style = Travel.type.headline,
                    color = c.ink,
                )
            }
            Icon(Icons.Rounded.Edit, "Edit day", tint = c.inkFaint, modifier = Modifier.padding(bottom = 6.dp).size(18.dp))
        }
        if (day.notes.isNotBlank()) {
            Spacer(Modifier.height(Space.sm))
            Text(day.notes, style = Travel.type.body, color = c.inkSoft)
        }
        if (activities.isNotEmpty()) {
            Spacer(Modifier.height(Space.lg))
            val done = activities.count { it.status == ActivityStatus.COMPLETED }
            val counted = activities.count { it.status != ActivityStatus.SKIPPED }.coerceAtLeast(1)
            val planned = activities.filter { it.status != ActivityStatus.SKIPPED }.sumOf { it.estimatedCost ?: 0.0 }
            val actual = activities.sumOf { it.actualCost ?: 0.0 }
            SurfaceCard(contentPadding = PaddingValues(Space.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$done of ${activities.size} completed", style = Travel.type.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
                    val skipped = activities.count { it.status == ActivityStatus.SKIPPED }
                    if (skipped > 0) Text("$skipped skipped", style = Travel.type.bodySmall, color = c.inkFaint)
                }
                Spacer(Modifier.height(Space.md))
                ProgressBar(done.toFloat() / counted, color = c.success)
                if (planned > 0 || actual > 0) {
                    Spacer(Modifier.height(Space.md))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Planned", style = Travel.type.bodySmall, color = c.inkFaint)
                            Text(Fmt.money(planned, currency), style = Travel.type.numberSmall, color = c.ink)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Actual", style = Travel.type.bodySmall, color = c.inkFaint)
                            Text(
                                Fmt.money(actual, currency),
                                style = Travel.type.numberSmall,
                                color = if (planned > 0 && actual > planned) c.danger else c.ink,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.md))
    }
}

@Composable
private fun DayEditSheet(day: ItineraryDayEntity, number: Int, onDismiss: () -> Unit, onSave: (ItineraryDayEntity) -> Unit) {
    var title by remember(day.id) { mutableStateOf(day.title) }
    var notes by remember(day.id) { mutableStateOf(day.notes) }
    TravelSheet(onDismiss = onDismiss, title = "Day $number · ${Fmt.weekdayShort(day.date)}") {
        FormField("Day title", title, { title = it }, placeholder = "e.g. Old town & sunset cruise", leadingIcon = Icons.Rounded.EventNote)
        Spacer(Modifier.height(Space.md))
        FormField("Notes", notes, { notes = it }, placeholder = "Anything to remember for this day", singleLine = false, minLines = 3)
        Spacer(Modifier.height(Space.xl))
        PrimaryButton("Save day", { onSave(day.copy(title = title.trim(), notes = notes.trim())) }, Modifier.fillMaxWidth())
    }
}
