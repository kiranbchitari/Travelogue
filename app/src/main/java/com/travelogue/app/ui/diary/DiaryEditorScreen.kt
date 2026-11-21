package com.travelogue.app.ui.diary

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.entity.Mood
import com.travelogue.app.data.db.entity.TripPhotoEntity
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav

@Composable
fun DiaryEditorScreen(nav: Nav, tripId: Long, entryId: Long) {
    val vm = appViewModel(key = "diary-editor-$tripId-$entryId") { DiaryEditorViewModel(trips, diary, photos, tripId, entryId) }
    val context = LocalContext.current
    val savedPhotos by vm.savedPhotos.collectAsStateWithLifecycle()
    val suggestions by vm.tagSuggestions.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(vm.finished) { if (vm.finished) nav.back() }
    LaunchedEffect(vm.message) {
        vm.message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.message = null }
    }
    val tryBack = { if (vm.dirty) confirmDiscard = true else nav.back() }
    BackHandler(enabled = vm.dirty && !vm.finished) { confirmDiscard = true }

    val visibleSaved = savedPhotos.filter { it.id !in vm.removedSaved }

    ScreenScaffold(
        title = if (vm.isNew) "New entry" else "Edit entry",
        subtitle = vm.trip?.name,
        onBack = tryBack,
        actions = {
            if (!vm.isNew) CircleIconButton(Icons.Rounded.DeleteOutline, { confirmDelete = true }, contentDescription = "Delete entry", tint = Travel.colors.danger)
        },
        bottomBar = {
            Box(
                Modifier.fillMaxWidth().background(Travel.colors.background).navigationBarsPadding().imePadding()
                    .padding(horizontal = Space.gutter, vertical = Space.md),
            ) {
                PrimaryButton(
                    if (vm.isNew) "Save memory" else "Save changes",
                    vm::save,
                    Modifier.fillMaxWidth(),
                    enabled = !vm.loading,
                    loading = vm.saving,
                )
            }
        },
    ) { padding ->
        if (vm.loading) {
            LoadingState(Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter).padding(top = Space.sm, bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            TitleField(vm.title, vm::updateTitle, vm.titleError)

            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                DateField("Date", vm.date, vm::updateDate, Modifier.weight(1.2f), error = vm.dateError)
                TimeField("Time", vm.time, { vm.updateTime(it) }, Modifier.weight(1f), onClear = { vm.updateTime(null) })
            }
            vm.trip?.let { t ->
                val d = vm.date
                if (d != null && (d.isBefore(t.startDate) || d.isAfter(t.endDate))) {
                    Text(
                        "This date is outside the trip (${Fmt.dateRange(t.startDate, t.endDate)}). That's fine — it will still appear in the story.",
                        style = Travel.type.bodySmall, color = Travel.colors.gold,
                        modifier = Modifier.padding(start = 4.dp).offset(y = (-12).dp),
                    )
                }
            }

            FormField(
                "Where", vm.location, vm::updateLocation,
                placeholder = "A café, a beach, a mountain pass…", leadingIcon = Icons.Rounded.Place,
                capitalization = KeyboardCapitalization.Words,
            )

            Column {
                Text("How did it feel?", style = Travel.type.label, color = Travel.colors.inkSoft, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    items(Mood.entries) { m ->
                        ChoicePill("${m.emoji}  ${m.label}", vm.mood == m, { vm.updateMood(if (vm.mood == m) null else m) })
                    }
                }
            }

            Column {
                Text("The story", style = Travel.type.label, color = Travel.colors.inkSoft, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                JournalPage(vm.body, vm::updateBody)
                val words = vm.body.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
                Text(
                    if (words == 1) "1 word" else "$words words",
                    style = Travel.type.bodySmall, color = Travel.colors.inkFaint,
                    modifier = Modifier.align(Alignment.End).padding(top = 6.dp, end = 4.dp),
                )
            }

            Column {
                Text("Photos", style = Travel.type.label, color = Travel.colors.inkSoft, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                PhotoEditorStrip(
                    saved = visibleSaved,
                    pending = vm.pending,
                    onAddPhotos = vm::addPhotos,
                    onRemoveSaved = vm::removeSaved,
                    onRemovePending = vm::removePending,
                )
                if (visibleSaved.isNotEmpty() || vm.pending.isNotEmpty()) {
                    Spacer(Modifier.height(Space.lg))
                    Overline("Captions", Modifier.padding(start = 4.dp, bottom = Space.sm))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        visibleSaved.forEach { p ->
                            CaptionRow(
                                thumb = { PhotoImage(p.relativePath, Modifier.fillMaxSize()) },
                                value = vm.savedCaptionEdits[p.id] ?: p.caption,
                                onChange = { vm.setSavedCaption(p.id, it) },
                            )
                        }
                        vm.pending.forEach { u ->
                            CaptionRow(
                                thumb = { UriImage(u, Modifier.fillMaxSize()) },
                                value = vm.pendingCaptions[u].orEmpty(),
                                onChange = { vm.setPendingCaption(u, it) },
                            )
                        }
                    }
                }
            }

            ChipInput(
                label = "Tags",
                values = vm.tags,
                onValuesChange = vm::updateTags,
                placeholder = "sunset, street food, friends…",
                suggestions = suggestions,
                onDraftChange = vm::updateTagDraft,
            )
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "Your unsaved words and photos for this entry will be lost.",
            confirmLabel = "Discard",
            onConfirm = { nav.back() },
            onDismiss = { confirmDiscard = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this entry?",
            message = "The entry and its photos will be permanently removed from this device.",
            confirmLabel = "Delete",
            onConfirm = vm::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Borderless large serif title input. */
@Composable
private fun TitleField(value: String, onChange: (String) -> Unit, error: String?) {
    val c = Travel.colors
    val style = Travel.type.headline.copy(color = c.ink)
    Column {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = style,
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text("Give this day a title", style = style.copy(color = c.inkFaint))
                    inner()
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Hairline()
        if (error != null) Text(error, style = Travel.type.bodySmall, color = c.danger, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Long-form text on a softly ruled journal page. */
@Composable
private fun JournalPage(value: String, onChange: (String) -> Unit) {
    val c = Travel.colors
    val style = Travel.type.journal.copy(color = c.ink)
    val density = LocalDensity.current
    val lineHeightPx = with(density) { style.lineHeight.toPx() }
    val padV = 20.dp
    val padH = 20.dp
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = style,
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        minLines = 10,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shapes.lg)
            .background(c.card)
            .drawBehind {
                val left = padH.toPx()
                var y = padV.toPx() + lineHeightPx
                while (y < size.height - padV.toPx() / 2) {
                    drawLine(c.line, Offset(left, y), Offset(size.width - left, y), strokeWidth = 1.dp.toPx())
                    y += lineHeightPx
                }
                // Margin rule, like a notebook.
                drawLine(c.accent.copy(alpha = 0.25f), Offset(left - 8.dp.toPx(), 0f), Offset(left - 8.dp.toPx(), size.height), 1.dp.toPx())
            }
            .padding(horizontal = padH, vertical = padV),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text("Dear diary, today…", style = style.copy(color = c.inkFaint))
                inner()
            }
        },
    )
}

@Composable
private fun CaptionRow(thumb: @Composable () -> Unit, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(Shapes.sm)) { thumb() }
        Spacer(Modifier.width(Space.md))
        TextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Add a caption…", style = Travel.type.body) },
            textStyle = Travel.type.body,
            singleLine = true,
            shape = Shapes.md,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}
