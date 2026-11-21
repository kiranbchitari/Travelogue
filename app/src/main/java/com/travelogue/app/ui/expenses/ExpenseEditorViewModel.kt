package com.travelogue.app.ui.expenses

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.core.util.toAmountOrNull
import com.travelogue.app.data.db.entity.*
import com.travelogue.app.data.repository.ExpenseRepository
import com.travelogue.app.data.repository.TripRepository
import com.travelogue.app.domain.SplitCalculator
import com.travelogue.app.domain.roundMoney
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

class ExpenseEditorViewModel(
    private val trips: TripRepository,
    private val expenses: ExpenseRepository,
    val tripId: Long,
    val expenseId: Long,
) : ViewModel() {

    val isEdit = expenseId != 0L

    val categories: StateFlow<List<ExpenseCategoryEntity>> =
        expenses.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var loaded by mutableStateOf(false); private set
    var trip by mutableStateOf<TripEntity?>(null); private set
    var travellers by mutableStateOf<List<TravellerEntity>>(emptyList()); private set

    var title by mutableStateOf("")
    var amount by mutableStateOf("")
    var currency by mutableStateOf("INR")
    var categoryId by mutableStateOf<Long?>(null)
    var date by mutableStateOf(LocalDate.now())
    var time by mutableStateOf<LocalTime?>(null)
    var location by mutableStateOf("")
    var paidBy by mutableStateOf<Long?>(null)
    var paymentMethod by mutableStateOf(PaymentMethod.CASH)
    var description by mutableStateOf("")
    var notes by mutableStateOf("")

    var receiptPath by mutableStateOf<String?>(null); private set
    var pendingReceipt by mutableStateOf<Uri?>(null); private set
    private var removeReceipt = false

    var splitType by mutableStateOf(SplitType.NONE)
    var participants by mutableStateOf<Set<Long>>(emptySet())
    var customShares by mutableStateOf<Map<Long, String>>(emptyMap())

    var errors by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var saving by mutableStateOf(false); private set

    private var original: ExpenseEntity? = null

    init {
        viewModelScope.launch {
            val t = trips.get(tripId)
            trip = t
            travellers = trips.getTravellers(tripId)
            if (isEdit) {
                val e = expenses.get(expenseId)
                if (e != null) {
                    original = e
                    title = e.title
                    amount = plainNumber(e.amount)
                    currency = e.currency
                    categoryId = e.categoryId
                    date = e.date
                    time = e.time
                    location = e.location
                    paidBy = e.paidByTravellerId
                    paymentMethod = e.paymentMethod
                    description = e.description
                    notes = e.notes
                    receiptPath = e.receiptPath
                    splitType = e.splitType
                    val splits = expenses.getSplits(e.id)
                    participants = splits.map { it.travellerId }.toSet().ifEmpty { travellers.map { it.id }.toSet() }
                    customShares = splits.associate { it.travellerId to plainNumber(it.amount) }
                }
            } else if (t != null) {
                currency = t.currency
                val today = LocalDate.now()
                date = if (!today.isBefore(t.startDate) && !today.isAfter(t.endDate)) today else t.startDate
                time = if (date == today) LocalTime.now().withSecond(0).withNano(0) else null
                paidBy = travellers.firstOrNull()?.id
                participants = travellers.map { it.id }.toSet()
            }
            if (participants.isEmpty()) participants = travellers.map { it.id }.toSet()
            loaded = true
        }
    }

    val canSplit: Boolean get() = travellers.size >= 2

    fun pickReceipt(uri: Uri) { pendingReceipt = uri; removeReceipt = false }
    fun clearReceipt() { pendingReceipt = null; if (receiptPath != null) removeReceipt = true; receiptPath = null }

    fun toggleParticipant(id: Long) {
        participants = if (id in participants) participants - id else participants + id
    }

    fun setShare(id: Long, value: String) {
        customShares = customShares + (id to value.filter { it.isDigit() || it == '.' || it == ',' })
    }

    /** Fills custom shares with an equal split of the current amount (handy starting point). */
    fun prefillCustomEqually() {
        val total = amount.toAmountOrNull() ?: return
        val shares = SplitCalculator.equalShares(total, travellers.map { it.id })
        customShares = shares.mapValues { plainNumber(it.value) }
    }

    val amountValue: Double? get() = amount.toAmountOrNull()

    /** Equal split preview for the selected participants. */
    val equalShares: Map<Long, Double>
        get() = SplitCalculator.equalShares(amountValue ?: 0.0, travellers.map { it.id }.filter { it in participants })

    val customTotal: Double get() = customShares.values.sumOf { it.toAmountOrNull() ?: 0.0 }.roundMoney()

    /** Amount still to allocate in a custom split (positive = unallocated, negative = over). */
    val customRemaining: Double get() = ((amountValue ?: 0.0) - customTotal).roundMoney()

    private fun validate(): Boolean {
        val e = mutableMapOf<String, String>()
        if (title.isBlank()) e["title"] = "Give this expense a title"
        val a = amountValue
        if (a == null) e["amount"] = "Enter an amount" else if (a <= 0) e["amount"] = "Amount must be more than zero"
        else if (a > 1_000_000_000) e["amount"] = "That amount looks too large"
        if (currency.isBlank() || currency.length !in 3..4) e["currency"] = "Choose a currency"
        if (categoryId == null) e["category"] = "Choose a category"
        if (splitType != SplitType.NONE) {
            if (paidBy == null) e["paidBy"] = "Choose who paid to split this expense"
            when (splitType) {
                SplitType.EQUAL -> if (participants.isEmpty()) e["split"] = "Choose at least one person to share with"
                SplitType.CUSTOM -> {
                    if (customShares.values.none { (it.toAmountOrNull() ?: 0.0) > 0 }) e["split"] = "Enter how much each person owes"
                    else if (a != null && abs(customRemaining) > 0.009) {
                        e["split"] = if (customRemaining > 0) "${Fmt.money(customRemaining, currency)} still to allocate"
                        else "Shares exceed the total by ${Fmt.money(-customRemaining, currency)}"
                    }
                }
                SplitType.NONE -> Unit
            }
        }
        errors = e
        return e.isEmpty()
    }

    fun save(onSaved: () -> Unit, onError: (String) -> Unit) {
        if (saving) return
        if (!validate()) { onError(errors.values.first()); return }
        val a = amountValue ?: return
        saving = true
        viewModelScope.launch {
            try {
                val effectiveSplit = if (canSplit) splitType else SplitType.NONE
                val shares: Map<Long, Double> = when (effectiveSplit) {
                    SplitType.EQUAL -> equalShares
                    SplitType.CUSTOM -> customShares.mapNotNull { (k, v) -> v.toAmountOrNull()?.takeIf { it > 0 }?.let { k to it.roundMoney() } }.toMap()
                    SplitType.NONE -> emptyMap()
                }
                val base = original ?: ExpenseEntity(tripId = tripId, title = "", amount = 0.0, currency = currency, categoryId = null, date = date)
                val row = base.copy(
                    title = title.trim(),
                    amount = a.roundMoney(),
                    currency = currency.uppercase(),
                    categoryId = categoryId,
                    date = date,
                    time = time,
                    location = location.trim(),
                    paidByTravellerId = paidBy,
                    paymentMethod = paymentMethod,
                    description = description.trim(),
                    notes = notes.trim(),
                    splitType = effectiveSplit,
                )
                expenses.save(row, shares, newReceipt = pendingReceipt, removeReceipt = removeReceipt && pendingReceipt == null)
                onSaved()
            } catch (t: Throwable) {
                onError(t.message ?: "Could not save the expense")
            } finally {
                saving = false
            }
        }
    }

    fun delete(onDeleted: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                expenses.delete(expenseId)
                onDeleted()
            } catch (t: Throwable) {
                onError(t.message ?: "Could not delete the expense")
            }
        }
    }
}
