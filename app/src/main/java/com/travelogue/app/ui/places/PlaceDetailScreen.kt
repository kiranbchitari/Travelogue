package com.travelogue.app.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.core.util.Intents
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav

@Composable
fun PlaceDetailScreen(nav: Nav, placeId: Long) {
    val vm = appViewModel(key = "place-$placeId") { PlaceDetailViewModel(journal, photos, placeId) }
    val state by vm.place.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val c = Travel.colors

    // The place was deleted (here or from the editor): leave once.
    LaunchedEffect(state) {
        if (!state.loading && state.value == null && !leaving) { leaving = true; nav.back() }
    }

    val place = state.value
    if (place == null) {
        Box(Modifier.fillMaxSize().background(c.background)) { LoadingState(Modifier.align(Alignment.Center)) }
        return
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        val heroListState = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(
            Modifier.fillMaxSize(),
            state = heroListState,
            contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Space.huge),
        ) {
            item {
                val pager = rememberPagerState { photos.size.coerceAtLeast(1) }
                Box(Modifier.fillMaxWidth().height(360.dp)) {
                    if (photos.isEmpty()) {
                        GradientArt(place.name, Modifier.fillMaxSize())
                    } else {
                        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                            val ph = photos[page]
                            PhotoImage(
                                ph.relativePath,
                                Modifier.fillMaxSize().pressable({ nav.photo(place.tripId, ph.id) }),
                                contentDescription = ph.caption.ifBlank { place.name },
                            )
                        }
                    }
                    BottomScrim(0.45f, 0.55f)
                    if (photos.size > 1) {
                        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = Space.lg), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(photos.size.coerceAtMost(10)) { i ->
                                val sel = pager.currentPage == i
                                Box(Modifier.size(width = if (sel) 18.dp else 6.dp, height = 6.dp).clip(Shapes.pill).background(Color.White.copy(alpha = if (sel) 1f else 0.5f)))
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = Space.gutter).padding(top = Space.xl)) {
                    Tag(place.category.label, icon = place.category.icon)
                    Spacer(Modifier.height(Space.md))
                    Text(place.name, style = Travel.type.display, color = c.ink)
                    Spacer(Modifier.height(Space.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        place.visitDate?.let { Text(Fmt.weekday(it), style = Travel.type.body, color = c.inkSoft) }
                        if (place.visitDate != null && place.rating > 0) {
                            Box(Modifier.padding(horizontal = Space.sm).size(4.dp).clip(CircleShape).background(c.inkFaint))
                        }
                        if (place.rating > 0) RatingStars(place.rating, size = 16.dp)
                    }
                }
            }
            if (place.hasMapTarget) {
                item {
                    Column(Modifier.padding(horizontal = Space.gutter).padding(top = Space.xl)) {
                        PrimaryButton("Open in maps", { openPlaceInMaps(context, place) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Map)
                        val extras = buildList {
                            if (place.mapsUrl.isNotBlank()) add(Triple("Map link", Icons.Rounded.Link) { Intents.openUrl(context, place.mapsUrl) })
                            if (place.latitude != null && place.longitude != null) add(Triple("Coordinates", Icons.Rounded.MyLocation) { Intents.openCoordinates(context, place.latitude, place.longitude, place.name) })
                            if (place.address.isNotBlank()) add(Triple("Address", Icons.Rounded.Place) { Intents.searchAddress(context, place.address) })
                        }
                        if (extras.size > 1) {
                            Spacer(Modifier.height(Space.sm))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                extras.forEach { (label, icon, action) -> SecondaryButton(label, action, Modifier.weight(1f), icon = icon) }
                            }
                        }
                    }
                }
            }
            if (place.description.isNotBlank()) {
                item {
                    Text(
                        place.description,
                        style = Travel.type.journal,
                        color = c.ink,
                        modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxl),
                    )
                }
            }
            if (place.address.isNotBlank() || (place.latitude != null && place.longitude != null)) {
                item {
                    SurfaceCard(Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxl).fillMaxWidth()) {
                        Overline("Where")
                        Spacer(Modifier.height(Space.xs))
                        if (place.address.isNotBlank()) {
                            InfoRow("Address", place.address, icon = Icons.Rounded.Place, onClick = { Intents.copy(context, "Address", place.address) })
                        }
                        if (place.latitude != null && place.longitude != null) {
                            if (place.address.isNotBlank()) Hairline()
                            val coords = "${Fmt.number(place.latitude, 5)}, ${Fmt.number(place.longitude, 5)}"
                            InfoRow("Coordinates", coords, icon = Icons.Rounded.MyLocation, onClick = { Intents.copy(context, "Coordinates", coords) })
                        }
                    }
                }
            }
            if (place.notes.isNotBlank()) {
                item {
                    Column(Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxl)) {
                        Overline("Notes")
                        Spacer(Modifier.height(Space.sm))
                        Text(place.notes, style = Travel.type.bodyLarge, color = c.inkSoft)
                    }
                }
            }
            if (photos.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Photographs",
                        subtitle = "${photos.size} ${if (photos.size == 1) "photo" else "photos"}",
                        modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxl, bottom = Space.md),
                    )
                }
                val rows = photos.chunked(3)
                items(rows.size) { r ->
                    Row(
                        Modifier.padding(horizontal = Space.gutter).padding(bottom = Space.sm),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        rows[r].forEach { ph ->
                            PhotoImage(
                                ph.relativePath,
                                Modifier.weight(1f).aspectRatio(1f).clip(Shapes.md).pressable({ nav.photo(place.tripId, ph.id) }),
                                contentDescription = ph.caption,
                            )
                        }
                        repeat(3 - rows[r].size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // Floating top controls over the hero
        ScrollStatusScrim(heroListState)
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.lg, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, nav::back, contentDescription = "Back", background = c.card.copy(alpha = 0.9f))
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Rounded.Edit, { nav.placeEditor(place.tripId, place.id) }, contentDescription = "Edit place", background = c.card.copy(alpha = 0.9f))
            Spacer(Modifier.width(Space.sm))
            CircleIconButton(Icons.Rounded.DeleteOutline, { confirmDelete = true }, contentDescription = "Delete place", background = c.card.copy(alpha = 0.9f), tint = c.danger)
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this place?",
            message = "The place and its photos will be removed from this trip. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(place.id) {} },
            onDismiss = { confirmDelete = false },
        )
    }
}
