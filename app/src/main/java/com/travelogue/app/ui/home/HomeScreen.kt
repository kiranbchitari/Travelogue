package com.travelogue.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.travelogue.app.core.designsystem.theme.Shapes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.charts.BarChart
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.common.tabContentPadding
import com.travelogue.app.ui.navigation.Nav
import com.travelogue.app.ui.navigation.TripTab
import com.travelogue.app.ui.navigation.TripsRoute
import com.travelogue.app.ui.trips.ExpenseLineRow
import com.travelogue.app.ui.trips.MemoryTile
import com.travelogue.app.ui.trips.TripHeroCard
import com.travelogue.app.ui.trips.TripRow
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(nav: Nav) {
    val vm = appViewModel(key = "home") { HomeViewModel(this) }
    val s by vm.state.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = tabContentPadding()) {
        item { Greeting() }

        if (!s.loaded) {
            item { LoadingState() }
            return@LazyColumn
        }

        if (s.tripCount == 0) {
            item { FirstRun(onCreate = nav::newTrip) }
            return@LazyColumn
        }

        item {
            PrimaryButton(
                "Create new trip",
                onClick = nav::newTrip,
                icon = Icons.Rounded.Add,
                modifier = Modifier.padding(horizontal = Space.gutter).fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.xxl))
        }

        item { StatsGrid(s) }

        if (s.upcoming.isNotEmpty()) {
            item {
                SectionHeader(
                    "Coming up",
                    subtitle = if (s.upcoming.size == 1) "1 trip on the horizon" else "${s.upcoming.size} trips on the horizon",
                    actionLabel = "All trips",
                    onAction = { nav.tab(TripsRoute) },
                    modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxxl, bottom = Space.md),
                )
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    items(s.upcoming, key = { it.trip.id }) { t ->
                        TripHeroCard(
                            t,
                            onClick = { nav.trip(t.trip.id) },
                            modifier = Modifier.fillParentMaxWidth(if (s.upcoming.size == 1) 1f else 0.86f),
                            height = 320.dp,
                        )
                    }
                }
            }
        }

        if (s.recent.isNotEmpty()) {
            item {
                SectionHeader(
                    "Recent journeys",
                    actionLabel = "See all",
                    onAction = { nav.tab(TripsRoute) },
                    modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxxl, bottom = Space.sm),
                )
            }
            item {
                SurfaceCard(Modifier.padding(horizontal = Space.gutter).fillMaxWidth(), contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm)) {
                    s.recent.forEachIndexed { i, t ->
                        if (i > 0) Hairline()
                        TripRow(t, onClick = { nav.trip(t.trip.id) })
                    }
                }
            }
        }

        item {
            SectionHeader(
                "Monthly spending",
                subtitle = "Last 12 months · all trips in ${s.defaultCurrency}",
                modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxxl, bottom = Space.md),
            )
            SurfaceCard(Modifier.padding(horizontal = Space.gutter).fillMaxWidth()) {
                Text(Fmt.money(s.monthlyTotal, s.defaultCurrency), style = Travel.type.number, color = Travel.colors.ink)
                Text("spent across the year · tap a bar for details", style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
                Spacer(Modifier.height(Space.sm))
                BarChart(s.monthly, valueLabel = { Fmt.money(it, s.defaultCurrency) }, height = 150.dp)
            }
        }

        if (s.recentExpenses.isNotEmpty()) {
            item {
                SectionHeader(
                    "Recent expenses",
                    modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxxl, bottom = Space.sm),
                )
                SurfaceCard(Modifier.padding(horizontal = Space.gutter).fillMaxWidth(), contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm)) {
                    s.recentExpenses.forEachIndexed { i, line ->
                        if (i > 0) Hairline()
                        ExpenseLineRow(
                            line.expense,
                            line.category,
                            subtitle = "${line.tripName} · ${Fmt.dayMonth(line.expense.date)}",
                            onClick = { nav.expenseEditor(line.expense.tripId, line.expense.id) },
                        )
                    }
                }
            }
        }

        if (s.recentPhotos.isNotEmpty()) {
            item {
                SectionHeader(
                    "Recently added memories",
                    modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxxl, bottom = Space.md),
                )
                LazyRow(contentPadding = PaddingValues(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    items(s.recentPhotos, key = { it.id }) { p -> MemoryTile(p, onClick = { nav.photo(0, p.id) }) }
                }
            }
        } else if (s.upcoming.isNotEmpty() || s.recent.isNotEmpty()) {
            item {
                val t = (s.upcoming + s.recent).first().trip
                SectionHeader(
                    "Memories",
                    modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.xxxl),
                )
                EmptyState(
                    Icons.Rounded.CalendarMonth,
                    "No memories yet",
                    "Write a diary entry or add photos to ${t.name} and they will appear here.",
                    actionLabel = "Open diary",
                    onAction = { nav.trip(t.id, TripTab.DIARY) },
                )
            }
        }
    }
}

@Composable
private fun Greeting() {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning."
        in 12..16 -> "Good afternoon."
        else -> "Good evening."
    }
    LargeHeader(overline = Fmt.weekday(LocalDate.now()), title = greeting, modifier = Modifier.padding(bottom = Space.md))
    Text(
        "Every journey, kept beautifully.",
        style = Travel.type.journal,
        color = Travel.colors.inkSoft,
        modifier = Modifier.padding(horizontal = Space.gutter).padding(bottom = Space.xl),
    )
}

@Composable
private fun StatsGrid(s: HomeState) {
    Column(Modifier.padding(horizontal = Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            StatTile("Trips", s.tripCount.toString(), Modifier.weight(1f), icon = Icons.Rounded.Luggage, tint = Travel.colors.accent)
            StatTile(
                "Spent on travel",
                Fmt.money(s.primaryTotal, s.defaultCurrency, compact = true),
                Modifier.weight(1f),
                icon = Icons.Rounded.Payments,
                tint = Travel.colors.secondary,
                caption = s.otherTotals.entries.takeIf { it.isNotEmpty() }
                    ?.joinToString(" · ", postfix = " without a rate") { "+" + Fmt.money(it.value, it.key, compact = true) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            StatTile("Places visited", s.placesCount.toString(), Modifier.weight(1f), icon = Icons.Rounded.Place, tint = Travel.colors.sage)
            StatTile("Days travelling", s.travelDays.toString(), Modifier.weight(1f), icon = Icons.Rounded.CalendarMonth, tint = Travel.colors.gold)
        }
    }
}

@Composable
private fun FirstRun(onCreate: () -> Unit) {
    Column(Modifier.padding(horizontal = Space.gutter)) {
        Box(Modifier.fillMaxWidth().height(260.dp).clip(Shapes.lg)) {
            GradientArt("travelogue-welcome", Modifier.matchParentSize())
            BottomScrim(0.6f, 0.55f)
            Column(Modifier.align(Alignment.BottomStart).padding(Space.xl)) {
                Overline("Your travel journal", color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(4.dp))
                Text("Begin your first story", style = Travel.type.headline, color = Color.White)
            }
        }
        Spacer(Modifier.height(Space.xl))
        Text(
            "Keep every trip in one place: plans and bookings, what you spent and who paid, where you stayed, and the moments you want to remember. Everything stays on this phone.",
            style = Travel.type.bodyLarge,
            color = Travel.colors.inkSoft,
        )
        Spacer(Modifier.height(Space.xl))
        PrimaryButton("Create your first trip", onCreate, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
    }
}
