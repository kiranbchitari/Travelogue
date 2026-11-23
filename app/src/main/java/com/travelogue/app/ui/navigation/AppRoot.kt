package com.travelogue.app.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.ui.backup.BackupHost
import com.travelogue.app.ui.backup.BackupScreen
import com.travelogue.app.ui.bookings.BookingEditorScreen
import com.travelogue.app.ui.contacts.ContactEditorScreen
import com.travelogue.app.ui.diary.DiaryDetailScreen
import com.travelogue.app.ui.diary.DiaryEditorScreen
import com.travelogue.app.ui.expenses.ExpenseEditorScreen
import com.travelogue.app.ui.expenses.ExpensesScreen
import com.travelogue.app.ui.expenses.TripFinanceScreen
import com.travelogue.app.ui.holidays.HolidayPlannerScreen
import com.travelogue.app.ui.home.HomeScreen
import com.travelogue.app.ui.hotels.HotelDetailScreen
import com.travelogue.app.ui.hotels.HotelEditorScreen
import com.travelogue.app.ui.itinerary.ActivityEditorScreen
import com.travelogue.app.ui.memories.MemoriesScreen
import com.travelogue.app.ui.memories.PhotoViewerScreen
import com.travelogue.app.ui.memories.TripGalleryScreen
import com.travelogue.app.ui.memories.TripTimelineScreen
import com.travelogue.app.ui.places.PlaceDetailScreen
import com.travelogue.app.ui.places.PlaceEditorScreen
import com.travelogue.app.ui.settings.ExchangeRatesScreen
import com.travelogue.app.ui.settings.ManageCategoriesScreen
import com.travelogue.app.ui.settings.ManageTravellersScreen
import com.travelogue.app.ui.settings.SettingsScreen
import com.travelogue.app.ui.trips.TripsScreen
import com.travelogue.app.ui.trips.detail.TripDetailScreen
import com.travelogue.app.ui.trips.editor.TripEditorScreen
import java.time.LocalDate

private data class TabItem(val route: Any, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val Tabs = listOf(
    TabItem(HomeRoute, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    TabItem(TripsRoute, "Trips", Icons.Outlined.Luggage, Icons.Rounded.Luggage),
    TabItem(ExpensesRoute, "Expenses", Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
    TabItem(MemoriesRoute, "Memories", Icons.Outlined.AutoStories, Icons.Rounded.AutoStories),
    TabItem(SettingsRoute, "Settings", Icons.Outlined.Tune, Icons.Rounded.Tune),
)

@Composable
fun AppRoot() {
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    val entry by controller.currentBackStackEntryAsState()
    val dest = entry?.destination
    val currentTab = Tabs.firstOrNull { t -> dest?.hierarchy?.any { it.hasRoute(t.route::class) } == true }

    Box(Modifier.fillMaxSize().background(Travel.colors.background)) {
        NavHost(
            navController = controller,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 10 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(260)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(320)) { it / 10 } },
        ) {
            composable<HomeRoute>(enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(180)) }) { HomeScreen(nav) }
            composable<TripsRoute>(enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(180)) }) { TripsScreen(nav) }
            composable<ExpensesRoute>(enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(180)) }) { ExpensesScreen(nav) }
            composable<MemoriesRoute>(enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(180)) }) { MemoriesScreen(nav) }
            composable<SettingsRoute>(enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(180)) }) { SettingsScreen(nav) }

            composable<TripEditorRoute> {
                val r = it.toRoute<TripEditorRoute>()
                TripEditorScreen(nav, r.tripId, r.startDay.takeIf { d -> d > 0 }?.let(LocalDate::ofEpochDay), r.endDay.takeIf { d -> d > 0 }?.let(LocalDate::ofEpochDay))
            }
            composable<HolidayPlannerRoute> { HolidayPlannerScreen(nav) }
            composable<TripDetailRoute> { val r = it.toRoute<TripDetailRoute>(); TripDetailScreen(nav, r.tripId, r.tab) }
            composable<TripFinanceRoute> { TripFinanceScreen(nav, it.toRoute<TripFinanceRoute>().tripId) }
            composable<TripTimelineRoute> { TripTimelineScreen(nav, it.toRoute<TripTimelineRoute>().tripId) }
            composable<TripGalleryRoute> { TripGalleryScreen(nav, it.toRoute<TripGalleryRoute>().tripId) }

            composable<ExpenseEditorRoute> { val r = it.toRoute<ExpenseEditorRoute>(); ExpenseEditorScreen(nav, r.tripId, r.expenseId) }
            composable<ActivityEditorRoute> { val r = it.toRoute<ActivityEditorRoute>(); ActivityEditorScreen(nav, r.tripId, r.activityId, r.dayId) }
            composable<HotelEditorRoute> { val r = it.toRoute<HotelEditorRoute>(); HotelEditorScreen(nav, r.tripId, r.hotelId) }
            composable<HotelDetailRoute> { HotelDetailScreen(nav, it.toRoute<HotelDetailRoute>().hotelId) }
            composable<PlaceEditorRoute> { val r = it.toRoute<PlaceEditorRoute>(); PlaceEditorScreen(nav, r.tripId, r.placeId) }
            composable<PlaceDetailRoute> { PlaceDetailScreen(nav, it.toRoute<PlaceDetailRoute>().placeId) }
            composable<BookingEditorRoute> { val r = it.toRoute<BookingEditorRoute>(); BookingEditorScreen(nav, r.tripId, r.bookingId) }
            composable<ContactEditorRoute> { val r = it.toRoute<ContactEditorRoute>(); ContactEditorScreen(nav, r.tripId, r.contactId) }
            composable<DiaryEditorRoute> { val r = it.toRoute<DiaryEditorRoute>(); DiaryEditorScreen(nav, r.tripId, r.entryId) }
            composable<DiaryDetailRoute> { DiaryDetailScreen(nav, it.toRoute<DiaryDetailRoute>().entryId) }
            composable<PhotoViewerRoute>(
                enterTransition = { fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.96f) },
                popExitTransition = { fadeOut(tween(200)) + scaleOut(tween(220), targetScale = 0.96f) },
            ) { val r = it.toRoute<PhotoViewerRoute>(); PhotoViewerScreen(nav, r.tripId, r.photoId, r.favouritesOnly) }

            composable<ManageCategoriesRoute> { ManageCategoriesScreen(nav) }
            composable<ManageTravellersRoute> { ManageTravellersScreen(nav) }
            composable<ExchangeRatesRoute> { ExchangeRatesScreen(nav) }
            composable<BackupRoute> { BackupScreen(nav) }
        }

        // Tab screens scroll edge-to-edge; keep the status bar legible over their content.
        if (currentTab != null) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(Travel.colors.background),
            )
        }

        AnimatedVisibility(
            visible = currentTab != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            DockedBottomBar(current = currentTab, onSelect = { nav.tab(it.route) })
        }
        NavBarIcons(lightIcons = currentTab != null || Travel.colors.isDark)
        BackupHost()
    }
}

/**
 * Docked ink bar. Each tab is an icon over a tracked micro-label; the current one is lit and sits
 * under a short ember rule. The bar stays ink in both themes.
 */
@Composable
private fun DockedBottomBar(current: TabItem?, onSelect: (TabItem) -> Unit) {
    val c = Travel.colors
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(if (c.isDark) 0.dp else 24.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x55000000))
            .clip(shape)
            .background(if (c.isDark) Color(0xFF101010) else Color(0xFF0E0E0E))
            .border(1.dp, Color.White.copy(alpha = if (c.isDark) 0.08f else 0f), shape)
            .navigationBarsPadding()
            .height(66.dp)
            .padding(horizontal = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tabs.forEach { tab ->
            val selected = tab == current
            val tint by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = 0.42f), label = "tabTint")
            val rule by animateDpAsState(if (selected) 18.dp else 0.dp, label = "tabRule")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(rule).height(2.dp).clip(Shapes.pill).background(Color(0xFFFF7A45)))
                Spacer(Modifier.weight(1f))
                Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(5.dp))
                Text(
                    tab.label.uppercase(),
                    style = Travel.type.overline.copy(fontSize = 9.sp, letterSpacing = 0.12.em),
                    color = tint,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The bar is ink even in the light theme, so the system navigation icons over it must be light. */
@Composable
private fun NavBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    val dark = Travel.colors.isDark
    if (view.isInEditMode) return
    DisposableEffect(lightIcons, dark) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightNavigationBars = !lightIcons
        onDispose { controller?.isAppearanceLightNavigationBars = !dark }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
