package com.travelogue.app.ui.memories

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.entity.TripEntity
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.diary.DiaryCardModel
import com.travelogue.app.ui.diary.DiaryEntryCard
import com.travelogue.app.ui.navigation.Nav
import com.travelogue.app.domain.CoverFrame
import com.travelogue.app.domain.coverFrame

@Composable
fun TripTimelineScreen(nav: Nav, tripId: Long) {
    val vm = appViewModel(key = "timeline-$tripId") { TripTimelineViewModel(trips, diary, journal, expenses, photos, tripId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Travel.colors
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val trip = state.trip

    Box(Modifier.fillMaxSize().background(c.background)) {
        val heroListState = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize(), state = heroListState, contentPadding = PaddingValues(bottom = navBottom + Space.huge)) {
            item(key = "hero") { StoryHero(trip, state) }

            if (!state.loaded) {
                item(key = "loading") { LoadingState() }
            } else if (trip == null) {
                item(key = "missing") { EmptyState(Icons.Rounded.Luggage, "Trip not found", "It may have been deleted.", actionLabel = "Go back", onAction = nav::back) }
            } else if (state.days.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Rounded.AutoStories,
                        "Your story starts here",
                        "Diary entries, places, hotel stays, activities, expenses and photos weave together here into a day-by-day story.",
                        actionLabel = "Write a diary entry",
                        onAction = { nav.diaryEditor(tripId) },
                    )
                }
            } else {
                var alt = 0
                state.days.forEach { day ->
                    item(key = "day-${day.date}") {
                        StoryDayHeader(
                            day.date, day.dayNumber,
                            Modifier.padding(top = Space.xxxl, bottom = Space.lg),
                        )
                    }
                    day.events.forEachIndexed { i, event ->
                        val mirrored = alt++ % 2 == 1
                        val last = i == day.events.lastIndex
                        item(key = event.key) {
                            StoryEventRow(event, last, mirrored, tripId, nav)
                        }
                    }
                }
                item(key = "end") {
                    Column(Modifier.fillMaxWidth().padding(top = Space.xxxl), horizontalAlignment = Alignment.CenterHorizontally) {
                        TimelineNode(c.gold, size = 18.dp)
                        Spacer(Modifier.height(Space.sm))
                        Text("Fin.", style = Travel.type.title, color = c.inkSoft)
                        Text("Until the next journey", style = Travel.type.bodySmall, color = c.inkFaint)
                    }
                }
            }
        }
        ScrollStatusScrim(heroListState)
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.lg, vertical = Space.sm)) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, nav::back, contentDescription = "Back", background = c.card.copy(alpha = 0.92f))
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Rounded.PhotoLibrary, { nav.tripGallery(tripId) }, contentDescription = "Gallery", background = c.card.copy(alpha = 0.92f))
        }
    }
}

@Composable
private fun StoryHero(trip: TripEntity?, state: TimelineState) {
    Column {
        Box(Modifier.fillMaxWidth().height(340.dp)) {
            CoverImage(trip?.coverPath, trip?.coverFrame ?: CoverFrame.Center, Modifier.fillMaxSize(), fallbackSeed = trip?.name.orEmpty())
            BottomScrim(0.75f, 0.8f)
            if (trip != null) {
                Column(Modifier.align(Alignment.BottomStart).padding(Space.gutter)) {
                    Overline("A trip story", color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(4.dp))
                    Text(trip.name, style = Travel.type.display, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOf(trip.destination, Fmt.dateRange(trip.startDate, trip.endDate)).filter { it.isNotBlank() }.joinToString("  ·  "),
                        style = Travel.type.body, color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
        if (trip != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.lg), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroStat(trip.days.toString(), if (trip.days == 1) "day" else "days")
                HeroStat(state.photoCount.toString(), "photos")
                HeroStat(state.entryCount.toString(), "stories")
                HeroStat(state.placeCount.toString(), "places")
            }
            Hairline(Modifier.padding(horizontal = Space.gutter))
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Travel.type.numberSmall, color = Travel.colors.ink)
        Text(label, style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
    }
}

/** Left rail (type icon + connecting line) with content on the right. */
@Composable
private fun StoryRail(icon: ImageVector, tint: Color, last: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val line = Travel.colors.line
    val bg = Travel.colors.background
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.gutter - 6.dp, end = Space.gutter)
            .drawBehind {
                val x = 14.dp.toPx()
                val end = if (last) 14.dp.toPx() else size.height
                drawLine(line, Offset(x, 0f), Offset(x, end), strokeWidth = 2.dp.toPx())
            },
    ) {
        IconBadge(icon, tint, size = 28.dp, iconSize = 14.dp, background = tint.copy(alpha = 0.18f).compositeOver(bg))
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f).padding(top = 4.dp, bottom = Space.xl), content = content)
    }
}

@Composable
private fun StoryEventRow(event: StoryEvent, last: Boolean, mirrored: Boolean, tripId: Long, nav: Nav) {
    val c = Travel.colors
    when (event) {
        is StoryEvent.Diary -> StoryRail(Icons.Rounded.AutoStories, c.accent, last) {
            Overline("Journal · ${Fmt.time(event.time)}", color = c.accent)
            Spacer(Modifier.height(Space.sm))
            DiaryEntryCard(
                DiaryCardModel(event.entry, event.photos, event.tags),
                onClick = { nav.diaryEntry(event.entry.id) },
                onPhotoClick = { nav.photo(tripId, it.id) },
                mirrored = mirrored,
            )
        }

        is StoryEvent.Place -> StoryRail(event.place.category.icon, c.secondary, last) {
            val p = event.place
            Column(Modifier.storyTap { nav.place(p.id) }) {
                Overline("Visited · ${p.category.label}", color = c.secondary)
                Spacer(Modifier.height(4.dp))
                Text(p.name, style = Travel.type.title, color = c.ink)
                if (p.rating > 0) { Spacer(Modifier.height(4.dp)); RatingStars(p.rating, size = 14.dp) }
                if (p.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(p.description, style = Travel.type.body, color = c.inkSoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (event.photos.isNotEmpty()) {
                Spacer(Modifier.height(Space.md))
                PhotoCollage(event.photos, Modifier.clip(Shapes.md), height = 190.dp, mirrored = mirrored) { nav.photo(tripId, it.id) }
            }
        }

        is StoryEvent.CheckIn -> StoryRail(Icons.Rounded.Hotel, c.chart[4], last) {
            val h = event.hotel
            Column(Modifier.storyTap { nav.hotel(h.id) }) {
                Overline("Checked in · ${Fmt.nights(h.nights)}", color = c.chart[4])
                Spacer(Modifier.height(4.dp))
                Text(h.name, style = Travel.type.title, color = c.ink)
                if (h.address.isNotBlank()) Text(h.address, style = Travel.type.bodySmall, color = c.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (h.review.overallRating > 0) { Spacer(Modifier.height(4.dp)); RatingStars(h.review.overallRating, size = 14.dp) }
                if (h.review.review.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("“${h.review.review.trim()}”", style = Travel.type.body.copy(fontFamily = Travel.type.journal.fontFamily), color = c.inkSoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (event.photos.isNotEmpty()) {
                Spacer(Modifier.height(Space.md))
                PhotoCollage(event.photos, Modifier.clip(Shapes.md), height = 190.dp, mirrored = mirrored) { nav.photo(tripId, it.id) }
            }
        }

        is StoryEvent.CheckOut -> StoryRail(Icons.AutoMirrored.Rounded.Logout, c.inkFaint, last) {
            Text(
                "Checked out of ${event.hotel.name}",
                style = Travel.type.body, color = c.inkSoft,
                modifier = Modifier.storyTap { nav.hotel(event.hotel.id) }.padding(vertical = 2.dp),
            )
        }

        is StoryEvent.Activity -> StoryRail(Icons.Rounded.Flag, event.activity.status.color(), last) {
            val a = event.activity
            Column(Modifier.storyTap { nav.activityEditor(tripId, a.id) }) {
                val times = listOfNotNull(a.startTime?.let(Fmt::time), a.endTime?.let(Fmt::time)).joinToString(" – ")
                Overline(listOf("Itinerary", times).filter { it.isNotBlank() }.joinToString(" · "), color = a.status.color())
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.name, style = Travel.type.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
                    Tag(a.status.label, color = a.status.color())
                }
                if (a.location.isNotBlank()) Text(a.location, style = Travel.type.bodySmall, color = c.inkFaint)
                if (a.description.isNotBlank()) Text(a.description, style = Travel.type.body, color = c.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!a.photoPath.isNullOrBlank()) {
                Spacer(Modifier.height(Space.md))
                PhotoImage(a.photoPath, Modifier.fillMaxWidth().height(170.dp).clip(Shapes.md))
            }
        }

        is StoryEvent.Booking -> StoryRail(event.booking.type.icon, c.chart[5], last) {
            val bk = event.booking
            Column(Modifier.fillMaxWidth().storyTap { nav.bookingEditor(tripId, bk.id) }) {
                Overline(listOfNotNull(bk.type.label, bk.departure?.let { Fmt.time(it.toLocalTime()) }).joinToString(" · "), color = c.chart[5])
                Spacer(Modifier.height(4.dp))
                Text(bk.title, style = Travel.type.titleSmall, color = c.ink)
                val route = listOf(bk.fromPlace, bk.toPlace).filter { it.isNotBlank() }
                if (route.isNotEmpty()) Text(route.joinToString("  →  "), style = Travel.type.body, color = c.inkSoft)
                val meta = listOf(bk.provider, bk.reference.takeIf { it.isNotBlank() }?.let { "Ref $it" }.orEmpty()).filter { it.isNotBlank() }
                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = Travel.type.bodySmall, color = c.inkFaint)
            }
        }

        is StoryEvent.Photos -> StoryRail(Icons.Rounded.PhotoCamera, c.chart[6], last) {
            Overline(if (event.photos.size == 1) "1 photo" else "${event.photos.size} photos", color = c.chart[6])
            Spacer(Modifier.height(Space.sm))
            PhotoCollage(event.photos, Modifier.clip(Shapes.md), height = 240.dp, mirrored = mirrored) { nav.photo(tripId, it.id) }
            event.photos.firstOrNull { it.caption.isNotBlank() }?.let {
                Spacer(Modifier.height(Space.sm))
                Text(it.caption, style = Travel.type.bodySmall.copy(fontFamily = Travel.type.journal.fontFamily), color = c.inkSoft)
            }
        }

        is StoryEvent.Expenses -> StoryRail(Icons.Rounded.AccountBalanceWallet, c.gold, last) {
            ExpenseSummary(event, tripId, nav)
        }
    }
}

@Composable
private fun ExpenseSummary(event: StoryEvent.Expenses, tripId: Long, nav: Nav) {
    val c = Travel.colors
    var expanded by rememberSaveable(event.key) { mutableStateOf(false) }
    Column(Modifier.animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().storyTap { expanded = !expanded }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Overline("Spending", color = c.gold)
                Spacer(Modifier.height(2.dp))
                val items = if (event.expenses.size == 1) "1 item" else "${event.expenses.size} items"
                Text("Spent ${Fmt.money(event.total, event.currency)} on $items", style = Travel.type.titleSmall, color = c.ink)
                if (event.unconvertedCount > 0) {
                    Text("${event.unconvertedCount} in other currencies not included", style = Travel.type.bodySmall, color = c.inkFaint)
                }
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "Collapse" else "Expand", tint = c.inkFaint)
        }
        if (expanded) {
            Spacer(Modifier.height(Space.sm))
            event.expenses.forEach { e ->
                val cat = e.categoryId?.let { event.categories[it] }
                Row(
                    Modifier.fillMaxWidth().storyTap { nav.expenseEditor(tripId, e.id) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(cat.icon(), cat.colorValue(), size = 30.dp, iconSize = 15.dp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = Travel.type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(cat?.name, e.time?.let(Fmt::time)).joinToString(" · "), style = Travel.type.bodySmall, color = c.inkFaint)
                    }
                    Text(Fmt.money(e.amount, e.currency), style = Travel.type.label, color = c.ink)
                }
            }
        }
    }
}

/**
 * A tappable block of the story with a rounded ripple. The rounded clip reaches [StoryTapBleed] past
 * the text on every side: clipped right at the text's edge, its corners cut off the first letter of
 * the block's first and last lines ("FLIGHT", "VISITED"). The text itself stays where it was.
 */
@Composable
private fun Modifier.storyTap(onClick: () -> Unit): Modifier =
    this.layout { measurable, constraints ->
        val bleed = StoryTapBleed.roundToPx()
        val placeable = measurable.measure(constraints.offset(2 * bleed, 2 * bleed))
        layout(placeable.width - 2 * bleed, placeable.height - 2 * bleed) { placeable.place(-bleed, -bleed) }
    }.clip(Shapes.sm).clickable(onClick = onClick).padding(StoryTapBleed)

private val StoryTapBleed = 6.dp
