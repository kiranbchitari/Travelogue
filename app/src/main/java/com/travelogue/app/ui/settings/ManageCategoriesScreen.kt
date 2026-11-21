package com.travelogue.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.data.db.entity.ExpenseCategoryEntity
import com.travelogue.app.data.repository.ExpenseRepository
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val repo: ExpenseRepository) : ViewModel() {
    val categories: StateFlow<List<ExpenseCategoryEntity>> = repo.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(existing: ExpenseCategoryEntity?, name: String, iconKey: String, color: Long, onResult: (String?) -> Unit) = viewModelScope.launch {
        val n = name.trim()
        if (n.isEmpty()) { onResult("Give the category a name"); return@launch }
        val clash = categories.value.any { it.name.equals(n, true) && it.id != existing?.id }
        if (clash) { onResult("A category with this name already exists"); return@launch }
        if (existing == null) repo.addCategory(n, iconKey, color) else repo.updateCategory(existing.copy(name = n, iconKey = iconKey, color = color))
        onResult(null)
    }

    fun delete(c: ExpenseCategoryEntity) = viewModelScope.launch { repo.deleteCategory(c.id) }
}

@Composable
fun ManageCategoriesScreen(nav: Nav) {
    val vm = appViewModel { CategoriesViewModel(expenses) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ExpenseCategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ExpenseCategoryEntity?>(null) }

    ScreenScaffold(
        title = "Expense categories",
        onBack = nav::back,
        floatingAction = { AccentFab(Icons.Rounded.Add, { creating = true }, label = "New category", modifier = Modifier.navigationBarsPadding()) },
    ) { padding ->
        val defaults = categories.filter { it.isDefault }
        val custom = categories.filterNot { it.isDefault }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = padding.calculateTopPadding() + Space.sm, bottom = padding.calculateBottomPadding() + 120.dp),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            item { Overline("Your categories", Modifier.padding(top = Space.sm, bottom = 4.dp)) }
            if (custom.isEmpty()) {
                item { Text("Create your own categories for things like tips, souvenirs or visas.", style = Travel.type.body, color = Travel.colors.inkSoft) }
            }
            items(custom, key = { it.id }) { c -> CategoryRow(c, onClick = { editing = c }, onDelete = { deleting = c }) }
            item { Overline("Default categories", Modifier.padding(top = Space.lg, bottom = 4.dp)) }
            items(defaults, key = { it.id }) { c -> CategoryRow(c, onClick = { editing = c }, onDelete = null) }
        }
    }

    if (creating || editing != null) {
        CategorySheet(
            existing = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { name, icon, color, done -> vm.save(editing, name, icon, color, done) },
        )
    }
    deleting?.let { c ->
        ConfirmDialog(
            "Delete “${c.name}”?",
            "Expenses in this category will become uncategorised. They are not deleted.",
            "Delete", { vm.delete(c) }, { deleting = null },
        )
    }
}

@Composable
private fun CategoryRow(c: ExpenseCategoryEntity, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(c.icon(), c.colorValue())
            Spacer(Modifier.width(Space.md))
            Text(c.name, style = Travel.type.titleSmall, color = Travel.colors.ink, modifier = Modifier.weight(1f))
            if (onDelete != null) {
                Text("Delete", style = Travel.type.label, color = Travel.colors.danger, modifier = Modifier.clip(Shapes.pill).clickable(onClick = onDelete).padding(8.dp))
            } else {
                Tag("Default", color = Travel.colors.inkSoft)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySheet(
    existing: ExpenseCategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, (String?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var icon by remember { mutableStateOf(existing?.iconKey ?: "other") }
    var color by remember { mutableLongStateOf(existing?.color ?: CategoryColorChoices.first()) }
    var error by remember { mutableStateOf<String?>(null) }

    TravelSheet(onDismiss, title = if (existing == null) "New category" else "Edit category") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(CategoryIcons.forKey(icon), Color(color), size = 56.dp, iconSize = 28.dp)
            Spacer(Modifier.width(Space.lg))
            FormField("Name", name, { name = it; error = null }, placeholder = "e.g. Souvenirs", error = error, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.lg))
        Overline("Icon")
        Spacer(Modifier.height(Space.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            CategoryIcons.keys.forEach { k ->
                val sel = k == icon
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (sel) Color(color).copy(alpha = 0.18f) else Travel.colors.card)
                        .then(if (sel) Modifier.border(1.5.dp, Color(color), CircleShape) else Modifier)
                        .clickable { icon = k },
                    contentAlignment = Alignment.Center,
                ) { Icon(CategoryIcons.forKey(k), k, tint = if (sel) Color(color) else Travel.colors.inkSoft, modifier = Modifier.size(20.dp)) }
            }
        }
        Spacer(Modifier.height(Space.lg))
        Overline("Colour")
        Spacer(Modifier.height(Space.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            CategoryColorChoices.forEach { v ->
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(v)).clickable { color = v }, contentAlignment = Alignment.Center) {
                    if (v == color) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(Space.xxl))
        PrimaryButton("Save", {
            onSave(name, icon, color) { err ->
                if (err == null) { onDismiss(); Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() } else error = err
            }
        }, Modifier.fillMaxWidth())
    }
}
