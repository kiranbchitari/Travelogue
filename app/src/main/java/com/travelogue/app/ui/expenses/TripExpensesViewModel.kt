package com.travelogue.app.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.data.db.entity.ExpenseEntity
import com.travelogue.app.data.db.entity.SplitType
import com.travelogue.app.di.AppContainer
import kotlinx.coroutines.flow.*
import java.time.LocalDate

data class DayGroup(val date: LocalDate, val items: List<ExpenseEntity>, val total: Double)

data class TripExpensesUi(
    val loading: Boolean = true,
    val money: TripMoney? = null,
    val groups: List<DayGroup> = emptyList(),
    val filteredCount: Int = 0,
    val filteredTotal: Double = 0.0,
    val isFiltered: Boolean = false,
    val categoryFilter: Long? = null,
    val travellerFilter: Long? = null,
    val dateFilter: LocalDate? = null,
)

private data class Filters(val category: Long?, val traveller: Long?, val date: LocalDate?)

class TripExpensesViewModel(container: AppContainer, tripId: Long) : ViewModel() {
    private val category = MutableStateFlow<Long?>(null)
    private val traveller = MutableStateFlow<Long?>(null)
    private val date = MutableStateFlow<LocalDate?>(null)

    private val filters = combine(category, traveller, date) { c, t, d -> Filters(c, t, d) }

    val state: StateFlow<TripExpensesUi> = combine(container.tripMoneyFlow(tripId), filters) { money, f ->
        if (money == null) return@combine TripExpensesUi(loading = false)
        val splitsByExpense = money.splits.groupBy { it.expenseId }
        val filtered = money.expenses.filter { e ->
            (f.category == null || e.categoryId == f.category) &&
                (f.date == null || e.date == f.date) &&
                (f.traveller == null || e.paidByTravellerId == f.traveller ||
                    (e.splitType != SplitType.NONE && splitsByExpense[e.id].orEmpty().any { it.travellerId == f.traveller }))
        }
        val groups = filtered.groupBy { it.date }
            .toSortedMap(compareByDescending { it })
            .map { (d, items) -> DayGroup(d, items, items.sumOf { money.converter.toBase(it.amount, it.currency) ?: 0.0 }) }
        TripExpensesUi(
            loading = false,
            money = money,
            groups = groups,
            filteredCount = filtered.size,
            filteredTotal = groups.sumOf { it.total },
            isFiltered = f.category != null || f.traveller != null || f.date != null,
            categoryFilter = f.category,
            travellerFilter = f.traveller,
            dateFilter = f.date,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripExpensesUi())

    fun setCategory(id: Long?) { category.value = id }
    fun setTraveller(id: Long?) { traveller.value = id }
    fun setDate(d: LocalDate?) { date.value = d }
    fun clearFilters() { category.value = null; traveller.value = null; date.value = null }
}
