package com.travelogue.app.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.charts.BarChart
import com.travelogue.app.core.designsystem.charts.ChartBar
import com.travelogue.app.core.designsystem.charts.ChartLegend
import com.travelogue.app.core.designsystem.charts.ChartSlice
import com.travelogue.app.core.designsystem.charts.DonutChart
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.common.tabContentPadding
import com.travelogue.app.ui.navigation.Nav
import java.time.YearMonth

@Composable
fun ExpensesScreen(nav: Nav) {
    val vm = appViewModel(key = "expenses-global") { ExpensesViewModel(this) }
    val ui by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = tabContentPadding(),
    ) {
        item(key = "header") { LargeHeader("Across all trips", "Expenses") }

        when {
            ui.loading -> item { LoadingState() }
            ui.trips.isEmpty() -> item {
                EmptyState(
                    Icons.Rounded.Luggage,
                    "No trips yet",
                    "Create your first trip and every expense you log will be gathered here with charts and totals.",
                    actionLabel = "Create a trip",
                    onAction = nav::newTrip,
                )
            }
            !ui.anyExpenses -> item {
                EmptyState(
                    Icons.Rounded.ReceiptLong,
                    "Nothing spent yet",
                    "Add expenses from inside a trip. They'll appear here across all your travels.",
                    actionLabel = "Add an expense",
                    onAction = { nav.expenseEditor(ui.trips.first().id) },
                )
            }
            else -> {
                item(key = "filters") { Filters(ui, vm) }
                item(key = "totals") { TotalsCard(ui) }

                if (ui.filtered.isEmpty()) {
                    item(key = "none") {
                        EmptyState(
                            Icons.Rounded.SearchOff,
                            "No matching expenses",
                            "Try a different trip, category or date range.",
                            actionLabel = "Clear filters",
                            onAction = { vm.update { GlobalFilters() } },
                        )
                    }
                } else {
                    if (ui.byCategory.isNotEmpty()) item(key = "donut") { CategoryChartCard(ui) }
                    if (ui.periods.size > 1) item(key = "bars") { PeriodChartCard(ui) }

                    val byMonth = ui.filtered.groupBy { YearMonth.from(it.expense.date) }
                    byMonth.forEach { (ym, monthRows) ->
                        item(key = "m-$ym") { MonthHeader(ym, monthRows, ui.primaryCurrency) }
                        monthRows.groupBy { it.expense.date }.forEach { (date, rows) ->
                            item(key = "d-$date") {
                                val dayTotal = rows.filter { it.trip.currency.equals(ui.primaryCurrency, true) }.sumOf { it.converted ?: 0.0 }
                                DayHeader(date, if (dayTotal > 0) Fmt.money(dayTotal, ui.primaryCurrency) else null)
                            }
                            items(rows, key = { "e-${it.expense.id}" }) { g ->
                                val e = g.expense
                                ExpenseRow(
                                    expense = e,
                                    category = e.categoryId?.let { ui.categoryById[it] },
                                    meta = expenseMeta(e, e.paidByTravellerId?.let { ui.travellerNames[it] }),
                                    convertedLabel = when {
                                        e.currency.equals(g.trip.currency, true) -> null
                                        g.converted == null -> "No rate set"
                                        else -> "≈ " + Fmt.money(g.converted, g.trip.currency)
                                    },
                                    overline = g.trip.name,
                                    onClick = { nav.expenseEditor(e.tripId, e.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Filters(ui: ExpensesUi, vm: ExpensesViewModel) {
    val f = ui.filters
    Column(Modifier.padding(top = Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        val tripOptions: List<Long?> = listOf<Long?>(null) + ui.trips.map { it.id }
        val tripNames = ui.trips.associate { it.id to it.name }
        ChoiceRow(
            options = tripOptions,
            selected = f.tripId,
            label = { id -> id?.let { tripNames[it] } ?: "All trips" },
            onSelect = { id -> vm.update { it.copy(tripId = id, travellerId = null) } },
        )
        ChoiceRow(
            options = DateRange.entries,
            selected = f.range,
            label = { it.label },
            onSelect = { r -> vm.update { it.copy(range = r) } },
        )
        if (f.range == DateRange.CUSTOM) {
            Row(Modifier.padding(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                DateField(
                    "From", f.customStart, { d -> vm.update { it.copy(customStart = d) } }, Modifier.weight(1f),
                    placeholder = "Any", onClear = { vm.update { it.copy(customStart = null) } },
                    error = if (f.customStart != null && f.customEnd != null && f.customEnd.isBefore(f.customStart)) "After end" else null,
                )
                DateField(
                    "To", f.customEnd, { d -> vm.update { it.copy(customEnd = d) } }, Modifier.weight(1f),
                    placeholder = "Any", onClear = { vm.update { it.copy(customEnd = null) } },
                )
            }
        }
        val usedCatIds = ui.filtered.mapNotNull { it.expense.categoryId }.toSet() + listOfNotNull(f.categoryId)
        val cats = ui.categories.filter { it.id in usedCatIds }
        if (cats.isNotEmpty()) {
            val catOptions: List<Long?> = listOf<Long?>(null) + cats.map { it.id }
            ChoiceRow(
                options = catOptions,
                selected = f.categoryId,
                label = { id -> id?.let { ui.categoryById[it]?.name } ?: "All categories" },
                onSelect = { id -> vm.update { it.copy(categoryId = id) } },
                dot = { id -> id?.let { ui.categoryById[it].colorValue() } },
            )
        }
        if (ui.travellerOptions.size >= 2) {
            val tOptions: List<Long?> = listOf<Long?>(null) + ui.travellerOptions.map { it.id }
            ChoiceRow(
                options = tOptions,
                selected = f.travellerId,
                label = { id -> id?.let { ui.travellerNames[it] } ?: "Everyone" },
                onSelect = { id -> vm.update { it.copy(travellerId = id) } },
            )
        }
        val active = f.tripId != null || f.categoryId != null || f.travellerId != null || f.range != DateRange.ALL
        if (active) {
            TextButton(onClick = { vm.update { GlobalFilters() } }, modifier = Modifier.padding(horizontal = Space.md)) {
                Text("Reset filters", style = Travel.type.label, color = Travel.colors.accent)
            }
        }
    }
}

@Composable
private fun TotalsCard(ui: ExpensesUi) {
    SurfaceCard(
        Modifier.padding(horizontal = Space.gutter).padding(top = Space.md).fillMaxWidth(),
        contentPadding = SummaryPadding,
    ) {
        Overline("Total spent")
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.money(ui.primaryTotal, ui.primaryCurrency), style = Travel.type.number, color = Travel.colors.ink, modifier = Modifier.weight(1f))
            Text(
                "${ui.filtered.size} ${if (ui.filtered.size == 1) "expense" else "expenses"}",
                style = Travel.type.bodySmall,
                color = Travel.colors.inkFaint,
            )
        }
        val others = ui.totals.filter { it.currency != ui.primaryCurrency }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            Hairline()
            Spacer(Modifier.height(Space.sm))
            Text("Trips in other currencies", style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
            others.forEach {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(it.currency, style = Travel.type.body, color = Travel.colors.inkSoft, modifier = Modifier.weight(1f))
                    Text(Fmt.money(it.total, it.currency), style = Travel.type.label, color = Travel.colors.ink)
                }
            }
        }
        if (ui.unconvertedCount > 0) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "${ui.unconvertedCount} foreign-currency ${if (ui.unconvertedCount == 1) "expense isn't" else "expenses aren't"} included — set a manual rate in that trip's financial summary.",
                style = Travel.type.bodySmall,
                color = Travel.colors.gold,
            )
        }
    }
}

@Composable
private fun CategoryChartCard(ui: ExpensesUi) {
    val fallback = Travel.colors.inkFaint
    val slices = ui.byCategory.map { ChartSlice(it.category?.name ?: "Uncategorised", it.total, if (it.category != null) it.category.colorValue() else fallback) }
    var selected by remember(slices) { mutableStateOf<Int?>(null) }
    val fmt: (Double) -> String = { Fmt.money(it, ui.primaryCurrency) }
    SurfaceCard(
        Modifier.padding(horizontal = Space.gutter).padding(top = Space.lg).fillMaxWidth(),
        contentPadding = SummaryPadding,
    ) {
        Overline("Where it went")
        if (ui.totals.size > 1) {
            Text("Showing ${ui.primaryCurrency} trips", style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        }
        Spacer(Modifier.height(Space.lg))
        DonutChart(
            slices = slices,
            centerLabel = "Total",
            valueLabel = fmt,
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            size = 190.dp,
        )
        Spacer(Modifier.height(Space.lg))
        ChartLegend(slices.take(8), fmt, selected = selected?.takeIf { it < 8 }, onSelect = { selected = it })
    }
}

@Composable
private fun PeriodChartCard(ui: ExpensesUi) {
    val fmt: (Double) -> String = { Fmt.money(it, ui.primaryCurrency) }
    SurfaceCard(
        Modifier.padding(horizontal = Space.gutter).padding(top = Space.lg).fillMaxWidth(),
        contentPadding = SummaryPadding,
    ) {
        Overline(if (ui.periodsAreDays) "Daily spending" else "Monthly spending")
        Text("Tap a bar to see the amount", style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        Spacer(Modifier.height(Space.sm))
        BarChart(ui.periods.map { ChartBar(it.label, it.total) }, valueLabel = fmt, height = 150.dp, color = Travel.colors.secondary)
    }
}

@Composable
private fun MonthHeader(ym: YearMonth, rows: List<GlobalExpense>, primary: String) {
    val total = rows.filter { it.trip.currency.equals(primary, true) }.sumOf { it.converted ?: 0.0 }
    Row(
        Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, top = Space.xxl),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(Fmt.monthYear(ym.atDay(1)), style = Travel.type.title, color = Travel.colors.ink, modifier = Modifier.weight(1f))
        if (total > 0) Text(Fmt.money(total, primary), style = Travel.type.numberSmall, color = Travel.colors.inkSoft)
    }
}
