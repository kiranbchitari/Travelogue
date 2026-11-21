package com.travelogue.app.ui.places

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.data.db.entity.PlaceCategory
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav

@Composable
fun PlaceEditorScreen(nav: Nav, tripId: Long, placeId: Long) {
    val vm = appViewModel(key = "place-editor-$tripId-$placeId") { PlaceEditorViewModel(journal, photos, trips, tripId, placeId) }
    val context = LocalContext.current
    val saved by vm.savedPhotos.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(vm.finished) { if (vm.finished) nav.back() }

    ScreenScaffold(
        title = if (vm.isNew) "New place" else "Edit place",
        subtitle = if (vm.isNew) "Add it to your travel journal" else null,
        onBack = nav::back,
        actions = {
            if (!vm.isNew) CircleIconButton(Icons.Rounded.DeleteOutline, { confirmDelete = true }, contentDescription = "Delete place", tint = Travel.colors.danger)
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
            FormSection("The place") {
                FormField("Name", f.name, { v -> vm.update { copy(name = v) } }, placeholder = "e.g. Palolem Beach", error = e.name, capitalization = KeyboardCapitalization.Words)
                Column {
                    Text("Category", style = Travel.type.label, color = Travel.colors.inkSoft, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        items(PlaceCategory.entries) { cat ->
                            ChoicePill(cat.label, f.category == cat, { vm.update { copy(category = cat) } }, icon = cat.icon)
                        }
                    }
                }
                DateField("Visit date", f.visitDate, { d -> vm.update { copy(visitDate = d) } }, placeholder = "When did you go?", onClear = { vm.update { copy(visitDate = null) } })
                RatingInput("Your rating", f.rating, { r -> vm.update { copy(rating = r) } })
            }

            FormSection("Photos") {
                PhotoEditorStrip(
                    saved = saved,
                    pending = vm.pending,
                    onAddPhotos = vm::addPhotos,
                    onRemoveSaved = vm::removeSaved,
                    onRemovePending = vm::removePending,
                )
            }

            FormSection("Story") {
                FormField("Description", f.description, { v -> vm.update { copy(description = v) } }, placeholder = "What was it like?", singleLine = false, minLines = 4)
                FormField("Notes", f.notes, { v -> vm.update { copy(notes = v) } }, placeholder = "Tips, best time to visit, what to order…", singleLine = false, minLines = 2)
            }

            FormSection("Location") {
                FormField("Address", f.address, { v -> vm.update { copy(address = v) } }, placeholder = "Street, area, city", leadingIcon = Icons.Rounded.Place, singleLine = false)
                FormField(
                    "Google Maps link (optional)", f.mapsUrl, { v -> vm.update { copy(mapsUrl = v.trim()) } },
                    placeholder = "https://maps.app.goo.gl/…", error = e.mapsUrl, keyboardType = KeyboardType.Uri,
                    capitalization = KeyboardCapitalization.None, leadingIcon = Icons.Rounded.Link,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    FormField(
                        "Latitude", f.latitude, { v -> vm.update { copy(latitude = v.filter { it.isDigit() || it == '.' || it == '-' }) } },
                        Modifier.weight(1f), placeholder = "15.0100", error = e.latitude, keyboardType = KeyboardType.Decimal,
                    )
                    FormField(
                        "Longitude", f.longitude, { v -> vm.update { copy(longitude = v.filter { it.isDigit() || it == '.' || it == '-' }) } },
                        Modifier.weight(1f), placeholder = "74.0230", error = e.longitude, keyboardType = KeyboardType.Decimal,
                    )
                }
                Text(
                    "Saved links and coordinates open in any maps app installed on your phone. Nothing is looked up online.",
                    style = Travel.type.bodySmall,
                    color = Travel.colors.inkFaint,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            PrimaryButton(
                if (vm.isNew) "Save place" else "Save changes",
                vm::save,
                Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Check,
                loading = vm.saving,
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this place?",
            message = "The place and its photos will be removed from this trip. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = vm::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}
