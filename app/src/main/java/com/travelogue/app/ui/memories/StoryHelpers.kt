package com.travelogue.app.ui.memories

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.travelogue.app.core.designsystem.components.Overline
import com.travelogue.app.core.designsystem.components.TimelineNode
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.entity.PhotoOwner
import com.travelogue.app.data.db.entity.TripEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 1-based day number of [date] within the trip, or null when outside the trip dates. */
fun storyDayNumber(date: LocalDate, trip: TripEntity?): Int? {
    trip ?: return null
    if (date.isBefore(trip.startDate) || date.isAfter(trip.endDate)) return null
    return (date.toEpochDay() - trip.startDate.toEpochDay() + 1).toInt()
}

/** Today when the trip is underway, otherwise the trip's first day. */
fun defaultTripDate(trip: TripEntity?, today: LocalDate = LocalDate.now()): LocalDate = when {
    trip == null -> today
    today.isBefore(trip.startDate) || today.isAfter(trip.endDate) -> trip.startDate
    else -> today
}

/** Timestamp for photos added to a trip gallery: now during the trip, otherwise trip start at noon. */
fun defaultPhotoTime(trip: TripEntity?, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
    val d = defaultTripDate(trip, now.toLocalDate())
    return if (d == now.toLocalDate()) now.withNano(0) else d.atTime(LocalTime.NOON)
}

fun PhotoOwner.sourceLabel(): String = when (this) {
    PhotoOwner.GENERAL -> "Trip gallery"
    PhotoOwner.DIARY -> "Diary entry"
    PhotoOwner.HOTEL -> "Hotel"
    PhotoOwner.PLACE -> "Place"
    PhotoOwner.ACTIVITY -> "Itinerary"
}

/**
 * Editorial day header: "Day 3" in serif with the weekday underneath, preceded by a timeline node.
 * A null [date] renders an "Along the way" header for undated items.
 */
@Composable
fun StoryDayHeader(
    date: LocalDate?,
    dayNumber: Int?,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
        TimelineNode(Travel.colors.accent, size = 16.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            val title = when {
                date == null -> "Along the way"
                dayNumber != null -> "Day $dayNumber"
                else -> Fmt.dayMonth(date)
            }
            Text(title, style = Travel.type.headline, color = Travel.colors.ink)
            if (date != null) Overline(Fmt.weekday(date) + if (dayNumber == null) " · ${date.year}" else "")
            else Overline("Undated moments")
        }
        if (trailing != null) Text(trailing, style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
    }
}
