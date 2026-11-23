package com.travelogue.app.ui.itinerary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.travelogue.app.core.designsystem.components.CircleIconButton
import com.travelogue.app.core.designsystem.components.Overline
import com.travelogue.app.core.designsystem.components.PhotoImage
import com.travelogue.app.core.designsystem.components.SurfaceCard
import com.travelogue.app.core.designsystem.components.Tag
import com.travelogue.app.core.designsystem.components.color
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.entity.ActivityStatus
import com.travelogue.app.data.db.entity.ItineraryActivityEntity
import com.travelogue.app.data.db.entity.ItineraryDayEntity
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDay = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

/** Horizontal strip of day chips with activity counts and completion dots. */
@Composable
internal fun DayStrip(
    days: List<ItineraryDayEntity>,
    selectedId: Long?,
    activitiesFor: (Long) -> List<ItineraryActivityEntity>,
    onSelect: (ItineraryDayEntity) -> Unit,
) {
    val listState = rememberLazyListState()
    val selectedIndex = days.indexOfFirst { it.id == selectedId }
    LaunchedEffect(selectedIndex) { if (selectedIndex > 0) listState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0)) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        itemsIndexed(days, key = { _, d -> d.id }) { i, day ->
            DayChip(i + 1, day, day.id == selectedId, activitiesFor(day.id)) { onSelect(day) }
        }
    }
}

@Composable
private fun DayChip(number: Int, day: ItineraryDayEntity, selected: Boolean, activities: List<ItineraryActivityEntity>, onClick: () -> Unit) {
    val c = Travel.colors
    val fg = if (selected) c.background else c.ink
    val soft = if (selected) c.background.copy(alpha = 0.7f) else c.inkFaint
    Column(
        Modifier
            .width(86.dp)
            .clip(Shapes.md)
            .background(if (selected) c.ink else c.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Overline("Day $number", color = soft)
        Spacer(Modifier.height(6.dp))
        Text(day.date.format(shortDay), style = Travel.type.titleSmall, color = fg)
        Text(Fmt.dayMonth(day.date), style = Travel.type.bodySmall, color = soft)
        Spacer(Modifier.height(10.dp))
        if (activities.isEmpty()) {
            Text("Free", style = Travel.type.bodySmall, color = soft)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                activities.take(5).forEach { a ->
                    val dot = when (a.status) {
                        ActivityStatus.COMPLETED -> c.success
                        ActivityStatus.SKIPPED -> soft.copy(alpha = 0.4f)
                        ActivityStatus.PLANNED -> if (selected) c.background.copy(alpha = 0.55f) else c.line
                    }
                    Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                }
                if (activities.size > 5) Text("+${activities.size - 5}", style = Travel.type.bodySmall, color = soft)
            }
        }
    }
}

/**
 * One activity on the vertical timeline: time column, rail with a node (tap to toggle completion),
 * and the activity card.
 */
@Composable
internal fun TimelineActivity(
    activity: ItineraryActivityEntity,
    isFirst: Boolean,
    isLast: Boolean,
    currency: String,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onMore: () -> Unit,
) {
    val c = Travel.colors
    val statusColor = activity.status.color()
    val skipped = activity.status == ActivityStatus.SKIPPED
    val done = activity.status == ActivityStatus.COMPLETED
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = Space.gutter),
    ) {
        // Time column
        Column(Modifier.width(52.dp).padding(top = 22.dp), horizontalAlignment = Alignment.End) {
            val t = activity.startTime
            if (t != null) {
                val parts = Fmt.time(t).split(" ")
                Text(parts.first(), style = Travel.type.label, color = c.ink)
                if (parts.size > 1) Text(parts.drop(1).joinToString(" "), style = Travel.type.bodySmall, color = c.inkFaint)
            } else {
                Text("—", style = Travel.type.label, color = c.inkFaint)
            }
        }
        Spacer(Modifier.width(10.dp))
        // Rail
        Box(Modifier.width(24.dp).fillMaxHeight()) {
            val lineColor = c.line
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                val nodeY = 30.dp.toPx()
                val top = if (isFirst) nodeY else 0f
                val bottom = if (isLast) nodeY else size.height
                if (bottom > top) drawLine(lineColor, Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx())
            }
            Box(
                Modifier
                    .padding(top = 18.dp)
                    .align(Alignment.TopCenter)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (done) c.success else c.background)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(Icons.Rounded.Check, "Mark as planned", tint = Color.White, modifier = Modifier.size(14.dp))
                } else {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        SurfaceCard(
            Modifier.weight(1f).padding(vertical = Space.sm).alpha(if (skipped) 0.6f else 1f),
            onClick = onClick,
            contentPadding = PaddingValues(start = Space.lg, top = Space.md, end = Space.sm, bottom = Space.md),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 2.dp)) {
                    Text(
                        activity.name,
                        style = Travel.type.titleSmall,
                        color = c.ink,
                        textDecoration = if (skipped) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val timeRange = listOfNotNull(activity.startTime?.let(Fmt::time), activity.endTime?.let(Fmt::time)).joinToString(" – ")
                    if (activity.location.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        IconLine(Icons.Rounded.Place, activity.location)
                    }
                    if (timeRange.isNotEmpty() && activity.endTime != null) {
                        Text(timeRange, style = Travel.type.bodySmall, color = c.inkFaint)
                    }
                    if (activity.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(activity.description, style = Travel.type.body, color = c.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tag(activity.status.label, color = statusColor)
                        val cost = costLine(activity, currency)
                        if (cost != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(cost, style = Travel.type.bodySmall, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (!activity.photoPath.isNullOrBlank()) {
                    Spacer(Modifier.width(Space.sm))
                    PhotoImage(activity.photoPath, Modifier.size(64.dp).clip(Shapes.sm), contentDescription = activity.name)
                }
                CircleIconButton(
                    Icons.Rounded.MoreHoriz,
                    onMore,
                    contentDescription = "Activity options",
                    size = 36.dp,
                    iconSize = 18.dp,
                    background = Color.Transparent,
                    tint = c.inkFaint,
                )
            }
        }
    }
}

private fun costLine(a: ItineraryActivityEntity, currency: String): String? {
    val est = a.estimatedCost?.let { "Est. ${Fmt.money(it, currency)}" }
    val act = a.actualCost?.let { "Spent ${Fmt.money(it, currency)}" }
    return listOfNotNull(est, act).joinToString(" · ").ifEmpty { null }
}

@Composable
internal fun IconLine(icon: ImageVector, text: String, color: Color = Travel.colors.inkSoft) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Travel.colors.inkFaint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = Travel.type.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A tappable row in an options sheet. */
@Composable
internal fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Travel.colors.ink, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.md)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = Space.sm, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Space.lg))
        Text(label, style = Travel.type.bodyLarge, color = tint)
    }
}
