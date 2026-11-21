package com.travelogue.app.ui.itinerary

import com.travelogue.app.ui.common.read
import com.travelogue.app.ui.common.DraftSlot
import com.travelogue.app.ui.common.DraftHolder
import androidx.core.os.bundleOf
import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Place
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.core.util.toAmountOrNull
import com.travelogue.app.data.db.entity.ActivityStatus
import com.travelogue.app.data.db.entity.ItineraryActivityEntity
import com.travelogue.app.data.db.entity.ItineraryDayEntity
import com.travelogue.app.data.db.entity.TripEntity
import com.travelogue.app.data.repository.JournalRepository
import com.travelogue.app.data.repository.TripRepository
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class ActivityEditorViewModel(
    private val trips: TripRepository,
    private val journal: JournalRepository,
    private val tripId: Long,
    private val activityId: Long,
    initialDayId: Long,
) : ViewModel(), DraftHolder {
    val isEdit = activityId != 0L
    var loaded by mutableStateOf(false); private set
    var trip by mutableStateOf<TripEntity?>(null); private set
    var days by mutableStateOf<List<ItineraryDayEntity>>(emptyList()); private set

    var name by mutableStateOf("")
    var dayId by mutableStateOf(initialDayId)
    var location by mutableStateOf("")
    var startTime by mutableStateOf<LocalTime?>(null)
    var endTime by mutableStateOf<LocalTime?>(null)
    var description by mutableStateOf("")
    var estimated by mutableStateOf("")
    var actual by mutableStateOf("")
    var status by mutableStateOf(ActivityStatus.PLANNED)
    var notes by mutableStateOf("")
    var savedPhoto by mutableStateOf<String?>(null); private set
    var pendingPhoto by mutableStateOf<Uri?>(null); private set
    private var removePhoto = false

    var nameError by mutableStateOf<String?>(null); private set
    var timeError by mutableStateOf<String?>(null); private set
    var estimatedError by mutableStateOf<String?>(null); private set
    var actualError by mutableStateOf<String?>(null); private set
    var dayError by mutableStateOf<String?>(null); private set
    var saving by mutableStateOf(false); private set

    private var original: ItineraryActivityEntity? = null

    private val draft = DraftSlot(::applyDraft)
    override fun restoreDraft(draft: Bundle) = this.draft.offer(draft)
    override fun saveDraft(): Bundle = if (!draft.ready) Bundle() else bundleOf(
        "name" to name, "dayId" to dayId, "location" to location, "startTime" to startTime, "endTime" to endTime,
        "description" to description, "estimated" to estimated, "actual" to actual, "status" to status, "notes" to notes,
        "removePhoto" to removePhoto,
    )

    private fun applyDraft(b: Bundle) {
        name = b.read("name") ?: name
        // Only keep the chosen day if it still exists.
        b.read<Long>("dayId")?.takeIf { id -> days.any { it.id == id } }?.let { dayId = it }
        location = b.read("location") ?: location; startTime = b.read("startTime"); endTime = b.read("endTime")
        description = b.read("description") ?: description; estimated = b.read("estimated") ?: estimated; actual = b.read("actual") ?: actual
        status = b.read("status") ?: status; notes = b.read("notes") ?: notes
        if (b.getBoolean("removePhoto")) { removePhoto = true; savedPhoto = null }
    }

    init {
        viewModelScope.launch {
            trip = trips.get(tripId)
            days = journal.getDays(tripId).sortedBy { it.date }
            if (isEdit) {
                journal.getActivity(activityId)?.let { a ->
                    original = a
                    name = a.name; dayId = a.dayId; location = a.location
                    startTime = a.startTime; endTime = a.endTime; description = a.description
                    estimated = a.estimatedCost?.let(::amountText).orEmpty()
                    actual = a.actualCost?.let(::amountText).orEmpty()
                    status = a.status; notes = a.notes; savedPhoto = a.photoPath
                }
            }
            if (days.none { it.id == dayId }) {
                dayId = (days.firstOrNull { it.date == LocalDate.now() } ?: days.firstOrNull())?.id ?: 0L
            }
            loaded = true
            draft.ready()
        }
    }

    // removePhoto is left as it is: the repository prefers a new photo when one is set, and if that
    // pick is cleared again the earlier removal still has to apply.
    fun pickPhoto(uri: Uri) { pendingPhoto = uri }
    fun clearPhoto() { if (pendingPhoto != null) pendingPhoto = null else { savedPhoto = null; removePhoto = true } }

    fun save(onError: (String) -> Unit, onDone: () -> Unit) {
        // Stays set after a successful save so a second tap during the exit transition is ignored.
        if (saving) return
        nameError = if (name.isBlank()) "Give the activity a name" else null
        dayError = if (dayId == 0L) "Choose a day" else null
        timeError = if (startTime != null && endTime != null && !endTime!!.isAfter(startTime)) "End time must be after the start time" else null
        val est = estimated.toAmountOrNull()
        val act = actual.toAmountOrNull()
        estimatedError = if (estimated.isNotBlank() && (est == null || est < 0)) "Enter a valid amount" else null
        actualError = if (actual.isNotBlank() && (act == null || act < 0)) "Enter a valid amount" else null
        if (listOf(nameError, dayError, timeError, estimatedError, actualError).any { it != null }) return
        saving = true
        viewModelScope.launch {
            try {
                val base = original ?: ItineraryActivityEntity(tripId = tripId, dayId = dayId, name = "")
                journal.saveActivity(
                    base.copy(
                        dayId = dayId, name = name.trim(), location = location.trim(),
                        startTime = startTime, endTime = endTime, description = description.trim(),
                        estimatedCost = est, actualCost = act, status = status, notes = notes.trim(),
                    ),
                    newPhoto = pendingPhoto,
                    removePhoto = removePhoto,
                )
                onDone()
            } catch (e: Exception) {
                saving = false
                onError(e.message ?: "Could not save the activity")
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            journal.deleteActivity(activityId)
            onDone()
        }
    }
}

internal fun amountText(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

@Composable
fun ActivityEditorScreen(nav: Nav, tripId: Long, activityId: Long, dayId: Long) {
    val vm = appViewModel(key = "activity-$tripId-$activityId-$dayId") {
        ActivityEditorViewModel(trips, journal, tripId, activityId, dayId)
    }
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val currency = vm.trip?.currency ?: "INR"

    ScreenScaffold(
        title = if (vm.isEdit) "Edit activity" else "New activity",
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
            FormSection("Activity") {
                FormField("Name", vm.name, { vm.name = it }, placeholder = "e.g. Sunrise at Tiger Hill", error = vm.nameError)
                DropdownField(
                    label = "Day",
                    options = vm.days,
                    selected = vm.days.firstOrNull { it.id == vm.dayId },
                    optionLabel = { d -> "Day ${vm.days.indexOf(d) + 1} · ${Fmt.weekdayShort(d.date)}" + if (d.title.isNotBlank()) " · ${d.title}" else "" },
                    onSelect = { vm.dayId = it.id },
                    error = vm.dayError,
                )
                FormField("Location", vm.location, { vm.location = it }, placeholder = "Where is it?", leadingIcon = Icons.Rounded.Place)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    TimeField("Start", vm.startTime, { vm.startTime = it }, Modifier.weight(1f), onClear = { vm.startTime = null })
                    TimeField("End", vm.endTime, { vm.endTime = it }, Modifier.weight(1f), onClear = { vm.endTime = null })
                }
                vm.timeError?.let { androidx.compose.material3.Text(it, style = Travel.type.bodySmall, color = Travel.colors.danger, modifier = Modifier.padding(start = 4.dp)) }
                FormField("Description", vm.description, { vm.description = it }, placeholder = "What's the plan?", singleLine = false, minLines = 3)
            }

            FormSection("Status") {
                val statusColors = ActivityStatus.entries.associateWith { it.color() }
                ChoiceRow(
                    options = ActivityStatus.entries,
                    selected = vm.status,
                    label = { it.label },
                    onSelect = { vm.status = it },
                    contentPadding = PaddingValues(0.dp),
                    dot = { statusColors[it] },
                )
            }

            FormSection("Cost · $currency") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    AmountField("Estimated", vm.estimated, { vm.estimated = it }, currency, Modifier.weight(1f), error = vm.estimatedError)
                    AmountField("Actual", vm.actual, { vm.actual = it }, currency, Modifier.weight(1f), error = vm.actualError)
                }
            }

            FormSection("Memory") {
                SinglePhotoField(
                    label = "Photo",
                    savedPath = vm.savedPhoto,
                    pending = vm.pendingPhoto,
                    onPick = vm::pickPhoto,
                    onRemove = vm::clearPhoto,
                )
                FormField("Notes", vm.notes, { vm.notes = it }, placeholder = "Tips, booking details, thoughts…", singleLine = false, minLines = 3)
            }

            PrimaryButton(
                if (vm.isEdit) "Save changes" else "Add activity",
                {
                    vm.save(
                        onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
                        onDone = nav::back,
                    )
                },
                Modifier.fillMaxWidth(),
                loading = vm.saving,
            )
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete activity?",
            message = "This activity and its photo will be removed.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(nav::back) },
            onDismiss = { confirmDelete = false },
        )
    }
}
