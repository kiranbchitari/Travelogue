package com.travelogue.app.ui.trips.editor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.travelogue.app.data.db.entity.TravelType
import com.travelogue.app.data.db.entity.TripStatus
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripEditorScreen(nav: Nav, tripId: Long, presetStart: LocalDate? = null, presetEnd: LocalDate? = null) {
    val vm = appViewModel(key = "trip-editor-$tripId") { TripEditorViewModel(this, tripId, presetStart, presetEnd) }
    val suggestions by vm.travellerSuggestions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val e = vm.errors

    fun save() = vm.save(
        onSaved = { id, created -> if (created) nav.replaceWithTrip(id) else nav.back() },
        onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
    )

    ScreenScaffold(
        title = if (vm.isNew) "New trip" else "Edit trip",
        subtitle = if (vm.isNew) "Every story starts somewhere" else vm.name.ifBlank { null },
        onBack = nav::back,
        bottomBar = {
            if (!vm.loading && !vm.notFound) {
                Box(
                    Modifier.fillMaxWidth().background(Travel.colors.background)
                        .navigationBarsPadding().imePadding()
                        .padding(horizontal = Space.gutter, vertical = Space.md),
                ) {
                    PrimaryButton(
                        if (vm.isNew) "Create trip" else "Save changes",
                        ::save,
                        Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Check,
                        loading = vm.saving,
                    )
                }
            }
        },
    ) { padding ->
        when {
            vm.loading -> LoadingState(Modifier.padding(padding))
            vm.notFound -> EmptyState(
                Icons.Rounded.Luggage, "Trip not found", "This trip may have been deleted.",
                Modifier.padding(padding), actionLabel = "Go back", onAction = nav::back,
            )
            else -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.gutter)
                    .padding(top = Space.sm, bottom = Space.xxxl),
                verticalArrangement = Arrangement.spacedBy(Space.xxl),
            ) {
                SinglePhotoField(
                    label = "Cover photograph",
                    savedPath = vm.savedCoverPath,
                    pending = vm.pendingCover,
                    onPick = vm::pickCover,
                    onRemove = vm::clearCover,
                    height = 200.dp,
                )

                FormSection("The trip") {
                    FormField("Trip name", vm.name, { vm.name = it }, placeholder = "Monsoon in Kerala", error = e.name, capitalization = KeyboardCapitalization.Words)
                    FormField("Destination", vm.destination, { vm.destination = it }, placeholder = "Alleppey & Munnar", error = e.destination, capitalization = KeyboardCapitalization.Words)
                    FormField("Country", vm.country, { vm.country = it }, placeholder = "India", capitalization = KeyboardCapitalization.Words)
                }

                FormSection("Dates") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        DateField("Start", vm.startDate, vm::setStart, Modifier.weight(1f), error = e.startDate)
                        DateField("End", vm.endDate, vm::setEnd, Modifier.weight(1f), error = e.endDate)
                    }
                    val s = vm.startDate
                    val en = vm.endDate
                    if (s != null && en != null && !en.isBefore(s)) {
                        val days = (en.toEpochDay() - s.toEpochDay()).toInt() + 1
                        Text(
                            "${Fmt.days(days)} · ${Fmt.nights(days - 1)}",
                            style = Travel.type.bodySmall,
                            color = Travel.colors.inkFaint,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }

                FormSection("Status") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        TripStatus.entries.forEach { st ->
                            ChoicePill(st.label, vm.status == st, { vm.pickStatus(st) }, leadingDot = st.color())
                        }
                    }
                }

                FormSection("Travellers") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        TravelType.entries.forEach { t ->
                            ChoicePill(t.label, vm.travelType == t, { vm.travelType = t })
                        }
                    }
                    FormField(
                        "Number of travellers", vm.travellerCount, { v -> vm.travellerCount = v.filter { it.isDigit() }.take(3) },
                        keyboardType = KeyboardType.Number, error = e.travellerCount,
                    )
                    ChipInput(
                        "Travellers' names",
                        vm.travellerNames,
                        vm::setTravellers,
                        placeholder = "Add a name",
                        suggestions = suggestions,
                        onDraftChange = { vm.travellerDraft = it },
                    )
                    Text(
                        "Names are used for “paid by” and splitting expenses.",
                        style = Travel.type.bodySmall,
                        color = Travel.colors.inkFaint,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                FormSection("Budget") {
                    val currencies = (listOf(vm.currency) + CommonCurrencies).distinct()
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        DropdownField(
                            "Currency", currencies, vm.currency,
                            { "$it · ${Fmt.currencySymbol(it)}" }, { vm.currency = it },
                            Modifier.weight(0.42f),
                        )
                        AmountField("Overall budget", vm.budget, { vm.budget = it }, vm.currency, Modifier.weight(0.58f), error = e.budget, placeholder = "Optional")
                    }
                }

                FormSection("The story") {
                    FormField("Description", vm.description, { vm.description = it }, placeholder = "What is this trip about?", singleLine = false, minLines = 3)
                    FormField("General notes", vm.notes, { vm.notes = it }, placeholder = "Visa, packing, reminders…", singleLine = false, minLines = 3)
                }
            }
        }
    }
}
