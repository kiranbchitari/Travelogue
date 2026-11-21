package com.travelogue.app.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav
import java.time.LocalDate

@Composable
fun TripExpensesTab(nav: Nav, tripId: Long, modifier: Modifier = Modifier) {
    val vm = appViewModel(key = "trip-expenses-$tripId") { TripExpensesViewModel(this, tripId) }
    val ui by vm.state.collectAsStateWithLifecycle()
    var showDates by remember { mutableStateOf(false) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier.fillMaxSize()) {
        val money = ui.money
        when {
            ui.loading -> LoadingState()
            money == null -> EmptyState(Icons.Rounded.ReceiptLong, "Trip not found", "This trip may have been deleted.")
            money.expenses.isEmpty() -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Space.lg, bottom = navBottom + 96.dp)) {
                item { BudgetSummary(money, onFinance = { nav.tripFinance(tripId) }) }
                item {
                    EmptyState(
                        Icons.Rounded.ReceiptLong,
                        "No expenses yet",
                        "Log every coffee, cab and souvenir. Totals, splits and charts build themselves.",
                        actionLabel = "Add expense",
                        onAction = { nav.expenseEditor(tripId) },
                    )
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = Space.lg, bottom = navBottom + 96.dp),
            ) {
                item(key = "summary") { BudgetSummary(money, onFinance = { nav.tripFinance(tripId) }) }

                if (money.finance.hasUnconverted) {
                    item(key = "warning") {
                        UnconvertedWarning(
                            currencies = money.finance.unconverted.keys.sorted(),
                            onClick = { nav.tripFinance(tripId) },
                        )
                    }
                }

                item(key = "filters") {
                    Column(Modifier.padding(top = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        val usedCategories = money.expenses.mapNotNull { it.categoryId }.distinct()
                            .mapNotNull { money.categoryById[it] }.sortedBy { it.sortOrder }
                        val catOptions: List<Long?> = listOf<Long?>(null) + usedCategories.map { it.id }
                        ChoiceRow(
                            options = catOptions,
                            selected = ui.categoryFilter,
                            label = { id -> id?.let { money.categoryById[it]?.name } ?: "All" },
                            onSelect = vm::setCategory,
                            dot = { id -> id?.let { money.categoryById[it].colorValue() } },
                        )
                        Row(
                            Modifier.padding(horizontal = Space.gutter),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChoicePill(
                                text = ui.dateFilter?.let(Fmt::weekdayShort) ?: "All days",
                                selected = ui.dateFilter != null,
                                onClick = { showDates = true },
                                icon = Icons.Rounded.CalendarMonth,
                            )
                            if (ui.isFiltered) {
                                TextButton(onClick = vm::clearFilters) {
                                    Text("Clear filters", style = Travel.type.label, color = Travel.colors.accent)
                                }
                            }
                        }
                        if (money.travellers.size >= 2) {
                            val tOptions: List<Long?> = listOf<Long?>(null) + money.travellers.map { it.id }
                            ChoiceRow(
                                options = tOptions,
                                selected = ui.travellerFilter,
                                label = { id -> id?.let { money.travellerNames[it] } ?: "Everyone" },
                                onSelect = vm::setTraveller,
                            )
                        }
                    }
                }

                if (ui.isFiltered) {
                    item(key = "filtered-total") {
                        Text(
                            "${ui.filteredCount} ${if (ui.filteredCount == 1) "expense" else "expenses"} · ${Fmt.money(ui.filteredTotal, money.trip.currency)}",
                            style = Travel.type.label,
                            color = Travel.colors.inkSoft,
                            modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.sm),
                        )
                    }
                }

                if (ui.groups.isEmpty()) {
                    item(key = "none") {
                        Text(
                            "Nothing matches these filters.",
                            style = Travel.type.body,
                            color = Travel.colors.inkFaint,
                            modifier = Modifier.fillMaxWidth().padding(Space.xxxl),
                        )
                    }
                }

                ui.groups.forEach { group ->
                    item(key = "day-${group.date}") {
                        DayHeader(group.date, Fmt.money(group.total, money.trip.currency))
                    }
                    items(group.items, key = { "e-${it.id}" }) { e ->
                        ExpenseRow(
                            expense = e,
                            category = e.categoryId?.let { money.categoryById[it] },
                            meta = expenseMeta(e, e.paidByTravellerId?.let { money.travellerNames[it] }),
                            convertedLabel = money.convertedLabel(e),
                            onClick = { nav.expenseEditor(tripId, e.id) },
                        )
                    }
                }
            }
        }

        AccentFab(
            icon = Icons.Rounded.Add,
            onClick = { nav.expenseEditor(tripId) },
            contentDescription = "Add expense",
            label = "Expense",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(Space.gutter),
        )
    }

    val money = ui.money
    if (showDates && money != null) {
        DayFilterSheet(
            start = money.trip.startDate,
            end = money.trip.endDate,
            extraDates = money.expenses.map { it.date },
            selected = ui.dateFilter,
            onSelect = { vm.setDate(it); showDates = false },
            onDismiss = { showDates = false },
        )
    }
}

/** Total, budget progress and a link to the full financial summary. */
@Composable
private fun BudgetSummary(money: TripMoney, onFinance: () -> Unit) {
    val f = money.finance
    val cur = money.trip.currency
    SurfaceCard(
        Modifier.padding(horizontal = Space.gutter).fillMaxWidth(),
        onClick = onFinance,
        contentPadding = SummaryPadding,
    ) {
        Overline("Spent so far")
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.money(f.total, cur), style = Travel.type.number, color = Travel.colors.ink, modifier = Modifier.weight(1f))
            Text(
                "${f.expenseCount} ${if (f.expenseCount == 1) "expense" else "expenses"}",
                style = Travel.type.bodySmall,
                color = Travel.colors.inkFaint,
            )
        }
        if (f.budget > 0) {
            Spacer(Modifier.height(Space.lg))
            ProgressBar(f.budgetUsed)
            Spacer(Modifier.height(Space.sm))
            Row {
                Text(
                    "${Math.round(f.budgetUsed * 100)}% of ${Fmt.money(f.budget, cur)}",
                    style = Travel.type.bodySmall,
                    color = Travel.colors.inkSoft,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (f.remaining >= 0) "${Fmt.money(f.remaining, cur)} left" else "${Fmt.money(-f.remaining, cur)} over",
                    style = Travel.type.label,
                    color = if (f.remaining >= 0) Travel.colors.success else Travel.colors.danger,
                )
            }
        } else {
            Spacer(Modifier.height(Space.sm))
            Text("No budget set for this trip", style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        }
        Spacer(Modifier.height(Space.lg))
        Hairline()
        Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
            Text("Financial summary & splits", style = Travel.type.label, color = Travel.colors.accent, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Travel.colors.accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun UnconvertedWarning(currencies: List<String>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = Space.gutter)
            .padding(top = Space.md)
            .fillMaxWidth()
            .clip(Shapes.md)
            .background(Travel.colors.goldSoft)
            .clickable(onClick = onClick)
            .padding(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = Travel.colors.gold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.md))
        Text(
            "Amounts in ${currencies.joinToString(", ")} aren't in the totals yet. Tap to set a manual rate.",
            style = Travel.type.bodySmall,
            color = Travel.colors.ink,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayFilterSheet(
    start: LocalDate,
    end: LocalDate,
    extraDates: List<LocalDate>,
    selected: LocalDate?,
    onSelect: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val days = remember(start, end, extraDates) {
        val range = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.take(120).toList()
        (range + extraDates).distinct().sorted()
    }
    val withExpenses = remember(extraDates) { extraDates.toSet() }
    TravelSheet(onDismiss = onDismiss, title = "Filter by day") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            ChoicePill("All days", selected == null, { onSelect(null) })
            days.forEach { d ->
                ChoicePill(
                    Fmt.weekdayShort(d),
                    selected == d,
                    { onSelect(d) },
                    leadingDot = if (d in withExpenses) Travel.colors.accent else null,
                )
            }
        }
    }
}
