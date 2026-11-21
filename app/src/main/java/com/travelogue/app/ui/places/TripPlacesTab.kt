package com.travelogue.app.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.entity.PlaceCategory
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav

@Composable
fun TripPlacesTab(nav: Nav, tripId: Long, modifier: Modifier = Modifier) {
    val vm = appViewModel(key = "places-$tripId") { TripPlacesViewModel(journal, photos, tripId) }
    val items by vm.places.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<PlaceCategory?>(null) }
    val context = LocalContext.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier.fillMaxSize()) {
        val all = items
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Space.lg, bottom = navBottom + 96.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            when {
                all == null -> item { LoadingState() }
                all.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Rounded.Place,
                        title = "No places yet",
                        message = "Beaches, cafés, viewpoints, temples — keep a little journal of everywhere you wandered.",
                        actionLabel = "Add a place",
                        onAction = { nav.placeEditor(tripId) },
                    )
                }
                else -> {
                    val categories = all.map { it.place.category }.distinct().sortedBy { it.ordinal }
                    if (categories.size > 1) {
                        item {
                            ChoiceRow(
                                options = listOf<PlaceCategory?>(null) + categories,
                                selected = filter?.takeIf { it in categories },
                                label = { it?.label ?: "All · ${all.size}" },
                                onSelect = { filter = it },
                            )
                        }
                    }
                    // A filter whose chip has disappeared (its last place was deleted) no longer applies.
                    val active = filter?.takeIf { categories.size > 1 && it in categories }
                    val shown = all.filter { active == null || it.place.category == active }
                    item {
                        Text(
                            "${shown.size} ${if (shown.size == 1) "place" else "places"} · ${shown.count { it.place.visitDate != null }} dated",
                            style = Travel.type.bodySmall,
                            color = Travel.colors.inkFaint,
                            modifier = Modifier.padding(horizontal = Space.gutter),
                        )
                    }
                    shown.firstOrNull()?.let { first ->
                        item(key = "feature-${first.place.id}") {
                            FeaturePlaceCard(
                                item = first,
                                onClick = { nav.place(first.place.id) },
                                onMap = { openPlaceInMaps(context, first.place) },
                                modifier = Modifier.padding(horizontal = Space.gutter),
                            )
                        }
                    }
                    items(shown.drop(1), key = { it.place.id }) { item ->
                        PlaceRow(
                            item = item,
                            onClick = { nav.place(item.place.id) },
                            onMap = { openPlaceInMaps(context, item.place) },
                            modifier = Modifier.padding(horizontal = Space.gutter),
                        )
                    }
                }
            }
        }
        AccentFab(
            Icons.Rounded.Add,
            { nav.placeEditor(tripId) },
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(Space.gutter),
            contentDescription = "Add place",
            label = "Place",
        )
    }
}

@Composable
private fun FeaturePlaceCard(item: PlaceItem, onClick: () -> Unit, onMap: () -> Unit, modifier: Modifier = Modifier) {
    val p = item.place
    SurfaceCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(230.dp)) {
            PhotoImage(item.coverPath, Modifier.fillMaxSize(), contentDescription = p.name, fallbackSeed = p.name)
            BottomScrim()
            if (p.hasMapTarget) {
                CircleIconButton(
                    Icons.Rounded.Map, onMap,
                    Modifier.align(Alignment.TopEnd).padding(Space.md),
                    contentDescription = "Open in maps",
                    background = Color.Black.copy(alpha = 0.35f), tint = Color.White, size = 40.dp,
                )
            }
            Column(Modifier.align(Alignment.BottomStart).padding(Space.lg)) {
                Tag(p.category.label, color = Color.White, background = Color.White.copy(alpha = 0.2f), icon = p.category.icon)
                Spacer(Modifier.height(Space.sm))
                Text(p.name, style = Travel.type.headline, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val meta = listOfNotNull(p.visitDate?.let(Fmt::weekdayShort), if (item.photoCount > 0) "${item.photoCount} photos" else null)
                    if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = Travel.type.bodySmall, color = Color.White.copy(alpha = 0.85f))
                    if (p.rating > 0) {
                        Spacer(Modifier.width(Space.sm))
                        RatingStars(p.rating, size = 13.dp)
                    }
                }
            }
        }
        if (p.description.isNotBlank() || p.address.isNotBlank()) {
            Column(Modifier.padding(Space.lg)) {
                if (p.description.isNotBlank()) {
                    Text(p.description, style = Travel.type.journal.copy(fontSize = Travel.type.bodyLarge.fontSize), color = Travel.colors.inkSoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (p.address.isNotBlank()) {
                    if (p.description.isNotBlank()) Spacer(Modifier.height(Space.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Place, null, tint = Travel.colors.inkFaint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(p.address, style = Travel.type.bodySmall, color = Travel.colors.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(item: PlaceItem, onClick: () -> Unit, onMap: () -> Unit, modifier: Modifier = Modifier) {
    val p = item.place
    val c = Travel.colors
    SurfaceCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp).clip(Shapes.md)) {
                if (item.coverPath != null) {
                    PhotoImage(item.coverPath, Modifier.fillMaxSize(), contentDescription = p.name)
                } else {
                    Box(Modifier.fillMaxSize().background(c.accentSoft), contentAlignment = Alignment.Center) {
                        Icon(p.category.icon, null, tint = c.accent, modifier = Modifier.size(30.dp))
                    }
                }
                if (item.photoCount > 1) {
                    Row(
                        Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(Shapes.pill).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("${item.photoCount}", style = Travel.type.bodySmall.copy(fontSize = Travel.type.overline.fontSize), color = Color.White)
                    }
                }
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = Travel.type.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(p.category.label, color = c.secondary)
                    p.visitDate?.let {
                        Spacer(Modifier.width(Space.sm))
                        Text(Fmt.dayMonth(it), style = Travel.type.bodySmall, color = c.inkFaint)
                    }
                }
                if (p.rating > 0) {
                    Spacer(Modifier.height(4.dp))
                    RatingStars(p.rating, size = 13.dp)
                }
                if (p.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(p.description, style = Travel.type.bodySmall, color = c.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (p.hasMapTarget) {
                Spacer(Modifier.width(Space.sm))
                CircleIconButton(Icons.Rounded.Map, onMap, contentDescription = "Open in maps", background = c.backgroundDeep, tint = c.accent, size = 40.dp, iconSize = 18.dp)
            }
        }
    }
}
