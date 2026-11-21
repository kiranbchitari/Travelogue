package com.travelogue.app.ui.hotels

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.CommonCurrencies
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.core.util.toAmountOrNull
import com.travelogue.app.data.db.entity.PaymentStatus
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav

@Composable
fun HotelEditorScreen(nav: Nav, tripId: Long, hotelId: Long) {
    val vm = appViewModel(key = "hotel-editor-$tripId-$hotelId") {
        HotelEditorViewModel(trips, journal, photos, expenses, tripId, hotelId)
    }
    val saved by vm.savedPhotos.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = if (vm.isEdit) "Edit stay" else "New stay",
        subtitle = vm.trip?.name,
        onBack = nav::back,
        actions = {
            if (vm.isEdit) CircleIconButton(Icons.Rounded.DeleteOutline, { confirmDelete = true }, contentDescription = "Delete", tint = Travel.colors.danger)
        },
    ) { padding ->
        if (!vm.loaded) {
            LoadingState(Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Space.gutter, vertical = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.xxl),
        ) {
            FormSection("The hotel") {
                FormField("Hotel name", vm.name, { vm.name = it }, placeholder = "e.g. The Elgin, Darjeeling", error = vm.nameError,
                    leadingIcon = Icons.Rounded.Hotel, capitalization = KeyboardCapitalization.Words)
                FormField("Address", vm.address, { vm.address = it }, placeholder = "Street, area, city", leadingIcon = Icons.Rounded.Place, singleLine = false)
                FormField("Contact number", vm.phone, { vm.phone = it }, placeholder = "+91 …", keyboardType = KeyboardType.Phone, leadingIcon = Icons.Rounded.Call)
            }

            FormSection("Your stay") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    DateField("Check-in", vm.checkIn, vm::updateCheckIn, Modifier.weight(1f))
                    DateField("Check-out", vm.checkOut, vm::updateCheckOut, Modifier.weight(1f), error = vm.dateError)
                }
                Text(
                    if (vm.nights == 0) "Same-day stay" else Fmt.nights(vm.nights),
                    style = Travel.type.label,
                    color = Travel.colors.accent,
                    modifier = Modifier.padding(start = 4.dp),
                )
                FormField("Room type", vm.roomType, { vm.roomType = it }, placeholder = "e.g. Deluxe double, garden view")
            }

            FormSection("Booking") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    FormField("Reference", vm.bookingReference, { vm.bookingReference = it }, Modifier.weight(1f), placeholder = "Confirmation no.",
                        capitalization = KeyboardCapitalization.Characters)
                    FormField("Platform", vm.bookingPlatform, { vm.bookingPlatform = it }, Modifier.weight(1f), placeholder = "Direct, app…")
                }
                DropdownField(
                    "Currency",
                    (listOf(vm.currency) + CommonCurrencies).distinct(),
                    vm.currency,
                    { "$it · ${Fmt.currencySymbol(it)}" },
                    { vm.currency = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    AmountField("Price per night", vm.pricePerNight, vm::updatePrice, vm.currency, Modifier.weight(1f), error = vm.priceError)
                    AmountField("Total price", vm.totalPrice, vm::updateTotal, vm.currency, Modifier.weight(1f), error = vm.totalError)
                }
                DropdownField("Payment status", PaymentStatus.entries, vm.paymentStatus, { it.label }, { vm.paymentStatus = it })
                if (!vm.isEdit) {
                    val total = vm.totalPrice.toAmountOrNull() ?: 0.0
                    SwitchRow(
                        "Also add to expenses",
                        vm.addToExpenses && total > 0,
                        { vm.addToExpenses = it },
                        description = if (total > 0) "Records ${Fmt.money(total, vm.currency)} under Hotel / Accommodation"
                        else "Enter a total price to record it as an expense",
                    )
                }
            }

            FormSection("Amenities") {
                ChipInput("Add amenity", vm.amenities, { vm.amenities = it }, placeholder = "e.g. Rooftop bar", suggestions = AmenitySuggestions)
            }

            FormSection("Photos") {
                PhotoEditorStrip(
                    saved = saved,
                    pending = vm.pendingPhotos,
                    onAddPhotos = vm::addPhotos,
                    onRemoveSaved = vm::deleteSaved,
                    onRemovePending = vm::removePending,
                )
            }

            FormSection("Your review") {
                RatingInput("Overall", vm.overall, { vm.overall = it })
                RatingInput("Cleanliness", vm.cleanliness, { vm.cleanliness = it }, size = 26.dp)
                RatingInput("Location", vm.locationRating, { vm.locationRating = it }, size = 26.dp)
                RatingInput("Service", vm.service, { vm.service = it }, size = 26.dp)
                RatingInput("Value for money", vm.value, { vm.value = it }, size = 26.dp)
                FormField("Overall review", vm.review, { vm.review = it }, placeholder = "How was the stay?", singleLine = false, minLines = 4)
                FormField("Pros", vm.pros, { vm.pros = it }, placeholder = "One per line", singleLine = false, minLines = 2)
                FormField("Cons", vm.cons, { vm.cons = it }, placeholder = "One per line", singleLine = false, minLines = 2)
                Column {
                    Text("Would you stay again?", style = Travel.type.label, color = Travel.colors.inkSoft, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        ChoicePill("Yes", vm.wouldStayAgain == true, { vm.wouldStayAgain = true }, leadingDot = Travel.colors.success)
                        ChoicePill("No", vm.wouldStayAgain == false, { vm.wouldStayAgain = false }, leadingDot = Travel.colors.danger)
                        ChoicePill("Not sure", vm.wouldStayAgain == null, { vm.wouldStayAgain = null })
                    }
                }
            }

            PrimaryButton(
                if (vm.isEdit) "Save changes" else "Add stay",
                { vm.save(onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }, onDone = nav::back) },
                Modifier.fillMaxWidth(),
                loading = vm.saving,
            )
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this stay?",
            message = "The hotel, its review and all of its photos will be removed. Expenses you recorded stay in place.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(nav::back) },
            onDismiss = { confirmDelete = false },
        )
    }
}
