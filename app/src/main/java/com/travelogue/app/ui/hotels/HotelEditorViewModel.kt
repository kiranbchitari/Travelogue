package com.travelogue.app.ui.hotels

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.util.toAmountOrNull
import com.travelogue.app.data.db.entity.*
import com.travelogue.app.data.repository.ExpenseRepository
import com.travelogue.app.data.repository.JournalRepository
import com.travelogue.app.data.repository.PhotoRepository
import com.travelogue.app.data.repository.TripRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

internal val AmenitySuggestions = listOf("Wi-Fi", "Breakfast", "Pool", "Parking", "AC", "Gym", "Spa", "Room service", "Airport shuttle", "Kitchen")

internal fun amountText(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

class HotelEditorViewModel(
    private val trips: TripRepository,
    private val journal: JournalRepository,
    private val photos: PhotoRepository,
    private val expenses: ExpenseRepository,
    private val tripId: Long,
    private val hotelId: Long,
) : ViewModel() {
    val isEdit = hotelId != 0L
    var loaded by mutableStateOf(false); private set
    var trip by mutableStateOf<TripEntity?>(null); private set

    var name by mutableStateOf("")
    var address by mutableStateOf("")
    var checkIn by mutableStateOf(LocalDate.now()); private set
    var checkOut by mutableStateOf(LocalDate.now().plusDays(1)); private set
    var roomType by mutableStateOf("")
    var bookingReference by mutableStateOf("")
    var bookingPlatform by mutableStateOf("")
    var phone by mutableStateOf("")
    var pricePerNight by mutableStateOf(""); private set
    var totalPrice by mutableStateOf(""); private set
    var currency by mutableStateOf("INR")
    var paymentStatus by mutableStateOf(PaymentStatus.PAID)
    var amenities by mutableStateOf<List<String>>(emptyList())

    var overall by mutableStateOf(0)
    var cleanliness by mutableStateOf(0)
    var locationRating by mutableStateOf(0)
    var service by mutableStateOf(0)
    var value by mutableStateOf(0)
    var review by mutableStateOf("")
    var pros by mutableStateOf("")
    var cons by mutableStateOf("")
    var wouldStayAgain by mutableStateOf<Boolean?>(null)

    var pendingPhotos by mutableStateOf<List<Uri>>(emptyList()); private set
    var addToExpenses by mutableStateOf(true)

    var nameError by mutableStateOf<String?>(null); private set
    var dateError by mutableStateOf<String?>(null); private set
    var priceError by mutableStateOf<String?>(null); private set
    var totalError by mutableStateOf<String?>(null); private set
    var saving by mutableStateOf(false); private set

    /** Once the user types a total, stop auto-calculating it. */
    private var totalEdited = false
    private var original: HotelEntity? = null

    val savedPhotos: StateFlow<List<TripPhotoEntity>> =
        (if (isEdit) photos.observeFor(PhotoOwner.HOTEL, hotelId) else flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nights: Int get() = (checkOut.toEpochDay() - checkIn.toEpochDay()).toInt().coerceAtLeast(0)

    init {
        viewModelScope.launch {
            val t = trips.get(tripId)
            trip = t
            if (t != null) {
                currency = t.currency
                checkIn = t.startDate
                checkOut = if (t.endDate.isAfter(t.startDate)) t.endDate else t.startDate.plusDays(1)
            }
            if (isEdit) journal.getHotel(hotelId)?.let { h ->
                original = h
                name = h.name; address = h.address; checkIn = h.checkIn; checkOut = h.checkOut
                roomType = h.roomType; bookingReference = h.bookingReference; bookingPlatform = h.bookingPlatform
                phone = h.phone; currency = h.currency; paymentStatus = h.paymentStatus; amenities = h.amenityList
                pricePerNight = if (h.pricePerNight > 0) amountText(h.pricePerNight) else ""
                totalPrice = if (h.totalPrice > 0) amountText(h.totalPrice) else ""
                totalEdited = h.totalPrice > 0 && Math.abs(h.totalPrice - h.pricePerNight * h.nights) > 0.005
                val r = h.review
                overall = r.overallRating; cleanliness = r.cleanlinessRating; locationRating = r.locationRating
                service = r.serviceRating; value = r.valueRating; review = r.review; pros = r.pros; cons = r.cons
                wouldStayAgain = r.wouldStayAgain
            }
            loaded = true
        }
    }

    fun updateCheckIn(d: LocalDate) {
        val keepNights = nights.coerceAtLeast(1)
        checkIn = d
        if (!checkOut.isAfter(d)) checkOut = d.plusDays(keepNights.toLong())
        recalcTotal()
    }

    fun updateCheckOut(d: LocalDate) { checkOut = d; recalcTotal() }

    fun updatePrice(s: String) { pricePerNight = s; recalcTotal() }

    fun updateTotal(s: String) {
        totalPrice = s
        totalEdited = s.isNotBlank()
        if (!totalEdited) recalcTotal()
    }

    private fun recalcTotal() {
        if (totalEdited) return
        val p = pricePerNight.toAmountOrNull()
        totalPrice = if (p != null && p > 0 && nights > 0) amountText(p * nights) else ""
    }

    fun addPhotos(uris: List<Uri>) { pendingPhotos = (pendingPhotos + uris).distinct() }
    fun removePending(uri: Uri) { pendingPhotos = pendingPhotos - uri }
    fun deleteSaved(photo: TripPhotoEntity) { viewModelScope.launch { photos.delete(photo.id) } }

    fun save(onError: (String) -> Unit, onDone: () -> Unit) {
        val price = pricePerNight.toAmountOrNull()
        val total = totalPrice.toAmountOrNull()
        nameError = if (name.isBlank()) "Enter the hotel name" else null
        dateError = if (checkOut.isBefore(checkIn)) "Check-out can't be before check-in" else null
        priceError = if (pricePerNight.isNotBlank() && (price == null || price < 0)) "Enter a valid amount" else null
        totalError = if (totalPrice.isNotBlank() && (total == null || total < 0)) "Enter a valid amount" else null
        if (listOf(nameError, dateError, priceError, totalError).any { it != null }) return
        saving = true
        viewModelScope.launch {
            try {
                val base = original ?: HotelEntity(tripId = tripId, name = "", checkIn = checkIn, checkOut = checkOut, currency = currency)
                val hotel = base.copy(
                    name = name.trim(), address = address.trim(), checkIn = checkIn, checkOut = checkOut,
                    roomType = roomType.trim(), bookingReference = bookingReference.trim(), bookingPlatform = bookingPlatform.trim(),
                    phone = phone.trim(), pricePerNight = price ?: 0.0, totalPrice = total ?: 0.0, currency = currency,
                    paymentStatus = paymentStatus, amenities = amenities.joinToString(", "),
                    review = HotelReview(
                        overallRating = overall, cleanlinessRating = cleanliness, locationRating = locationRating,
                        serviceRating = service, valueRating = value, review = review.trim(), pros = pros.trim(),
                        cons = cons.trim(), wouldStayAgain = wouldStayAgain,
                    ),
                )
                journal.saveHotel(hotel, pendingPhotos)
                if (!isEdit && addToExpenses && (total ?: 0.0) > 0.0) {
                    val category = expenses.getCategories().firstOrNull { it.iconKey == "hotel" }
                    expenses.save(
                        ExpenseEntity(
                            tripId = tripId,
                            title = "Stay at ${hotel.name}",
                            amount = total ?: 0.0,
                            currency = currency,
                            categoryId = category?.id,
                            date = checkIn,
                            location = hotel.address,
                            paymentMethod = PaymentMethod.CARD,
                            description = listOf(hotel.roomType, "${hotel.nights} nights").filter { it.isNotBlank() }.joinToString(" · "),
                        ),
                        emptyMap(),
                    )
                }
                pendingPhotos = emptyList()
                onDone()
            } catch (e: Exception) {
                onError(e.message ?: "Could not save the stay")
            } finally {
                saving = false
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            journal.deleteHotel(hotelId)
            onDone()
        }
    }
}
