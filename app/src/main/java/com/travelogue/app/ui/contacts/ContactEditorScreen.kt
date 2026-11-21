package com.travelogue.app.ui.contacts

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Phone
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
import com.travelogue.app.data.db.entity.ContactType
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav

@Composable
fun ContactEditorScreen(nav: Nav, tripId: Long, contactId: Long) {
    val vm = appViewModel(key = "contact-editor-$tripId-$contactId") { ContactEditorViewModel(journal, tripId, contactId) }
    val context = LocalContext.current
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(vm.finished) { if (vm.finished) nav.back() }

    ScreenScaffold(
        title = if (vm.isNew) "New contact" else "Edit contact",
        subtitle = "Saved only inside Travelogue",
        onBack = nav::back,
        actions = {
            if (!vm.isNew) CircleIconButton(Icons.Rounded.DeleteOutline, { confirmDelete = true }, contentDescription = "Delete contact", tint = Travel.colors.danger)
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
            FormSection("Who") {
                FormField("Name", f.name, { v -> vm.update { copy(name = v) } }, placeholder = "e.g. Ramesh (driver)", error = e.name, capitalization = KeyboardCapitalization.Words)
                Column {
                    Text("Type", style = Travel.type.label, color = Travel.colors.inkSoft, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        items(ContactType.entries) { t ->
                            ChoicePill(t.label, f.type == t, { vm.update { copy(type = t) } }, icon = t.icon)
                        }
                    }
                }
            }

            FormSection("Numbers") {
                FormField(
                    "Phone number", f.phone, { v -> vm.update { copy(phone = v) } },
                    placeholder = "+91 98765 43210", error = e.phone, keyboardType = KeyboardType.Phone, leadingIcon = Icons.Rounded.Phone,
                )
                SwitchRow("WhatsApp on the same number", f.sameAsPhone, { v -> vm.update { copy(sameAsPhone = v, whatsapp = if (!v && whatsapp.isBlank()) phone else whatsapp) } })
                if (!f.sameAsPhone) {
                    FormField(
                        "WhatsApp number", f.whatsapp, { v -> vm.update { copy(whatsapp = v) } },
                        placeholder = "Include the country code", error = e.whatsapp, keyboardType = KeyboardType.Phone,
                    )
                }
            }

            FormSection("Where") {
                FormField("Address", f.address, { v -> vm.update { copy(address = v) } }, placeholder = "Optional", singleLine = false, leadingIcon = Icons.Rounded.Place)
                Column {
                    FormField(
                        "Associated hotel or place", f.associatedWith, { v -> vm.update { copy(associatedWith = v) } },
                        placeholder = "e.g. Casa Azul Resort", capitalization = KeyboardCapitalization.Words,
                    )
                    val matching = suggestions.filter { s ->
                        !s.equals(f.associatedWith.trim(), true) && (f.associatedWith.isBlank() || s.contains(f.associatedWith.trim(), true))
                    }.take(8)
                    if (matching.isNotEmpty()) {
                        Spacer(Modifier.height(Space.sm))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            items(matching) { s -> ChoicePill(s, false, { vm.update { copy(associatedWith = s) } }) }
                        }
                    }
                }
            }

            FormSection("Notes") {
                FormField("Description", f.description, { v -> vm.update { copy(description = v) } }, placeholder = "Speaks English, has an AC car…", singleLine = false, minLines = 2)
                FormField("Notes", f.notes, { v -> vm.update { copy(notes = v) } }, placeholder = "Rates agreed, timings…", singleLine = false, minLines = 2)
            }

            PrimaryButton(
                if (vm.isNew) "Save contact" else "Save changes",
                vm::save,
                Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Check,
                loading = vm.saving,
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this contact?",
            message = "It will be removed from this trip. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = vm::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}
