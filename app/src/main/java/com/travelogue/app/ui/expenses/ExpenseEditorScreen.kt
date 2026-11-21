package com.travelogue.app.ui.expenses

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.CommonCurrencies
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.entity.ExpenseCategoryEntity
import com.travelogue.app.data.db.entity.PaymentMethod
import com.travelogue.app.data.db.entity.SplitType
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav
import kotlin.math.abs

@Composable
fun ExpenseEditorScreen(nav: Nav, tripId: Long, expenseId: Long) {
    val vm = appViewModel(key = "expense-editor-$tripId-$expenseId") { ExpenseEditorViewModel(trips, expenses, tripId, expenseId) }
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }

    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    ScreenScaffold(
        title = if (vm.isEdit) "Edit expense" else "New expense",
        subtitle = vm.trip?.name,
        onBack = nav::back,
        actions = {
            if (vm.isEdit) CircleIconButton(Icons.Rounded.DeleteOutline, { confirmDelete = true }, contentDescription = "Delete expense", tint = Travel.colors.danger)
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Travel.colors.background)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = Space.gutter, vertical = Space.md),
            ) {
                PrimaryButton(
                    text = if (vm.isEdit) "Save changes" else "Save expense",
                    onClick = { vm.save(onSaved = nav::back, onError = toast) },
                    icon = Icons.Rounded.Check,
                    loading = vm.saving,
                    enabled = vm.loaded,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                .padding(horizontal = Space.gutter)
                .padding(top = Space.sm, bottom = Space.xxxl),
            verticalArrangement = Arrangement.spacedBy(Space.xxl),
        ) {
            AmountHero(vm)

            FormSection("Details") {
                FormField(
                    label = "Title",
                    value = vm.title,
                    onValueChange = { vm.title = it },
                    placeholder = "Dinner at the beach shack",
                    error = vm.errors["title"],
                )
                val selectedCategory = categories.firstOrNull { it.id == vm.categoryId }
                PickerField(
                    label = "Category",
                    value = selectedCategory?.name ?: "",
                    onClick = { showCategories = true },
                    placeholder = "Choose a category",
                    error = vm.errors["category"],
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    DateField("Date", vm.date, { vm.date = it }, Modifier.weight(1f))
                    TimeField("Time", vm.time, { vm.time = it }, Modifier.weight(1f), onClear = { vm.time = null })
                }
                FormField(
                    label = "Location",
                    value = vm.location,
                    onValueChange = { vm.location = it },
                    placeholder = "Where was this?",
                    leadingIcon = Icons.Rounded.Place,
                    capitalization = KeyboardCapitalization.Words,
                )
            }

            FormSection("Payment") {
                if (vm.travellers.isNotEmpty()) {
                    Text("Paid by", style = Travel.type.label, color = Travel.colors.inkSoft, modifier = Modifier.padding(start = 4.dp))
                    TravellerChoices(
                        names = vm.travellers.map { it.id to it.name },
                        selected = setOfNotNull(vm.paidBy),
                        onToggle = { id -> vm.paidBy = if (vm.paidBy == id) null else id },
                    )
                    vm.errors["paidBy"]?.let { Text(it, style = Travel.type.bodySmall, color = Travel.colors.danger, modifier = Modifier.padding(start = 4.dp)) }
                }
                Text("Payment method", style = Travel.type.label, color = Travel.colors.inkSoft, modifier = Modifier.padding(start = 4.dp))
                ChoiceRow(
                    options = PaymentMethod.entries,
                    selected = vm.paymentMethod,
                    label = { it.label },
                    onSelect = { vm.paymentMethod = it },
                    contentPadding = PaddingValues(0.dp),
                )
            }

            if (vm.canSplit) SplitSection(vm)

            FormSection("Receipt & notes") {
                SinglePhotoField(
                    label = "Receipt photo",
                    savedPath = vm.receiptPath,
                    pending = vm.pendingReceipt,
                    onPick = vm::pickReceipt,
                    onRemove = vm::clearReceipt,
                    height = 160.dp,
                )
                FormField(
                    label = "Description",
                    value = vm.description,
                    onValueChange = { vm.description = it },
                    placeholder = "What was it for?",
                    singleLine = false,
                    minLines = 2,
                )
                FormField(
                    label = "Notes",
                    value = vm.notes,
                    onValueChange = { vm.notes = it },
                    placeholder = "Anything to remember",
                    singleLine = false,
                    minLines = 2,
                )
            }
        }
    }

    if (showCategories) {
        CategorySheet(
            categories = categories,
            selectedId = vm.categoryId,
            onSelect = { vm.categoryId = it.id; showCategories = false },
            onManage = { showCategories = false; nav.categories() },
            onDismiss = { showCategories = false },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this expense?",
            message = "It will be removed from the trip totals, splits and analytics. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(onDeleted = nav::back, onError = toast) },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Big centred amount with the currency picker beside it. */
@Composable
private fun AmountHero(vm: ExpenseEditorViewModel) {
    val currencies = remember(vm.currency, vm.trip?.currency) {
        (listOfNotNull(vm.trip?.currency, vm.currency) + CommonCurrencies).distinct()
    }
    SurfaceCard(contentPadding = PaddingValues(Space.xl)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            AmountField(
                label = "Amount",
                value = vm.amount,
                onValueChange = { vm.amount = it },
                currency = vm.currency,
                modifier = Modifier.weight(1.6f),
                error = vm.errors["amount"],
            )
            DropdownField(
                label = "Currency",
                options = currencies,
                selected = vm.currency,
                optionLabel = { "$it  ${Fmt.currencySymbol(it)}" },
                onSelect = { vm.currency = it },
                modifier = Modifier.weight(1f),
                error = vm.errors["currency"],
            )
        }
        val trip = vm.trip
        if (trip != null && !vm.currency.equals(trip.currency, true)) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "Foreign currency. Set a manual rate in the trip's financial summary to include it in totals.",
                style = Travel.type.bodySmall,
                color = Travel.colors.inkFaint,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TravellerChoices(names: List<Pair<Long, String>>, selected: Set<Long>, onToggle: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        names.forEach { (id, name) ->
            ChoicePill(name, id in selected, { onToggle(id) })
        }
    }
}

@Composable
private fun SplitSection(vm: ExpenseEditorViewModel) {
    FormSection("Split between travellers") {
        ChoiceRow(
            options = SplitType.entries,
            selected = vm.splitType,
            label = { it.label },
            onSelect = {
                vm.splitType = it
                if (it == SplitType.CUSTOM && vm.customShares.values.all { s -> s.isBlank() }) vm.prefillCustomEqually()
            },
            contentPadding = PaddingValues(0.dp),
        )
        when (vm.splitType) {
            SplitType.NONE -> Text(
                "Not shared — this counts as personal spending for whoever paid.",
                style = Travel.type.bodySmall,
                color = Travel.colors.inkFaint,
                modifier = Modifier.padding(start = 4.dp),
            )
            SplitType.EQUAL -> {
                Text("Shared with", style = Travel.type.label, color = Travel.colors.inkSoft, modifier = Modifier.padding(start = 4.dp))
                TravellerChoices(vm.travellers.map { it.id to it.name }, vm.participants, vm::toggleParticipant)
                val shares = vm.equalShares
                if (shares.isNotEmpty() && (vm.amountValue ?: 0.0) > 0) {
                    SurfaceCard(elevation = 0.dp, color = Travel.colors.backgroundDeep, contentPadding = PaddingValues(Space.lg)) {
                        vm.travellers.filter { it.id in shares }.forEach { t ->
                            ShareLine(t.name, Fmt.money(shares[t.id] ?: 0.0, vm.currency), t.id == vm.paidBy)
                        }
                    }
                }
            }
            SplitType.CUSTOM -> {
                vm.travellers.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InitialAvatar(t.name, size = 36.dp)
                        Spacer(Modifier.width(Space.md))
                        Text(
                            t.name + if (t.id == vm.paidBy) " (paid)" else "",
                            style = Travel.type.body,
                            color = Travel.colors.ink,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        FormField(
                            label = "",
                            value = vm.customShares[t.id] ?: "",
                            onValueChange = { vm.setShare(t.id, it) },
                            placeholder = "0",
                            keyboardType = KeyboardType.Decimal,
                            prefix = Fmt.currencySymbol(vm.currency) + " ",
                            modifier = Modifier.width(150.dp),
                        )
                    }
                }
                val remaining = vm.customRemaining
                val ok = abs(remaining) <= 0.009
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Shapes.md)
                        .background(if (ok) Travel.colors.sageSoft else Travel.colors.accentSoft)
                        .padding(horizontal = Space.lg, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when {
                            ok -> "Shares add up to the total"
                            remaining > 0 -> "Left to allocate"
                            else -> "Over the total by"
                        },
                        style = Travel.type.label,
                        color = if (ok) Travel.colors.success else Travel.colors.accent,
                        modifier = Modifier.weight(1f),
                    )
                    if (!ok) Text(Fmt.money(abs(remaining), vm.currency), style = Travel.type.label, color = Travel.colors.accent)
                }
                TextButton(onClick = vm::prefillCustomEqually) {
                    Text("Reset to an equal split", style = Travel.type.label, color = Travel.colors.accent)
                }
            }
        }
        vm.errors["split"]?.let { Text(it, style = Travel.type.bodySmall, color = Travel.colors.danger, modifier = Modifier.padding(start = 4.dp)) }
    }
}

@Composable
private fun ShareLine(name: String, amount: String, paid: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = Travel.type.body, color = Travel.colors.ink, modifier = Modifier.weight(1f))
        if (paid) {
            Tag("Paid", color = Travel.colors.success)
            Spacer(Modifier.width(Space.sm))
        }
        Text(amount, style = Travel.type.label, color = Travel.colors.ink)
    }
}

/** Grid of categories with icon and colour, plus a link to manage custom categories. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySheet(
    categories: List<ExpenseCategoryEntity>,
    selectedId: Long?,
    onSelect: (ExpenseCategoryEntity) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    TravelSheet(onDismiss = onDismiss, title = "Category") {
        FlowRow(
            Modifier.fillMaxWidth(),
            maxItemsInEachRow = 4,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            categories.forEach { cat ->
                val selected = cat.id == selectedId
                Column(
                    Modifier
                        .weight(1f)
                        .clip(Shapes.md)
                        .background(if (selected) cat.colorValue().copy(alpha = 0.14f) else Travel.colors.card)
                        .then(if (selected) Modifier.border(1.5.dp, cat.colorValue(), Shapes.md) else Modifier)
                        .clickable { onSelect(cat) }
                        .padding(vertical = Space.md, horizontal = Space.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconBadge(cat.icon(), cat.colorValue(), size = 40.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        cat.name,
                        style = Travel.type.bodySmall,
                        color = Travel.colors.ink,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Pad the last row so tiles keep equal widths.
            repeat((4 - categories.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(Space.lg))
        SecondaryButton("Manage categories", onManage, icon = Icons.Rounded.Tune, modifier = Modifier.fillMaxWidth())
    }
}

