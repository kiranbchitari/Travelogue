package com.travelogue.app.ui.memories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.common.tabContentPadding
import com.travelogue.app.ui.diary.DiaryEntryCard
import com.travelogue.app.ui.navigation.Nav
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class MemoriesView(val label: String) { MOMENTS("Moments"), FAVOURITES("Favourites"), TRIPS("Trips"), JOURNAL("Journal") }

private val MonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

@Composable
fun MemoriesScreen(nav: Nav) {
    val vm = appViewModel(key = "memories") { MemoriesViewModel(trips, diary, photos) }
    val state by vm.state.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf(MemoriesView.MOMENTS) }
    val c = Travel.colors

    LazyColumn(Modifier.fillMaxSize(), contentPadding = tabContentPadding()) {
        item(key = "header") {
            LargeHeader(overline = "Your scrapbook", title = "Memories")
            val trips = state.galleries.count { it.photoCount > 0 }
            Text(
                listOf(
                    if (state.photoCount == 1) "1 photo" else "${state.photoCount} photos",
                    if (state.journal.size == 1) "1 story" else "${state.journal.size} stories",
                    if (trips == 1) "1 trip" else "$trips trips",
                ).joinToString("  ·  "),
                style = Travel.type.body,
                color = c.inkSoft,
                modifier = Modifier.padding(horizontal = Space.gutter),
            )
            Spacer(Modifier.height(Space.lg))
        }
        item(key = "tabs") {
            ChoiceRow(MemoriesView.entries, view, { it.label }, { view = it })
            Spacer(Modifier.height(Space.xl))
        }

        if (!state.loaded) {
            item(key = "loading") { LoadingState() }
            return@LazyColumn
        }

        when (view) {
            MemoriesView.MOMENTS -> {
                if (state.moments.isEmpty()) {
                    item(key = "empty-moments") {
                        EmptyState(
                            Icons.Rounded.PhotoLibrary, "No photographs yet",
                            "Photos you add to diary entries, hotels, places and trip galleries will gather here.",
                            actionLabel = if (state.galleries.isNotEmpty()) "Open a trip gallery" else null,
                            onAction = state.galleries.firstOrNull()?.let { g -> { nav.tripGallery(g.trip.id) } },
                        )
                    }
                }
                state.moments.forEach { group ->
                    item(key = "m-${group.month}") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Space.gutter).padding(top = Space.md, bottom = Space.md),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(group.month.format(MonthFormat), style = Travel.type.title, color = c.ink, modifier = Modifier.weight(1f))
                            Text(if (group.photos.size == 1) "1 photo" else "${group.photos.size} photos", style = Travel.type.bodySmall, color = c.inkFaint)
                        }
                    }
                    photoMosaic(group.photos, "mm-${group.month}") { nav.photo(0, it.id) }
                    item(key = "ms-${group.month}") { Spacer(Modifier.height(Space.lg)) }
                }
            }

            MemoriesView.FAVOURITES -> {
                if (state.favourites.isEmpty()) {
                    item(key = "empty-fav") {
                        EmptyState(
                            Icons.Rounded.FavoriteBorder, "Nothing hearted yet",
                            "Open any photo and tap the heart to keep your best shots together here.",
                        )
                    }
                } else {
                    item(key = "fav-count") {
                        Text(
                            "${state.favourites.size} favourite${if (state.favourites.size == 1) "" else "s"}",
                            style = Travel.type.bodySmall, color = c.inkFaint,
                            modifier = Modifier.padding(horizontal = Space.gutter).padding(bottom = Space.md),
                        )
                    }
                    photoMosaic(state.favourites, "fav") { nav.photo(0, it.id, favouritesOnly = true) }
                }
            }

            MemoriesView.TRIPS -> {
                if (state.galleries.isEmpty()) {
                    item(key = "empty-trips") {
                        EmptyState(Icons.Rounded.Luggage, "No trips yet", "Create a trip and its photo gallery will appear here.", actionLabel = "Create a trip", onAction = nav::newTrip)
                    }
                }
                state.galleries.forEach { g ->
                    item(key = "g-${g.trip.id}") {
                        GalleryCard(g, Modifier.padding(horizontal = Space.gutter).padding(bottom = Space.lg)) { nav.tripGallery(g.trip.id) }
                    }
                }
            }

            MemoriesView.JOURNAL -> {
                if (state.journal.isEmpty()) {
                    item(key = "empty-journal") {
                        EmptyState(Icons.Rounded.AutoStories, "No stories written yet", "Open a trip and use the Diary tab to write your first entry.")
                    }
                }
                state.journal.forEachIndexed { i, card ->
                    item(key = "j-${card.entry.id}") {
                        DiaryEntryCard(
                            model = card,
                            onClick = { nav.diaryEntry(card.entry.id) },
                            onPhotoClick = { nav.photo(card.entry.tripId, it.id) },
                            mirrored = i % 2 == 1,
                            showDate = true,
                            modifier = Modifier.padding(horizontal = Space.gutter).padding(bottom = Space.lg),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryCard(g: TripGalleryCard, modifier: Modifier, onClick: () -> Unit) {
    SurfaceCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(210.dp)) {
            CoverImage(g.coverPath, g.coverFrame, Modifier.fillMaxSize(), fallbackSeed = g.trip.name)
            BottomScrim(0.7f, 0.7f)
            Column(Modifier.align(Alignment.BottomStart).padding(Space.lg)) {
                Overline(Fmt.dateRange(g.trip.startDate, g.trip.endDate), color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(2.dp))
                Text(g.trip.name, style = Travel.type.headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(g.trip.destination, g.trip.country).filter { it.isNotBlank() }.joinToString(", "),
                    style = Travel.type.body, color = Color.White.copy(alpha = 0.85f), maxLines = 1,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (g.photoCount) { 0 -> "No photos yet"; 1 -> "1 photo"; else -> "${g.photoCount} photos" },
                style = Travel.type.label, color = Travel.colors.ink, modifier = Modifier.weight(1f),
            )
            if (g.favouriteCount > 0) Tag("♥ ${g.favouriteCount}", color = Travel.colors.gold)
            if (g.preview.isNotEmpty()) {
                Spacer(Modifier.width(Space.md))
                Box {
                    g.preview.forEachIndexed { i, p ->
                        PhotoTile(p, Modifier.padding(start = (i * 24).dp).size(34.dp), shape = Shapes.pill, showFavourite = false)
                    }
                }
            }
        }
    }
}
