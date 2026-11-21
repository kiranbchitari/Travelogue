package com.travelogue.app.ui.bookings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.CommonCurrencies
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.core.util.Intents
import com.travelogue.app.data.db.entity.BookingType
import com.travelogue.app.data.db.entity.PaymentStatus
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav

@Composable
fun BookingEditorScreen(nav: Nav, tripId: Long, bookingId: Long) {
    val vm = appViewModel(key = "booking-editor-$tripId-$bookingId") {
        BookingEditorViewModel(journal, trips, expenses, media, tripId, bookingId)
    }
    val context = LocalContext.current
    val saved by vm.attachments.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val pickDocs = rememberDocumentPicker(vm::addAttachments)
    val pickImages = rememberImagePicker(true, vm::addAttachments)

    LaunchedEffect(vm) { vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(vm.finished) { if (vm.finished) nav.back() }

    ScreenScaffold(
        title = if (vm.isNew) "New booking" else "Edit booking",
        onBack = nav::back,
        actions = {
            if (!vm.isNew) CircleIconButton(Icons.Rounded.DeleteOutline, { confirmDelete = true }, contentDescription = "Delete booking", tint = Travel.colors.danger)
            CircleIconButton(Icons.Rounded.Check, vm::save, contentDescription = "Save", tint = Travel.colors.onAccent, background = Travel.colors.accent)
        },
    ) { padding ->
        if (!vm.loaded) {
            LoadingState(Modifier.padding(padding))
            return@ScreenScaffold
        }
        val f = vm.form
        val e = vm.errors
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .padding(top = Space.sm, bottom = Space.huge),
            verticalArrangement = Arrangement.spacedBy(Space.xxl),
        ) {
            FormSection("Booking") {
                Column {
                    Text("Type", style = Travel.type.label, color = Travel.colors.inkSoft, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        items(BookingType.entries) { t ->
                            ChoicePill(t.label, f.type == t, { vm.update { copy(type = t) } }, icon = t.icon)
                        }
                    }
                }
                FormField(
                    "Title", f.title, { v -> vm.update { copy(title = v) } },
                    placeholder = when (f.type) {
                        BookingType.FLIGHT -> "e.g. IndiGo 6E 523"
                        BookingType.TRAIN -> "e.g. Konkan Kanya Express"
                        BookingType.HOTEL -> "e.g. Two nights at Casa Azul"
                        else -> "What is this booking?"
                    },
                    error = e.title, capitalization = KeyboardCapitalization.Words,
                )
                FormField("Provider", f.provider, { v -> vm.update { copy(provider = v) } }, placeholder = "Airline, operator, platform…", capitalization = KeyboardCapitalization.Words)
                FormField(
                    if (f.type == BookingType.FLIGHT || f.type == BookingType.TRAIN) "PNR / Booking reference" else "Booking reference",
                    f.reference, { v -> vm.update { copy(reference = v) } },
                    placeholder = "ABC123", capitalization = KeyboardCapitalization.Characters,
                )
            }

            FormSection("Journey") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    FormField("From", f.from, { v -> vm.update { copy(from = v) } }, Modifier.weight(1f), placeholder = "Origin", capitalization = KeyboardCapitalization.Words)
                    FormField("To", f.to, { v -> vm.update { copy(to = v) } }, Modifier.weight(1f), placeholder = "Destination", capitalization = KeyboardCapitalization.Words)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    DateField("Departure", f.departureDate, { d -> vm.update { copy(departureDate = d) } }, Modifier.weight(1.2f), placeholder = "Date", onClear = { vm.update { copy(departureDate = null, departureTime = null) } })
                    TimeField("Time", f.departureTime, { t -> vm.update { copy(departureTime = t) } }, Modifier.weight(1f), onClear = { vm.update { copy(departureTime = null) } })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    DateField("Arrival", f.arrivalDate, { d -> vm.update { copy(arrivalDate = d) } }, Modifier.weight(1.2f), placeholder = "Date", error = e.arrival, onClear = { vm.update { copy(arrivalDate = null, arrivalTime = null) } })
                    TimeField("Time", f.arrivalTime, { t -> vm.update { copy(arrivalTime = t) } }, Modifier.weight(1f), onClear = { vm.update { copy(arrivalTime = null) } })
                }
            }

            FormSection("Payment") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    AmountField("Amount", f.amount, { v -> vm.update { copy(amount = v) } }, f.currency, Modifier.weight(1.4f), error = e.amount)
                    DropdownField(
                        "Currency",
                        (listOf(vm.tripCurrency, f.currency) + CommonCurrencies).distinct(),
                        f.currency,
                        { it },
                        { cur -> vm.update { copy(currency = cur) } },
                        Modifier.weight(1f),
                        error = e.currency,
                    )
                }
                DropdownField("Payment status", PaymentStatus.entries, f.paymentStatus, { it.label }, { s -> vm.update { copy(paymentStatus = s) } })
                if (vm.isNew) {
                    SwitchRow(
                        "Also add to expenses",
                        vm.addToExpenses,
                        vm::setAddToExpenses,
                        description = "Records this amount in the trip's expenses so your totals stay complete.",
                    )
                }
            }

            FormSection("Tickets & confirmations") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    SecondaryButton("PDF or file", pickDocs, Modifier.weight(1f), icon = Icons.Rounded.UploadFile)
                    SecondaryButton("Screenshot", pickImages, Modifier.weight(1f), icon = Icons.Rounded.AddPhotoAlternate)
                }
                if (saved.isEmpty() && vm.pending.isEmpty()) {
                    Text(
                        "Attach e-tickets, boarding passes or confirmation screenshots. They're copied into the app so they work offline.",
                        style = Travel.type.bodySmall,
                        color = Travel.colors.inkFaint,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                saved.forEach { doc ->
                    AttachmentRow(
                        name = doc.fileName,
                        detail = Fmt.bytes(doc.sizeBytes),
                        icon = iconFor(doc.mimeType),
                        onOpen = { Intents.openFile(context, vm.fileFor(doc), doc.mimeType) },
                        onRemove = { vm.removeSaved(doc) },
                    )
                }
                vm.pending.forEach { uri ->
                    val name = remember(uri) { vm.pendingName(uri) }
                    val mime = remember(uri) { context.contentResolver.getType(uri).orEmpty() }
                    AttachmentRow(name = name, detail = "Will be saved", icon = iconFor(mime), onOpen = null, onRemove = { vm.removePending(uri) })
                }
            }

            FormSection("Notes") {
                FormField("Notes", f.notes, { v -> vm.update { copy(notes = v) } }, placeholder = "Seat, coach, terminal, baggage…", singleLine = false, minLines = 3)
            }

            PrimaryButton(
                if (vm.isNew) "Save booking" else "Save changes",
                vm::save,
                Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Check,
                loading = vm.saving,
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this booking?",
            message = "The booking and its attached files will be removed. Any expense you recorded for it stays. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = vm::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun iconFor(mime: String): ImageVector = when {
    mime == "application/pdf" -> Icons.Rounded.PictureAsPdf
    mime.startsWith("image/") -> Icons.Rounded.Image
    else -> Icons.Rounded.Description
}

@Composable
private fun AttachmentRow(name: String, detail: String, icon: ImageVector, onOpen: (() -> Unit)?, onRemove: () -> Unit) {
    val c = Travel.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.md)
            .background(c.card)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, c.accent, size = 38.dp, iconSize = 18.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(name, style = Travel.type.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = Travel.type.bodySmall, color = c.inkFaint)
        }
        if (onOpen != null) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open", tint = c.inkFaint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.sm))
        }
        CircleIconButton(Icons.Rounded.Close, onRemove, contentDescription = "Remove attachment", size = 34.dp, iconSize = 16.dp, background = c.backgroundDeep, tint = c.inkSoft)
    }
}
