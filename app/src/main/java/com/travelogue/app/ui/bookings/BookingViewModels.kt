package com.travelogue.app.ui.bookings

import com.travelogue.app.ui.common.read
import com.travelogue.app.ui.common.DraftSlot
import com.travelogue.app.ui.common.DraftHolder
import androidx.core.os.bundleOf
import android.os.Bundle
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.storage.MediaStorage
import com.travelogue.app.core.util.toAmountOrNull
import com.travelogue.app.data.db.entity.*
import com.travelogue.app.data.repository.ExpenseRepository
import com.travelogue.app.data.repository.JournalRepository
import com.travelogue.app.data.repository.TripRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// ---------------------------------------------------------------------------------------------
// Tab
// ---------------------------------------------------------------------------------------------

data class BookingItem(val booking: BookingEntity, val attachments: Int)

class TripBookingsViewModel(journal: JournalRepository, trips: TripRepository, tripId: Long) : ViewModel() {
    val bookings: StateFlow<List<BookingItem>?> = combine(journal.observeBookings(tripId), trips.observeDocuments(tripId)) { bookings, docs ->
        val counts = docs.filter { it.ownerType == DocumentOwner.BOOKING }.groupingBy { it.ownerId }.eachCount()
        bookings
            .sortedWith(compareBy<BookingEntity, LocalDateTime?>(nullsLast()) { it.departure }.thenBy { it.createdAt })
            .map { BookingItem(it, counts[it.id] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

// ---------------------------------------------------------------------------------------------
// Editor
// ---------------------------------------------------------------------------------------------

data class BookingForm(
    val title: String = "",
    val type: BookingType = BookingType.FLIGHT,
    val provider: String = "",
    val reference: String = "",
    val from: String = "",
    val to: String = "",
    val departureDate: LocalDate? = null,
    val departureTime: LocalTime? = null,
    val arrivalDate: LocalDate? = null,
    val arrivalTime: LocalTime? = null,
    val amount: String = "",
    val currency: String = "INR",
    val paymentStatus: PaymentStatus = PaymentStatus.PAID,
    val notes: String = "",
) : java.io.Serializable

data class BookingErrors(
    val title: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val arrival: String? = null,
) {
    val any: Boolean get() = listOfNotNull(title, amount, currency, arrival).isNotEmpty()
}

private fun dateTime(d: LocalDate?, t: LocalTime?): LocalDateTime? = d?.atTime(t ?: LocalTime.MIDNIGHT)

fun BookingForm.validate(): BookingErrors {
    val amt = amount.trim()
    val dep = dateTime(departureDate, departureTime)
    val arr = dateTime(arrivalDate ?: departureDate.takeIf { arrivalTime != null }, arrivalTime)
    return BookingErrors(
        title = if (title.isBlank()) "Give the booking a title" else null,
        amount = when {
            amt.isEmpty() -> null
            amt.toAmountOrNull() == null -> "Enter a valid amount"
            amt.toAmountOrNull()!! < 0 -> "Amount can't be negative"
            else -> null
        },
        currency = if (!currency.matches(Regex("[A-Za-z]{3}"))) "Use a 3-letter currency code" else null,
        arrival = when {
            arrivalTime != null && arr == null -> "Pick a date for the arrival time"
            dep != null && arr != null && arr.isBefore(dep) -> "Arrival must be after departure"
            else -> null
        },
    )
}

class BookingEditorViewModel(
    private val journal: JournalRepository,
    private val trips: TripRepository,
    private val expenses: ExpenseRepository,
    private val media: MediaStorage,
    val tripId: Long,
    val bookingId: Long,
) : ViewModel(), DraftHolder {
    val isNew = bookingId == 0L
    var form by mutableStateOf(BookingForm())
        private set
    var tripCurrency by mutableStateOf("INR")
        private set
    var loaded by mutableStateOf(false)
        private set
    var showErrors by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set

    /** null = follow the default (on when an amount is entered). */
    private var addToExpensesChoice by mutableStateOf<Boolean?>(null)
    val addToExpenses: Boolean get() = addToExpensesChoice ?: ((form.amount.toAmountOrNull() ?: 0.0) > 0.0)
    fun setAddToExpenses(v: Boolean) { addToExpensesChoice = v }

    val pending = mutableStateListOf<Uri>()
    private val pendingNames = HashMap<Uri, String>()
    private var original: BookingEntity? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** Saved attachments removed in this edit. They are only deleted for real when the edit is saved. */
    private val removedSaved = MutableStateFlow<Set<Long>>(emptySet())

    val attachments: StateFlow<List<DocumentEntity>> =
        (if (isNew) flowOf(emptyList()) else trips.observeDocumentsFor(DocumentOwner.BOOKING, bookingId))
            .combine(removedSaved) { list, gone -> list.filter { it.id !in gone } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val draft = DraftSlot(::applyDraft)
    override fun restoreDraft(draft: Bundle) = this.draft.offer(draft)
    override fun saveDraft(): Bundle = if (!draft.ready || finished) Bundle() else
        bundleOf("form" to form, "addToExpenses" to addToExpensesChoice, "removedSaved" to HashSet(removedSaved.value))

    private fun applyDraft(b: Bundle) {
        form = b.read("form") ?: form
        addToExpensesChoice = b.read("addToExpenses")
        removedSaved.value = b.read<HashSet<Long>>("removedSaved") ?: removedSaved.value
    }

    init {
        viewModelScope.launch {
            val trip = trips.get(tripId)
            tripCurrency = trip?.currency ?: "INR"
            if (isNew) {
                form = form.copy(currency = tripCurrency, departureDate = trip?.startDate)
            } else {
                journal.getBooking(bookingId)?.let { b ->
                    original = b
                    form = BookingForm(
                        title = b.title, type = b.type, provider = b.provider, reference = b.reference,
                        from = b.fromPlace, to = b.toPlace,
                        departureDate = b.departure?.toLocalDate(), departureTime = b.departure?.toLocalTime()?.takeIf { it != LocalTime.MIDNIGHT },
                        arrivalDate = b.arrival?.toLocalDate(), arrivalTime = b.arrival?.toLocalTime()?.takeIf { it != LocalTime.MIDNIGHT },
                        amount = if (b.amount == 0.0) "" else com.travelogue.app.ui.expenses.plainNumber(b.amount),
                        currency = b.currency, paymentStatus = b.paymentStatus, notes = b.notes,
                    )
                }
            }
            loaded = true
            draft.ready()
        }
    }

    val errors: BookingErrors get() = if (showErrors) form.validate() else BookingErrors()

    fun update(transform: BookingForm.() -> BookingForm) { form = form.transform() }

    fun addAttachments(uris: List<Uri>) {
        uris.forEach { if (it !in pending) pending.add(it) }
    }

    fun removePending(uri: Uri) { pending.remove(uri) }

    fun pendingName(uri: Uri): String = pendingNames.getOrPut(uri) { media.displayName(uri) ?: "Attachment" }

    fun removeSaved(doc: DocumentEntity) { removedSaved.value += doc.id }

    fun fileFor(doc: DocumentEntity): File = media.file(doc.relativePath)

    fun save() {
        if (saving) return
        showErrors = true
        val f = form
        if (f.validate().any) {
            _messages.tryEmit("Please fix the highlighted fields")
            return
        }
        saving = true
        viewModelScope.launch {
            try {
                val departure = dateTime(f.departureDate, f.departureTime)
                val amount = f.amount.toAmountOrNull() ?: 0.0
                val base = original ?: BookingEntity(tripId = tripId, title = "", currency = f.currency)
                val row = base.copy(
                    title = f.title.trim(),
                    type = f.type,
                    provider = f.provider.trim(),
                    reference = f.reference.trim(),
                    fromPlace = f.from.trim(),
                    toPlace = f.to.trim(),
                    departure = departure,
                    arrival = dateTime(f.arrivalDate ?: f.departureDate.takeIf { f.arrivalTime != null }, f.arrivalTime),
                    amount = amount,
                    currency = f.currency.uppercase(),
                    paymentStatus = f.paymentStatus,
                    notes = f.notes.trim(),
                )
                val alsoExpense = isNew && addToExpenses && amount > 0
                journal.saveBooking(row, pending.toList())
                removedSaved.value.forEach { trips.deleteDocument(it) }
                pending.clear()
                if (alsoExpense) recordExpense(row, departure)
                finished = true
            } catch (e: Exception) {
                saving = false
                _messages.tryEmit(e.message ?: "Couldn't save this booking")
            }
        }
    }

    private suspend fun recordExpense(b: BookingEntity, departure: LocalDateTime?) {
        val key = when (b.type) {
            BookingType.FLIGHT, BookingType.TRAIN, BookingType.BUS, BookingType.FERRY -> "transport"
            BookingType.HOTEL -> "hotel"
            else -> "ticket"
        }
        val categories = expenses.getCategories()
        val category = categories.firstOrNull { it.iconKey == key } ?: categories.firstOrNull { it.iconKey == "other" }
        val route = listOf(b.fromPlace, b.toPlace).filter { it.isNotBlank() }.joinToString(" → ")
        expenses.save(
            ExpenseEntity(
                tripId = b.tripId,
                title = b.title,
                amount = b.amount,
                currency = b.currency,
                categoryId = category?.id,
                date = departure?.toLocalDate() ?: LocalDate.now(),
                time = departure?.toLocalTime()?.takeIf { it != LocalTime.MIDNIGHT },
                location = route,
                paymentMethod = PaymentMethod.CARD,
                description = buildString {
                    append("${b.type.label} booking")
                    if (b.provider.isNotBlank()) append(" · ${b.provider}")
                    if (b.reference.isNotBlank()) append(" · Ref ${b.reference}")
                },
            ),
            emptyMap(),
        )
    }

    fun delete() {
        viewModelScope.launch {
            journal.deleteBooking(bookingId)
            finished = true
        }
    }
}
