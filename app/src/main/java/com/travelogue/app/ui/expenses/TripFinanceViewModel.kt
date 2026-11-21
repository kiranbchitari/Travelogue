package com.travelogue.app.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.data.db.entity.SettlementEntity
import com.travelogue.app.di.AppContainer
import com.travelogue.app.domain.SplitCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TripFinanceUi(
    val loading: Boolean = true,
    val money: TripMoney? = null,
    val balances: List<SplitCalculator.Balance> = emptyList(),
    val transfers: List<SplitCalculator.Transfer> = emptyList(),
    val hasSplits: Boolean = false,
)

class TripFinanceViewModel(private val container: AppContainer, private val tripId: Long) : ViewModel() {

    val state: StateFlow<TripFinanceUi> = container.tripMoneyFlow(tripId).map { money ->
        if (money == null) return@map TripFinanceUi(loading = false)
        val travellerIds = money.travellers.map { it.id }
        val balances = SplitCalculator.balances(travellerIds, money.expenses, money.splits, money.settlements, money.converter)
        TripFinanceUi(
            loading = false,
            money = money,
            balances = balances.sortedByDescending { it.net },
            transfers = SplitCalculator.suggestTransfers(balances),
            hasSplits = money.splits.isNotEmpty() || money.settlements.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripFinanceUi())

    fun markPaid(t: SplitCalculator.Transfer, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching {
            container.expenses.addSettlement(
                SettlementEntity(tripId = tripId, fromTravellerId = t.fromTravellerId, toTravellerId = t.toTravellerId, amount = t.amount, date = LocalDate.now()),
            )
        }.onFailure { onError(it.message ?: "Could not record the payment") }
    }

    fun deleteSettlement(id: Long) = viewModelScope.launch { runCatching { container.expenses.deleteSettlement(id) } }

    fun setRate(currency: String, rate: Double?, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching { container.trips.setRate(tripId, currency, rate) }.onFailure { onError(it.message ?: "Could not save the rate") }
    }
}
