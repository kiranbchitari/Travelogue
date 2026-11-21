package com.travelogue.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Group of form fields under a small heading. */
@Composable
fun FormSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Overline(title, Modifier.padding(start = 4.dp, bottom = Space.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Space.md), content = content)
    }
}

/** Label above a soft filled box. */
@Composable
private fun FieldFrame(label: String, error: String?, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = Travel.type.label, color = Travel.colors.inkSoft, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        content()
        if (error != null) {
            Text(error, style = Travel.type.bodySmall, color = Travel.colors.danger, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
    }
}

@Composable
fun fieldColors(): TextFieldColors {
    val c = Travel.colors
    return TextFieldDefaults.colors(
        focusedContainerColor = c.card,
        unfocusedContainerColor = c.card,
        disabledContainerColor = c.card,
        errorContainerColor = c.card,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        cursorColor = c.accent,
        focusedTextColor = c.ink,
        unfocusedTextColor = c.ink,
        disabledTextColor = c.ink,
        focusedPlaceholderColor = c.inkFaint,
        unfocusedPlaceholderColor = c.inkFaint,
        errorPlaceholderColor = c.inkFaint,
        errorTextColor = c.ink,
        errorCursorColor = c.danger,
        errorLeadingIconColor = c.danger,
        errorTrailingIconColor = c.inkSoft,
        errorPrefixColor = c.inkSoft,
        focusedLeadingIconColor = c.inkSoft,
        unfocusedLeadingIconColor = c.inkFaint,
        focusedTrailingIconColor = c.inkSoft,
        unfocusedTrailingIconColor = c.inkFaint,
    )
}

/** Text input used by every form. */
@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    leadingIcon: ImageVector? = null,
    prefix: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    FieldFrame(label, error, modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, style = Travel.type.bodyLarge) },
            textStyle = Travel.type.bodyLarge,
            singleLine = singleLine,
            minLines = minLines,
            isError = error != null,
            shape = Shapes.md,
            colors = fieldColors(),
            leadingIcon = leadingIcon?.let { { Icon(it, null, modifier = Modifier.size(20.dp)) } },
            prefix = prefix?.let { { Text(it, style = Travel.type.bodyLarge, color = Travel.colors.inkSoft) } },
            trailingIcon = trailing,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
            ),
        )
    }
}

/** Decimal amount input with a currency symbol prefix. */
@Composable
fun AmountField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    currency: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    placeholder: String = "0",
) {
    FormField(
        label = label,
        value = value,
        onValueChange = { s -> onValueChange(s.filter { it.isDigit() || it == '.' || it == ',' }) },
        modifier = modifier,
        placeholder = placeholder,
        error = error,
        keyboardType = KeyboardType.Decimal,
        prefix = Fmt.currencySymbol(currency) + " ",
    )
}

/** A field-looking button that opens a picker. */
@Composable
fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
    icon: ImageVector = Icons.Rounded.ExpandMore,
    error: String? = null,
    onClear: (() -> Unit)? = null,
) {
    FieldFrame(label, error, modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(Shapes.md)
                .background(Travel.colors.card)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value.ifEmpty { placeholder },
                style = Travel.type.bodyLarge,
                color = if (value.isEmpty()) Travel.colors.inkFaint else Travel.colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (onClear != null && value.isNotEmpty()) {
                Icon(Icons.Rounded.Close, "Clear", tint = Travel.colors.inkFaint, modifier = Modifier.size(20.dp).clip(Shapes.pill).clickable(onClick = onClear))
                Spacer(Modifier.width(8.dp))
            }
            Icon(icon, null, tint = Travel.colors.inkFaint, modifier = Modifier.size(20.dp))
        }
    }
}

/** Date picker field using the Material 3 date picker in a dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    placeholder: String = "Pick a date",
    onClear: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, date?.let(Fmt::date) ?: "", { open = true }, modifier, placeholder, Icons.Rounded.CalendarMonth, error, onClear)
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("Done", color = Travel.colors.accent) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel", color = Travel.colors.inkSoft) } },
            colors = DatePickerDefaults.colors(containerColor = Travel.colors.cardHigh),
        ) {
            DatePicker(
                state = state,
                colors = DatePickerDefaults.colors(
                    containerColor = Travel.colors.cardHigh,
                    selectedDayContainerColor = Travel.colors.accent,
                    todayDateBorderColor = Travel.colors.accent,
                    todayContentColor = Travel.colors.accent,
                ),
            )
        }
    }
}

/** Time picker field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    time: LocalTime?,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Any time",
    onClear: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, time?.let(Fmt::time) ?: "", { open = true }, modifier, placeholder, Icons.Rounded.Schedule, null, onClear)
    if (open) {
        val initial = time ?: LocalTime.now()
        val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = Travel.colors.cardHigh,
            confirmButton = {
                TextButton(onClick = { onTimeChange(LocalTime.of(state.hour, state.minute)); open = false }) { Text("Done", color = Travel.colors.accent) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel", color = Travel.colors.inkSoft) } },
            text = {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        selectorColor = Travel.colors.accent,
                        timeSelectorSelectedContainerColor = Travel.colors.accentSoft,
                        periodSelectorSelectedContainerColor = Travel.colors.accentSoft,
                    ),
                )
            },
        )
    }
}

/** Dropdown picker for a fixed list of options. */
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
    error: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        PickerField(label, selected?.let(optionLabel) ?: "", { open = true }, placeholder = placeholder, error = error)
        DropdownMenu(open, { open = false }, containerColor = Travel.colors.cardHigh, shape = Shapes.md, modifier = Modifier.heightIn(max = 360.dp)) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(optionLabel(o), style = Travel.type.body, color = if (o == selected) Travel.colors.accent else Travel.colors.ink) },
                    onClick = { onSelect(o); open = false },
                )
            }
        }
    }
}

/** Row with a label and a switch. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, description: String? = null) {
    Row(
        modifier.fillMaxWidth().clip(Shapes.md).background(Travel.colors.card).clickable { onCheckedChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Travel.type.bodyLarge, color = Travel.colors.ink)
            if (description != null) Text(description, style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Travel.colors.accent,
                checkedThumbColor = Travel.colors.onAccent,
                uncheckedTrackColor = Travel.colors.line,
                uncheckedBorderColor = Color.Transparent,
                uncheckedThumbColor = Travel.colors.inkFaint,
            ),
        )
    }
}

/** Read-only star rating. */
@Composable
fun RatingStars(rating: Int, modifier: Modifier = Modifier, max: Int = 5, size: Dp = 16.dp, color: Color = Travel.colors.gold) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(max) { i ->
            Icon(
                if (i < rating) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                null,
                tint = if (i < rating) color else Travel.colors.line,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** Tappable star rating input. Tapping the current value clears it. */
@Composable
fun RatingInput(label: String, rating: Int, onRatingChange: (Int) -> Unit, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Row(
        modifier.fillMaxWidth().clip(Shapes.md).background(Travel.colors.card).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Travel.type.body, color = Travel.colors.inkSoft, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(5) { i ->
                val v = i + 1
                Icon(
                    if (v <= rating) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    "$v stars",
                    tint = if (v <= rating) Travel.colors.gold else Travel.colors.inkFaint,
                    modifier = Modifier.size(size).clip(Shapes.pill).clickable { onRatingChange(if (v == rating) 0 else v) }.padding(2.dp),
                )
            }
        }
    }
}

/** Free-form tag/name entry: type and press add; shows removable chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipInput(
    label: String,
    values: List<String>,
    onValuesChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Type and press +",
    suggestions: List<String> = emptyList(),
) {
    var text by remember { mutableStateOf("") }
    fun add(v: String) {
        val t = v.trim().trimEnd(',')
        if (t.isNotEmpty() && values.none { it.equals(t, true) }) onValuesChange(values + t)
        text = ""
    }
    Column(modifier) {
        FormField(
            label = label,
            value = text,
            onValueChange = { if (it.endsWith(",")) add(it) else text = it },
            placeholder = placeholder,
            capitalization = KeyboardCapitalization.Words,
            trailing = {
                TextButton(onClick = { add(text) }, enabled = text.isNotBlank()) { Text("Add", color = Travel.colors.accent) }
            },
        )
        val remaining = suggestions.filter { s -> values.none { it.equals(s, true) } }.take(8)
        if (values.isNotEmpty() || remaining.isNotEmpty()) Spacer(Modifier.height(Space.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            values.forEach { v ->
                Row(
                    Modifier.clip(Shapes.pill).background(Travel.colors.ink).clickable { onValuesChange(values - v) }.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(v, style = Travel.type.label, color = Travel.colors.background)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Close, "Remove $v", tint = Travel.colors.background, modifier = Modifier.size(14.dp))
                }
            }
            remaining.forEach { s ->
                Text(
                    "+ $s",
                    style = Travel.type.label,
                    color = Travel.colors.inkSoft,
                    modifier = Modifier.clip(Shapes.pill).background(Travel.colors.backgroundDeep).clickable { add(s) }.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}
