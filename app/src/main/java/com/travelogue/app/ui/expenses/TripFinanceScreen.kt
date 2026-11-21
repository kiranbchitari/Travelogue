package com.travelogue.app.ui.expenses

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.charts.*
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.core.util.toAmountOrNull
import com.travelogue.app.data.db.entity.SettlementEntity
import com.travelogue.app.domain.SplitCalculator
import com.travelogue.app.domain.TripFinance
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TripFinanceScreen(nav: Nav, tripId: Long) {
    val vm = appViewModel(key = "trip-finance-$tripId") { TripFinanceViewModel(this, tripId) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    var confirmTransfer by remember { mutableStateOf<SplitCalculator.Transfer?>(null) }
    var confirmDeleteSettlement by remember { mutableStateOf<SettlementEntity?>(null) }

    val money = ui.money
    ScreenScaffold(
        title = "Financial summary",
        subtitle = money?.trip?.name,
        onBack = nav::back,
        actions = { CircleIconButton(Icons.Rounded.Add, { nav.expenseEditor(tripId) }, contentDescription = "Add expense") },
    ) { padding ->
        when {
            ui.loading -> LoadingState(Modifier.padding(padding))
            money == null -> EmptyState(Icons.Rounded.ReceiptLong, "Trip not found", "This trip may have been deleted.", Modifier.padding(padding))
            else -> {
                val f = money.finance
                val cur = money.trip.currency
                val fmt: (Double) -> String = { Fmt.money(it, cur) }
                LazyColumn(
                    Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.sm, bottom = Space.huge),
                    verticalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    item(key = "hero") { BudgetHero(f) }

                    if (f.hasUnconverted) {
                        item(key = "warn") {
                            Text(
                                "Not in totals: " + f.unconverted.entries.joinToString(" · ") { Fmt.money(it.value, it.key) } +
                                    ". Add a manual rate below to include them.",
                                style = Travel.type.bodySmall,
                                color = Travel.colors.gold,
                            )
                        }
                    }

                    if (f.expenseCount == 0) {
                        item(key = "empty") {
                            EmptyState(
                                Icons.Rounded.Insights,
                                "No spending to analyse yet",
                                "Add a few expenses and this page fills with charts, splits and balances.",
                                actionLabel = "Add expense",
                                onAction = { nav.expenseEditor(tripId) },
                            )
                        }
                    } else {
                        item(key = "stats") { KeyStats(f, money) }
                        item(key = "groups") { GroupBreakdown(f) }
                        item(key = "categories") { CategoryCard(f, fmt) }
                        item(key = "daily") { DailyCard(money, fmt) }
                        item(key = "timeline") { TimelineCard(f, fmt) }
                        if (f.byPerson.isNotEmpty()) item(key = "people") { PeopleCard(f, money, fmt) }
                    }

                    if (money.travellers.size >= 2) {
                        item(key = "balances-h") {
                            SectionHeader("Balances", Modifier.padding(top = Space.md), subtitle = "From split expenses and recorded payments")
                        }
                        item(key = "balances") {
                            BalancesCard(ui, money, fmt, onMarkPaid = { confirmTransfer = it })
                        }
                        if (money.settlements.isNotEmpty()) {
                            item(key = "settlements-h") { SectionHeader("Payments recorded") }
                            items(money.settlements, key = { "s-${it.id}" }) { s ->
                                SettlementRow(s, money, fmt, onDelete = { confirmDeleteSettlement = s })
                            }
                        }
                    }

                    if (money.foreignCurrencies.isNotEmpty()) {
                        item(key = "rates-h") {
                            SectionHeader(
                                "Currencies",
                                Modifier.padding(top = Space.md),
                                subtitle = "Manual conversion rates into $cur. Amounts without a rate are left out of totals.",
                            )
                        }
                        items(money.foreignCurrencies, key = { "r-$it" }) { code ->
                            val existing = money.rates.firstOrNull { it.currency.equals(code, true) }?.rate
                            val original = money.expenses.filter { it.currency.equals(code, true) }.sumOf { it.amount }
                            RateRow(code, cur, existing, original, onSave = { r -> vm.setRate(code, r, toast); toast(if (r == null) "Rate removed" else "Rate saved") })
                        }
                    }
                }
            }
        }
    }

    confirmTransfer?.let { t ->
        val names = money?.travellerNames.orEmpty()
        ConfirmDialog(
            title = "Mark as paid?",
            message = "${names[t.fromTravellerId] ?: "Someone"} paid ${names[t.toTravellerId] ?: "someone"} ${Fmt.money(t.amount, money?.trip?.currency ?: "")}. This records a settlement and updates balances.",
            confirmLabel = "Mark as paid",
            destructive = false,
            onConfirm = { vm.markPaid(t, toast) },
            onDismiss = { confirmTransfer = null },
        )
    }
    confirmDeleteSettlement?.let { s ->
        ConfirmDialog(
            title = "Remove this payment?",
            message = "The balances will go back to how they were before it was recorded.",
            confirmLabel = "Remove",
            onConfirm = { vm.deleteSettlement(s.id) },
            onDismiss = { confirmDeleteSettlement = null },
        )
    }
}

@Composable
private fun BudgetHero(f: TripFinance) {
    val cur = f.currency
    SurfaceCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BudgetRing(progress = if (f.budget > 0) f.budgetUsed else 0f, size = 124.dp, thickness = 12.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (f.budget > 0) "${Math.round(f.budgetUsed * 100)}%" else "—",
                        style = Travel.type.numberSmall,
                        color = Travel.colors.ink,
                    )
                    Text(if (f.budget > 0) "of budget" else "no budget", style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
                }
            }
            Spacer(Modifier.width(Space.xl))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                HeroFigure("Spent", Fmt.money(f.total, cur), Travel.colors.ink)
                HeroFigure("Planned budget", if (f.budget > 0) Fmt.money(f.budget, cur) else "Not set", Travel.colors.inkSoft)
                if (f.budget > 0) {
                    HeroFigure(
                        if (f.remaining >= 0) "Remaining" else "Over budget",
                        Fmt.money(kotlin.math.abs(f.remaining), cur),
                        if (f.remaining >= 0) Travel.colors.success else Travel.colors.danger,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroFigure(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        Text(value, style = Travel.type.numberSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun KeyStats(f: TripFinance, money: TripMoney) {
    val cur = f.currency
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            StatTile("Cost per person", Fmt.money(f.costPerPerson, cur), Modifier.weight(1f), icon = Icons.Rounded.Groups, tint = Travel.colors.secondary)
            StatTile("Average per day", Fmt.money(f.averageDaily, cur), Modifier.weight(1f), icon = Icons.Rounded.CalendarMonth, tint = Travel.colors.accent)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            val h = f.highest
            StatTile(
                "Highest expense",
                h?.let { money.converter.toBase(it.amount, it.currency)?.let { v -> Fmt.money(v, cur) } } ?: "—",
                Modifier.weight(1f),
                icon = Icons.Rounded.TrendingUp,
                tint = Travel.colors.gold,
                caption = h?.title,
            )
            StatTile("Expenses logged", f.expenseCount.toString(), Modifier.weight(1f), icon = Icons.Rounded.ReceiptLong, tint = Travel.colors.sage, caption = Fmt.days(money.trip.days))
        }
    }
}

@Composable
private fun GroupBreakdown(f: TripFinance) {
    val cur = f.currency
    val total = f.total.coerceAtLeast(0.0001)
    SurfaceCard(Modifier.fillMaxWidth(), contentPadding = SummaryPadding) {
        Overline("Essentials")
        Spacer(Modifier.height(Space.md))
        listOf(
            Triple("Accommodation", f.accommodation, Icons.Rounded.Hotel),
            Triple("Transportation", f.transport, Icons.Rounded.DirectionsCar),
            Triple("Food & dining", f.food, Icons.Rounded.Restaurant),
        ).forEachIndexed { i, (label, value, icon) ->
            val color = listOf(Travel.colors.chart[4], Travel.colors.secondary, Travel.colors.accent)[i]
            if (i > 0) Spacer(Modifier.height(Space.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, color, size = 36.dp, iconSize = 18.dp)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(label, style = Travel.type.body, color = Travel.colors.ink, modifier = Modifier.weight(1f))
                        Text(Fmt.money(value, cur), style = Travel.type.label, color = Travel.colors.ink)
                    }
                    Spacer(Modifier.height(6.dp))
                    ProgressBar((value / total).toFloat(), color = color, height = 6.dp)
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(f: TripFinance, fmt: (Double) -> String) {
    val fallback = Travel.colors.inkFaint
    val slices = f.byCategory.filter { it.total > 0 }.map {
        ChartSlice(it.category?.name ?: "Uncategorised", it.total, if (it.category != null) it.category.colorValue() else fallback)
    }
    var selected by remember(slices) { mutableStateOf<Int?>(null) }
    if (slices.isEmpty()) return
    SurfaceCard(Modifier.fillMaxWidth(), contentPadding = SummaryPadding) {
        Overline("By category")
        Spacer(Modifier.height(Space.lg))
        DonutChart(
            slices = slices,
            centerLabel = "Total",
            valueLabel = fmt,
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(Space.lg))
        ChartLegend(slices, fmt, selected = selected, onSelect = { selected = it })
    }
}

@Composable
private fun DailyCard(money: TripMoney, fmt: (Double) -> String) {
    val f = money.finance
    val byDay = f.byDay.associate { it.date to it.total }
    val trip = money.trip
    val days = remember(trip.startDate, trip.endDate, f.byDay) {
        val range = generateSequence(trip.startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(trip.endDate) }.take(62).toList()
        (range + f.byDay.map { it.date }).distinct().sorted()
    }
    val dayFmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    val bars = days.map { ChartBar(if (days.size <= 10) it.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())) else it.dayOfMonth.toString(), byDay[it] ?: 0.0) }
    val peak = f.byDay.maxByOrNull { it.total }
    SurfaceCard(Modifier.fillMaxWidth(), contentPadding = SummaryPadding) {
        Overline("Daily spending")
        if (peak != null) {
            Spacer(Modifier.height(Space.xs))
            Text("Biggest day: ${peak.date.format(dayFmt)} · ${fmt(peak.total)}", style = Travel.type.bodySmall, color = Travel.colors.inkSoft)
        }
        Spacer(Modifier.height(Space.sm))
        BarChart(bars, valueLabel = fmt, height = 160.dp)
    }
}

@Composable
private fun TimelineCard(f: TripFinance, fmt: (Double) -> String) {
    if (f.byDay.size < 2) return
    val dayFmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    var running = 0.0
    val points = f.byDay.map { running += it.total; ChartBar(it.date.format(dayFmt), running) }
    SurfaceCard(Modifier.fillMaxWidth(), contentPadding = SummaryPadding) {
        Overline("Spending over the trip")
        Spacer(Modifier.height(Space.xs))
        Text("Cumulative total, tap to inspect a day", style = Travel.type.bodySmall, color = Travel.colors.inkSoft)
        Spacer(Modifier.height(Space.sm))
        AreaChart(points, valueLabel = fmt, height = 140.dp)
    }
}

@Composable
private fun PeopleCard(f: TripFinance, money: TripMoney, fmt: (Double) -> String) {
    val max = f.byPerson.maxOf { it.total }.coerceAtLeast(0.0001)
    SurfaceCard(Modifier.fillMaxWidth(), contentPadding = SummaryPadding) {
        Overline("Individual spending")
        Spacer(Modifier.height(Space.xs))
        Text("What each person's share of the spending came to", style = Travel.type.bodySmall, color = Travel.colors.inkSoft)
        f.byPerson.forEachIndexed { i, p ->
            val name = money.travellerNames[p.travellerId] ?: "Unknown"
            Spacer(Modifier.height(Space.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialAvatar(name, size = 36.dp)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(name, style = Travel.type.body, color = Travel.colors.ink, modifier = Modifier.weight(1f))
                        Text(fmt(p.total), style = Travel.type.label, color = Travel.colors.ink)
                    }
                    Spacer(Modifier.height(6.dp))
                    ProgressBar((p.total / max).toFloat(), color = Travel.colors.chart[i % Travel.colors.chart.size], height = 6.dp)
                }
            }
        }
    }
}

@Composable
private fun BalancesCard(ui: TripFinanceUi, money: TripMoney, fmt: (Double) -> String, onMarkPaid: (SplitCalculator.Transfer) -> Unit) {
    SurfaceCard(Modifier.fillMaxWidth(), contentPadding = SummaryPadding) {
        if (!ui.hasSplits) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.CallSplit, Travel.colors.secondary)
                Spacer(Modifier.width(Space.md))
                Text(
                    "No shared expenses yet. Choose \"Split equally\" or \"Custom split\" when adding an expense to track who owes whom.",
                    style = Travel.type.body,
                    color = Travel.colors.inkSoft,
                )
            }
            return@SurfaceCard
        }
        ui.balances.forEachIndexed { i, b ->
            val name = money.travellerNames[b.travellerId] ?: "Unknown"
            if (i > 0) Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialAvatar(name, size = 36.dp)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(name, style = Travel.type.body, color = Travel.colors.ink)
                    Text("Paid ${fmt(b.paid)} · Share ${fmt(b.share)}", style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
                }
                val net = b.net
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        when {
                            net > 0.004 -> "+" + fmt(net)
                            net < -0.004 -> fmt(net)
                            else -> "Settled"
                        },
                        style = Travel.type.label,
                        color = when {
                            net > 0.004 -> Travel.colors.success
                            net < -0.004 -> Travel.colors.danger
                            else -> Travel.colors.inkFaint
                        },
                    )
                    Text(
                        when {
                            net > 0.004 -> "gets back"
                            net < -0.004 -> "owes"
                            else -> ""
                        },
                        style = Travel.type.bodySmall,
                        color = Travel.colors.inkFaint,
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.xl))
        Hairline()
        Spacer(Modifier.height(Space.lg))
        Overline("Who owes whom")
        Spacer(Modifier.height(Space.sm))
        if (ui.transfers.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Travel.colors.success, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Space.sm))
                Text("Everyone is settled up.", style = Travel.type.body, color = Travel.colors.inkSoft)
            }
        } else {
            ui.transfers.forEachIndexed { i, t ->
                if (i > 0) Spacer(Modifier.height(Space.md))
                val from = money.travellerNames[t.fromTravellerId] ?: "Unknown"
                val to = money.travellerNames[t.toTravellerId] ?: "Unknown"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(from, style = Travel.type.label, color = Travel.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Travel.colors.inkFaint, modifier = Modifier.padding(horizontal = 6.dp).size(14.dp))
                            Text(to, style = Travel.type.label, color = Travel.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        }
                        Text(fmt(t.amount), style = Travel.type.numberSmall, color = Travel.colors.accent)
                    }
                    SecondaryButton("Mark as paid", { onMarkPaid(t) }, icon = Icons.Rounded.Done)
                }
            }
        }
    }
}

@Composable
private fun SettlementRow(s: SettlementEntity, money: TripMoney, fmt: (Double) -> String, onDelete: () -> Unit) {
    val from = money.travellerNames[s.fromTravellerId] ?: "Unknown"
    val to = money.travellerNames[s.toTravellerId] ?: "Unknown"
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconBadge(Icons.Rounded.Handshake, Travel.colors.success, size = 40.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text("$from paid $to", style = Travel.type.body, color = Travel.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Fmt.date(s.date), style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        }
        Text(fmt(s.amount), style = Travel.type.label, color = Travel.colors.ink)
        Spacer(Modifier.width(Space.xs))
        CircleIconButton(Icons.Rounded.DeleteOutline, onDelete, contentDescription = "Remove payment", tint = Travel.colors.inkFaint, size = 36.dp, iconSize = 18.dp, background = Travel.colors.background)
    }
}

@Composable
private fun RateRow(code: String, base: String, existing: Double?, originalTotal: Double, onSave: (Double?) -> Unit) {
    var text by remember(code, existing) { mutableStateOf(existing?.let { plainNumber(it, 6) } ?: "") }
    var error by remember(code) { mutableStateOf<String?>(null) }
    SurfaceCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(code, style = Travel.type.titleSmall, color = Travel.colors.ink)
                Text(
                    "${Fmt.money(originalTotal, code)} spent" + (existing?.let { " · ≈ ${Fmt.money(originalTotal * it, base)}" } ?: " · not in totals"),
                    style = Travel.type.bodySmall,
                    color = if (existing == null) Travel.colors.gold else Travel.colors.inkFaint,
                )
            }
            if (existing != null) Tag("Rate set", color = Travel.colors.success)
        }
        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            FormField(
                label = "1 $code =",
                value = text,
                onValueChange = { s -> text = s.filter { it.isDigit() || it == '.' || it == ',' }; error = null },
                placeholder = "0.00",
                keyboardType = KeyboardType.Decimal,
                prefix = Fmt.currencySymbol(base) + " ",
                error = error,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                if (text.isBlank() && existing != null) "Remove" else "Save",
                onClick = {
                    if (text.isBlank()) {
                        if (existing != null) onSave(null)
                    } else {
                        val r = text.toAmountOrNull()
                        if (r == null || r <= 0) error = "Enter a rate above zero" else onSave(r)
                    }
                },
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Text(
            "Rates are entered by you; nothing is looked up online.",
            style = Travel.type.bodySmall,
            color = Travel.colors.inkFaint,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = Space.sm),
        )
    }
}
