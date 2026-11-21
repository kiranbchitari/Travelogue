package com.travelogue.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.core.util.toAmountOrNull
import com.travelogue.app.di.AppContainer
import com.travelogue.app.domain.CurrencyConverter
import com.travelogue.app.domain.HomeConverter
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RateRow(
    val currency: String,
    /** Rate saved in Settings (1 currency = rate home), if any. */
    val saved: Double?,
    /** A rate already entered on one of the trips, offered as a starting point. */
    val suggestion: Double?,
    val suggestionSource: String?,
    /** Expenses that can't be brought into the home currency right now. */
    val missingCount: Int,
    val missingAmount: Double,
)

data class RatesUi(val loading: Boolean = true, val home: String = "INR", val rows: List<RateRow> = emptyList())

class ExchangeRatesViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<RatesUi> = combine(
        c.trips.observeTrips(), c.expenses.observeAll(), c.trips.observeAllRates(), c.settings.settings,
    ) { trips, expenses, tripRates, settings ->
        val home = settings.defaultCurrency.uppercase()
        val homeConv = HomeConverter(home, settings.exchangeRates)
        val ratesByTrip = tripRates.groupBy { it.tripId }
        val converters = trips.associate { t -> t.id to CurrencyConverter(t.currency, ratesByTrip[t.id].orEmpty().associate { it.currency to it.rate }) }
        val currencies = (trips.map { it.currency.uppercase() } + expenses.map { it.currency.uppercase() }).toSet() - home
        val explicit = settings.exchangeRates
        val rows = currencies.sorted().map { cur ->
            val saved = explicit.firstOrNull { it.from == cur && it.to == home }?.rate
                ?: explicit.firstOrNull { it.from == home && it.to == cur }?.rate?.let { 1 / it }
            // Suggest from a trip: a home-currency trip that priced this currency, or a trip in this
            // currency that priced the home currency (inverted).
            val direct = trips.firstNotNullOfOrNull { t ->
                if (!t.currency.equals(home, true)) null else ratesByTrip[t.id]?.firstOrNull { it.currency.equals(cur, true) }?.let { it.rate to t.name }
            }
            val inverse = trips.firstNotNullOfOrNull { t ->
                if (!t.currency.equals(cur, true)) null else ratesByTrip[t.id]?.firstOrNull { it.currency.equals(home, true) }?.let { (1 / it.rate) to t.name }
            }
            val sug = direct ?: inverse
            val missing = expenses.filter { e ->
                (e.currency.equals(cur, true) || trips.firstOrNull { it.id == e.tripId }?.currency.equals(cur, true)) &&
                    homeConv.convert(e.amount, e.currency, converters[e.tripId]) == null
            }
            RateRow(cur, saved, sug?.first, sug?.second, missing.size, missing.filter { it.currency.equals(cur, true) }.sumOf { it.amount })
        }.sortedByDescending { it.missingCount }
        RatesUi(false, home, rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RatesUi())

    fun save(currency: String, rate: Double?) = viewModelScope.launch {
        c.settings.setExchangeRate(currency, state.value.home, rate)
    }
}

@Composable
fun ExchangeRatesScreen(nav: Nav) {
    val vm = appViewModel { ExchangeRatesViewModel(this) }
    val ui by vm.state.collectAsStateWithLifecycle()

    ScreenScaffold("Exchange rates", onBack = nav::back, subtitle = "Into ${ui.home}") { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.gutter, end = Space.gutter,
                top = padding.calculateTopPadding() + Space.sm,
                bottom = padding.calculateBottomPadding() + Space.huge,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item {
                Text(
                    "Home and the Expenses tab show every trip in ${ui.home}. Each trip's own rates are used first; " +
                        "these rates fill the gaps. You type them in — nothing is looked up online.",
                    style = Travel.type.body, color = Travel.colors.inkSoft,
                )
                Spacer(Modifier.height(Space.sm))
            }
            when {
                ui.loading -> item { LoadingState() }
                ui.rows.isEmpty() -> item {
                    EmptyState(
                        Icons.Rounded.CurrencyExchange,
                        "Everything is in ${ui.home}",
                        "When a trip or expense uses another currency, it will appear here so you can set a rate.",
                    )
                }
                else -> items(ui.rows, key = { it.currency }) { row -> RateCard(row, ui.home, vm::save) }
            }
        }
    }
}

@Composable
private fun RateCard(row: RateRow, home: String, onSave: (String, Double?) -> Unit) {
    val context = LocalContext.current
    var text by remember(row.saved) { mutableStateOf(row.saved?.let { plain(it) }.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.currency, style = Travel.type.title, color = Travel.colors.ink)
                Text(Fmt.currencySymbol(row.currency), style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
            }
            when {
                row.saved != null -> Tag("Rate set", color = Travel.colors.success)
                row.missingCount > 0 -> Tag("Needed", color = Travel.colors.gold)
                else -> Tag("Covered by trips", color = Travel.colors.inkSoft)
            }
        }
        if (row.missingCount > 0) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "${row.missingCount} ${if (row.missingCount == 1) "expense isn't" else "expenses aren't"} in your totals yet" +
                    if (row.missingAmount > 0) " (${Fmt.money(row.missingAmount, row.currency)})" else "",
                style = Travel.type.bodySmall, color = Travel.colors.gold,
            )
        }
        Spacer(Modifier.height(Space.md))
        AmountField("1 ${row.currency} =", text, { text = it; error = null }, currency = home, error = error, placeholder = "Rate in $home")
        if (row.suggestion != null && row.saved == null) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "Use ${Fmt.number(row.suggestion, 4)} from ${row.suggestionSource}",
                style = Travel.type.label, color = Travel.colors.accent,
                modifier = Modifier.clip(Shapes.pill).clickable { text = plain(row.suggestion); error = null }.padding(vertical = 6.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (row.saved != null) {
                TextButton(onClick = { onSave(row.currency, null); text = "" }) { Text("Remove", color = Travel.colors.danger, style = Travel.type.label) }
            }
            TextButton(onClick = {
                val v = text.toAmountOrNull()
                if (v == null || v <= 0) error = "Enter a rate above zero"
                else { onSave(row.currency, v); Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() }
            }) { Text("Save", color = Travel.colors.accent, style = Travel.type.label) }
        }
    }
}

/** Locale-independent number text for inputs ("92.5"), which the amount parser always reads back correctly. */
private fun plain(v: Double): String =
    java.math.BigDecimal(v).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
