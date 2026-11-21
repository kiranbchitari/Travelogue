package com.travelogue.app.ui.trips.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.travelogue.app.core.designsystem.charts.BudgetRing
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.entity.DiaryEntryEntity
import com.travelogue.app.data.db.entity.HotelEntity
import com.travelogue.app.data.db.entity.TripEntity
import com.travelogue.app.ui.navigation.Nav
import com.travelogue.app.ui.navigation.TripTab
import com.travelogue.app.ui.trips.ExpenseLineRow
import com.travelogue.app.ui.trips.phaseLabel

/** Tab 0 of the trip detail screen. */
@Composable
fun TripOverviewTab(
    nav: Nav,
    state: TripDetailState,
    onOpenTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trip = state.trip ?: return
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Space.lg, bottom = Space.huge + 24.dp),
        ) {
            item { BudgetCard(trip, state, onClick = { nav.tripFinance(trip.id) }) }

            item {
                Row(
                    Modifier.padding(horizontal = Space.gutter).padding(top = Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    StatTile("Duration", Fmt.days(trip.days), Modifier.weight(1f), icon = Icons.Rounded.CalendarMonth, tint = Travel.colors.gold,
                        caption = trip.phaseLabel())
                    StatTile("Places", state.placesCount.toString(), Modifier.weight(1f), icon = Icons.Rounded.Place, tint = Travel.colors.sage,
                        onClick = { onOpenTab(TripTab.PLACES) })
                    StatTile("Photos", state.photoCount.toString(), Modifier.weight(1f), icon = Icons.Rounded.PhotoLibrary, tint = Travel.colors.secondary,
                        onClick = { nav.tripGallery(trip.id) })
                }
            }

            item {
                Row(
                    Modifier.padding(horizontal = Space.gutter).padding(top = Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    QuickLink(Icons.Rounded.PieChart, "Finances", "Budget & splits", Travel.colors.accent, Modifier.weight(1f)) { nav.tripFinance(trip.id) }
                    QuickLink(Icons.Rounded.AutoStories, "Trip story", "Timeline", Travel.colors.secondary, Modifier.weight(1f)) { nav.tripTimeline(trip.id) }
                    QuickLink(Icons.Rounded.Collections, "Gallery", "All photos", Travel.colors.gold, Modifier.weight(1f)) { nav.tripGallery(trip.id) }
                }
            }

            if (trip.description.isNotBlank()) {
                item {
                    Column(Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxxl)) {
                        Overline("About this trip")
                        Spacer(Modifier.height(Space.sm))
                        Text(trip.description, style = Travel.type.journal, color = Travel.colors.ink)
                    }
                }
            }

            item { TravellersSection(trip, state) }

            item {
                SectionHeader(
                    "Where we stayed",
                    actionLabel = if (state.hotels.isNotEmpty()) "All stays" else null,
                    onAction = { onOpenTab(TripTab.HOTELS) },
                    modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxxl, bottom = Space.sm),
                )
                if (state.hotels.isEmpty()) {
                    AddRow(Icons.Rounded.Hotel, "Add a stay", "Hotels, homestays and reviews") { nav.hotelEditor(trip.id) }
                } else {
                    SurfaceCard(Modifier.padding(horizontal = Space.gutter).fillMaxWidth(), contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.xs)) {
                        state.hotels.forEachIndexed { i, h ->
                            if (i > 0) Hairline()
                            HotelLine(h) { nav.hotel(h.id) }
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    "From the diary",
                    subtitle = if (state.diaryCount > 0) "${state.diaryCount} entries" else null,
                    actionLabel = if (state.diaryCount > 0) "Read all" else null,
                    onAction = { onOpenTab(TripTab.DIARY) },
                    modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxxl, bottom = Space.sm),
                )
                if (state.recentDiary.isEmpty()) {
                    AddRow(Icons.Rounded.EditNote, "Write the first entry", "Moments you want to remember") { nav.diaryEditor(trip.id) }
                }
            }
            items(state.recentDiary, key = { "d${it.id}" }) { d ->
                DiaryLine(d, Modifier.padding(horizontal = Space.gutter).padding(bottom = Space.md)) { nav.diaryEntry(d.id) }
            }

            item {
                SectionHeader(
                    "Recent expenses",
                    subtitle = if (state.expenseCount > 0) "${state.expenseCount} in total" else null,
                    actionLabel = if (state.expenseCount > 0) "All expenses" else null,
                    onAction = { onOpenTab(TripTab.EXPENSES) },
                    modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxl, bottom = Space.sm),
                )
                if (state.recentExpenses.isEmpty()) {
                    AddRow(Icons.Rounded.Payments, "Record an expense", "Track spending and who paid") { nav.expenseEditor(trip.id) }
                } else {
                    SurfaceCard(Modifier.padding(horizontal = Space.gutter).fillMaxWidth(), contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.xs)) {
                        state.recentExpenses.forEachIndexed { i, e ->
                            if (i > 0) Hairline()
                            val cat = e.categoryId?.let { state.categories[it] }
                            ExpenseLineRow(
                                e, cat,
                                subtitle = listOfNotNull(cat?.name, Fmt.dayMonth(e.date)).joinToString(" · "),
                                onClick = { nav.expenseEditor(trip.id, e.id) },
                            )
                        }
                    }
                }
            }

            if (trip.notes.isNotBlank()) {
                item {
                    SurfaceCard(
                        Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxxl).fillMaxWidth(),
                        color = Travel.colors.goldSoft,
                        elevation = 0.dp,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.StickyNote2, null, tint = Travel.colors.gold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Space.sm))
                            Overline("Notes", color = Travel.colors.gold)
                        }
                        Spacer(Modifier.height(Space.sm))
                        Text(trip.notes, style = Travel.type.body, color = Travel.colors.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetCard(trip: TripEntity, s: TripDetailState, onClick: () -> Unit) {
    val c = Travel.colors
    SurfaceCard(Modifier.padding(horizontal = Space.gutter).fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(Space.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (trip.budget > 0) {
                val used = (s.total / trip.budget).toFloat()
                BudgetRing(used, size = 104.dp, thickness = 10.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${Math.round(used * 100)}%", style = Travel.type.numberSmall, color = if (used > 1f) c.danger else c.ink)
                        Text("of budget", style = Travel.type.bodySmall, color = c.inkFaint)
                    }
                }
                Spacer(Modifier.width(Space.xl))
            }
            Column(Modifier.weight(1f)) {
                Overline("Total spent")
                Spacer(Modifier.height(2.dp))
                Text(Fmt.money(s.total, trip.currency), style = Travel.type.number, color = c.ink, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                if (trip.budget > 0) {
                    val remaining = trip.budget - s.total
                    Text(
                        if (remaining >= 0) "${Fmt.money(remaining, trip.currency)} left of ${Fmt.money(trip.budget, trip.currency)}"
                        else "${Fmt.money(-remaining, trip.currency)} over budget",
                        style = Travel.type.body,
                        color = if (remaining >= 0) c.inkSoft else c.danger,
                    )
                } else {
                    Text("No budget set · ${s.expenseCount} expenses", style = Travel.type.body, color = c.inkSoft)
                }
                if (s.unconvertedCurrencies.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add a rate for ${s.unconvertedCurrencies.joinToString()} to include it",
                        style = Travel.type.bodySmall,
                        color = c.accent,
                    )
                }
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = c.inkFaint)
        }
    }
}

@Composable
private fun QuickLink(icon: ImageVector, title: String, caption: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    SurfaceCard(modifier, onClick = onClick, color = tint.copy(alpha = 0.1f), elevation = 0.dp, contentPadding = PaddingValues(Space.md)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(Space.md))
        Text(title, style = Travel.type.label, color = Travel.colors.ink, maxLines = 1)
        Text(caption, style = Travel.type.bodySmall, color = Travel.colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TravellersSection(trip: TripEntity, s: TripDetailState) {
    Column(Modifier.padding(top = Space.xxxl)) {
        SectionHeader(
            "Travelling together",
            subtitle = "${trip.travelType.label} · ${if (trip.travellerCount == 1) "1 traveller" else "${trip.travellerCount} travellers"}",
            modifier = Modifier.padding(horizontal = Space.gutter).padding(bottom = Space.md),
        )
        if (s.travellers.isEmpty()) {
            Text(
                "Add travellers' names when editing the trip to split expenses.",
                style = Travel.type.body,
                color = Travel.colors.inkFaint,
                modifier = Modifier.padding(horizontal = Space.gutter),
            )
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                items(s.travellers, key = { it.id }) { t ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                        InitialAvatar(t.name, size = 52.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(t.name, style = Travel.type.bodySmall, color = Travel.colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val extra = trip.travellerCount - s.travellers.size
                if (extra > 0) {
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                            InitialAvatar("+ $extra", size = 52.dp, color = Travel.colors.inkSoft)
                            Spacer(Modifier.height(6.dp))
                            Text("more", style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HotelLine(h: HotelEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Shapes.sm).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Rounded.Hotel, Travel.colors.secondary)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(h.name, style = Travel.type.titleSmall, color = Travel.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Fmt.dateRange(h.checkIn, h.checkOut)} · ${Fmt.nights(h.nights)}",
                style = Travel.type.bodySmall, color = Travel.colors.inkFaint, maxLines = 1,
            )
        }
        if (h.review.overallRating > 0) RatingStars(h.review.overallRating, size = 13.dp)
    }
}

@Composable
private fun DiaryLine(d: DiaryEntryEntity, modifier: Modifier, onClick: () -> Unit) {
    SurfaceCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(Fmt.dateTime(d.dateTime), Modifier.weight(1f))
            d.mood?.let { Text(it.emoji, style = Travel.type.titleSmall) }
        }
        Spacer(Modifier.height(6.dp))
        Text(d.title, style = Travel.type.title, color = Travel.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (d.body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(d.body, style = Travel.type.body, color = Travel.colors.inkSoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (d.location.isNotBlank()) {
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Place, null, tint = Travel.colors.inkFaint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(d.location, style = Travel.type.bodySmall, color = Travel.colors.inkFaint, maxLines = 1)
            }
        }
    }
}

/** Inviting dashed-feel row used when a section is empty. */
@Composable
private fun AddRow(icon: ImageVector, title: String, caption: String, onClick: () -> Unit) {
    SurfaceCard(
        Modifier.padding(horizontal = Space.gutter).fillMaxWidth(),
        onClick = onClick,
        color = Travel.colors.backgroundDeep,
        elevation = 0.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, Travel.colors.accent, background = Travel.colors.card)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = Travel.type.titleSmall, color = Travel.colors.ink)
                Text(caption, style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
            }
            Icon(Icons.Rounded.Add, null, tint = Travel.colors.accent)
        }
    }
}
