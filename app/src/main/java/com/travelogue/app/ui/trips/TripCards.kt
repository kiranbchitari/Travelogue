package com.travelogue.app.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.entity.ExpenseCategoryEntity
import com.travelogue.app.data.db.entity.ExpenseEntity
import com.travelogue.app.data.db.entity.TripEntity
import com.travelogue.app.data.db.entity.TripPhotoEntity

/** Translucent label that sits on top of a photo. */
@Composable
fun PhotoTag(text: String, modifier: Modifier = Modifier, dot: Color? = null) {
    Row(
        modifier.clip(Shapes.pill).background(Color.Black.copy(alpha = 0.38f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Travel.type.label.copy(fontSize = Travel.type.bodySmall.fontSize), color = Color.White, maxLines = 1)
    }
}

/**
 * Large image card for a trip: cover photo (or generated art), scrim, title block, and budget usage.
 */
@Composable
fun TripHeroCard(
    summary: TripSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
    showPhase: Boolean = true,
) {
    val trip = summary.trip
    val c = Travel.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .pressable(onClick)
            .shadow(if (c.isDark) 0.dp else 10.dp, Shapes.lg, spotColor = Color(0x333A2A1A))
            .clip(Shapes.lg),
    ) {
        PhotoImage(trip.coverPath, Modifier.matchParentSize(), contentDescription = trip.name, fallbackSeed = trip.uuid)
        BottomScrim(0.75f, 0.78f)
        Row(Modifier.align(Alignment.TopStart).padding(Space.lg), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (showPhase) PhotoTag(trip.phaseLabel(), dot = trip.status.color())
            if (trip.archived) PhotoTag("Archived")
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(Space.xl)) {
            Text(
                listOf(trip.destination, trip.country).filter { it.isNotBlank() }.joinToString(" · ").uppercase(),
                style = Travel.type.overline,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(trip.name, style = Travel.type.headline, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${Fmt.dateRange(trip.startDate, trip.endDate)} · ${Fmt.days(trip.days)}",
                    style = Travel.type.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(Fmt.money(summary.total, trip.currency, compact = true), style = Travel.type.numberSmall, color = Color.White)
            }
            if (trip.budget > 0) {
                Spacer(Modifier.height(Space.sm))
                ProgressBar(
                    (summary.total / trip.budget).toFloat(),
                    color = Color.White,
                    track = Color.White.copy(alpha = 0.25f),
                    height = 4.dp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "of ${Fmt.money(trip.budget, trip.currency, compact = true)} budget",
                    style = Travel.type.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/** Compact trip row with a thumbnail, used for "recent trips". */
@Composable
fun TripRow(summary: TripSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val trip = summary.trip
    Row(
        modifier.fillMaxWidth().clip(Shapes.md).clickable(onClick = onClick).padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoImage(trip.coverPath, Modifier.size(width = 76.dp, height = 88.dp).clip(Shapes.md), fallbackSeed = trip.uuid)
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f)) {
            Text(trip.name, style = Travel.type.title, color = Travel.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Place, null, tint = Travel.colors.inkFaint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    listOf(trip.destination, trip.country).filter { it.isNotBlank() }.joinToString(", "),
                    style = Travel.type.body,
                    color = Travel.colors.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(Fmt.dateRange(trip.startDate, trip.endDate), style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        }
        Spacer(Modifier.width(Space.md))
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.money(summary.total, trip.currency, compact = true), style = Travel.type.numberSmall, color = Travel.colors.ink)
            Text(Fmt.days(trip.days), style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        }
    }
}

/** One expense line: category badge, title, subtitle and amount. */
@Composable
fun ExpenseLineRow(
    expense: ExpenseEntity,
    category: ExpenseCategoryEntity?,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clip(Shapes.sm).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(category.icon(), category.colorValue(), size = 40.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(expense.title, style = Travel.type.titleSmall, color = Travel.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = Travel.type.bodySmall, color = Travel.colors.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.md))
        Text(Fmt.money(expense.amount, expense.currency), style = Travel.type.label, color = Travel.colors.ink)
    }
}

/** Portrait photo tile for horizontal memory strips. */
@Composable
fun MemoryTile(photo: TripPhotoEntity, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 128.dp, height: Dp = 164.dp) {
    Box(modifier.size(width, height).pressable(onClick).clip(Shapes.md)) {
        PhotoImage(photo.relativePath, Modifier.matchParentSize(), contentDescription = photo.caption.ifBlank { null })
        if (photo.caption.isNotBlank()) {
            BottomScrim(0.5f, 0.6f)
            Text(
                photo.caption,
                style = Travel.type.bodySmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(Space.sm),
            )
        }
    }
}
