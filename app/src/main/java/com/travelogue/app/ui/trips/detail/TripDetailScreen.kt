package com.travelogue.app.ui.trips.detail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.entity.TripEntity
import com.travelogue.app.data.db.entity.TripStatus
import com.travelogue.app.ui.backup.rememberTripExporter
import com.travelogue.app.ui.bookings.TripBookingsTab
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.contacts.TripContactsTab
import com.travelogue.app.ui.diary.TripDiaryTab
import com.travelogue.app.ui.expenses.TripExpensesTab
import com.travelogue.app.ui.hotels.TripHotelsTab
import com.travelogue.app.ui.itinerary.TripItineraryTab
import com.travelogue.app.ui.navigation.Nav
import com.travelogue.app.ui.navigation.TripTab
import com.travelogue.app.ui.places.TripPlacesTab
import com.travelogue.app.ui.trips.PhotoTag
import com.travelogue.app.ui.trips.phaseLabel
import kotlinx.coroutines.launch
import com.travelogue.app.domain.coverFrame

@Composable
fun TripDetailScreen(nav: Nav, tripId: Long, initialTab: Int) {
    val vm = appViewModel(key = "trip-detail-$tripId") { TripDetailViewModel(this, tripId) }
    val s by vm.state.collectAsStateWithLifecycle()

    when {
        !s.loaded -> Box(Modifier.fillMaxSize().background(Travel.colors.background)) { LoadingState(Modifier.align(Alignment.Center)) }
        s.trip == null -> ScreenScaffold("Trip", onBack = nav::back) { p ->
            EmptyState(
                Icons.Rounded.Luggage, "This trip is gone", "It may have been deleted.",
                Modifier.padding(p), actionLabel = "Go back", onAction = nav::back,
            )
        }
        else -> TripDetailContent(nav, vm, s, s.trip!!, initialTab)
    }
}

@Composable
private fun TripDetailContent(nav: Nav, vm: TripDetailViewModel, s: TripDetailState, trip: TripEntity, initialTab: Int) {
    val c = Travel.colors
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val maxHeader = 340.dp + statusTop
    val minHeader = 64.dp + statusTop
    val maxPx = with(density) { maxHeader.toPx() }
    val minPx = with(density) { minHeader.toPx() }
    var headerRaw by rememberSaveable { mutableFloatStateOf(Float.MAX_VALUE) }
    val headerPx = headerRaw.coerceIn(minPx, maxPx)
    val fraction = ((headerPx - minPx) / (maxPx - minPx)).coerceIn(0f, 1f)

    val connection = remember(minPx, maxPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy >= 0) return Offset.Zero
                val cur = headerRaw.coerceIn(minPx, maxPx)
                val new = (cur + dy).coerceIn(minPx, maxPx)
                val consumed = new - cur
                headerRaw = new
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy <= 0) return Offset.Zero
                val cur = headerRaw.coerceIn(minPx, maxPx)
                val new = (cur + dy).coerceIn(minPx, maxPx)
                val used = new - cur
                headerRaw = new
                return Offset(0f, used)
            }
        }
    }

    val pager = rememberPagerState(initialPage = initialTab.coerceIn(0, TripTab.titles.lastIndex)) { TripTab.titles.size }
    val scope = rememberCoroutineScope()
    val openTab: (Int) -> Unit = { i -> scope.launch { pager.animateScrollToPage(i) } }

    var confirmDelete by remember { mutableStateOf(false) }
    var statusSheet by remember { mutableStateOf(false) }
    val export = rememberTripExporter()

    StatusBarIcons(lightIcons = fraction > 0.35f || c.isDark)

    val menu = listOf(
        MenuItem("Edit trip", Icons.Rounded.Edit) { nav.editTrip(trip.id) },
        MenuItem("Change status", Icons.Rounded.Flag) { statusSheet = true },
        MenuItem(if (trip.archived) "Unarchive" else "Archive", if (trip.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive) {
            vm.setArchived(!trip.archived)
        },
        MenuItem("Export as ZIP", Icons.Rounded.IosShare) { export(trip.id, trip.name) },
        MenuItem("Delete trip", Icons.Rounded.DeleteOutline, destructive = true) { confirmDelete = true },
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .nestedScroll(connection),
    ) {
        Hero(
            trip = trip,
            fraction = fraction,
            height = with(density) { headerPx.toDp() },
            statusTop = statusTop,
            onBack = nav::back,
            menu = menu,
        )
        TabStrip(selected = pager.currentPage, onSelect = openTab)
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            key = { it },
        ) { page ->
            val m = Modifier.fillMaxSize()
            when (page) {
                TripTab.OVERVIEW -> TripOverviewTab(nav, s, openTab, m)
                TripTab.ITINERARY -> TripItineraryTab(nav, trip.id, m)
                TripTab.EXPENSES -> TripExpensesTab(nav, trip.id, m)
                TripTab.PLACES -> TripPlacesTab(nav, trip.id, m)
                TripTab.HOTELS -> TripHotelsTab(nav, trip.id, m)
                TripTab.BOOKINGS -> TripBookingsTab(nav, trip.id, m)
                TripTab.CONTACTS -> TripContactsTab(nav, trip.id, m)
                TripTab.DIARY -> TripDiaryTab(nav, trip.id, m)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete “${trip.name}”?",
            message = "This permanently removes the trip with all its expenses, stays, bookings, places, contacts, diary entries and photos from this phone. Export it first if you want a copy.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(); nav.back() },
            onDismiss = { confirmDelete = false },
        )
    }

    if (statusSheet) {
        TravelSheet(onDismiss = { statusSheet = false }, title = "Trip status") {
            TripStatus.entries.forEach { st ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Shapes.md)
                        .background(if (trip.status == st) st.color().copy(alpha = 0.1f) else Color.Transparent)
                        .clickable { vm.setStatus(st); statusSheet = false }
                        .padding(horizontal = Space.lg, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(Shapes.pill).background(st.color()))
                    Spacer(Modifier.width(Space.md))
                    Text(st.label, style = Travel.type.bodyLarge, color = c.ink, modifier = Modifier.weight(1f))
                    if (trip.status == st) Icon(Icons.Rounded.Check, null, tint = st.color())
                }
            }
        }
    }
}

/** Large cover that shrinks into a compact bar as the content scrolls. */
@Composable
private fun Hero(
    trip: TripEntity,
    fraction: Float,
    height: androidx.compose.ui.unit.Dp,
    statusTop: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    menu: List<MenuItem>,
) {
    val c = Travel.colors
    val buttonBg = lerpColor(c.card, Color.Black.copy(alpha = 0.32f), fraction)
    val buttonTint = lerpColor(c.ink, Color.White, fraction)
    Box(Modifier.fillMaxWidth().height(height).background(c.background)) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = fraction.coerceIn(0f, 1f) }) {
            CoverImage(trip.coverPath, trip.coverFrame, Modifier.matchParentSize(), contentDescription = trip.name, fallbackSeed = trip.uuid)
            Box(
                Modifier.fillMaxWidth().height(statusTop + 80.dp)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))),
            )
            BottomScrim(0.7f, 0.8f)
        }

        // Expanded title block
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = Space.gutter, vertical = Space.xl)
                .alpha(((fraction - 0.35f) / 0.65f).coerceIn(0f, 1f)),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PhotoTag(trip.status.label, dot = trip.status.color())
                trip.phaseLabel().takeIf { !it.equals(trip.status.label, ignoreCase = true) && !(it == "Completed") }?.let { PhotoTag(it) }
                if (trip.archived) PhotoTag("Archived")
            }
            Spacer(Modifier.height(Space.md))
            Text(trip.name, style = Travel.type.display, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Place, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    listOf(trip.destination, trip.country).filter { it.isNotBlank() }.joinToString(", "),
                    style = Travel.type.bodyLarge,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${Fmt.dateRange(trip.startDate, trip.endDate)} · ${Fmt.days(trip.days)}",
                style = Travel.type.body,
                color = Color.White.copy(alpha = 0.78f),
            )
        }

        // Top bar (always visible)
        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = statusTop)
                .height(64.dp)
                .padding(horizontal = Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back", background = buttonBg, tint = buttonTint)
            Spacer(Modifier.width(Space.md))
            Text(
                trip.name,
                style = Travel.type.title,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).alpha(((0.35f - fraction) / 0.35f).coerceIn(0f, 1f)),
            )
            OverflowMenu(menu, background = buttonBg, tint = buttonTint)
        }
    }
}

@Composable
private fun TabStrip(selected: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.animateScrollToItem((selected - 1).coerceAtLeast(0)) }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().background(Travel.colors.background).padding(vertical = Space.md),
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        items(TripTab.titles.size) { i ->
            ChoicePill(TripTab.titles[i], selected == i, { onSelect(i) })
        }
    }
}

/** Light status-bar icons while the dark cover photo is behind them. */
@Composable
private fun StatusBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    val dark = Travel.colors.isDark
    if (view.isInEditMode) return
    DisposableEffect(lightIcons, dark) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !lightIcons
        onDispose { controller?.isAppearanceLightStatusBars = !dark }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
