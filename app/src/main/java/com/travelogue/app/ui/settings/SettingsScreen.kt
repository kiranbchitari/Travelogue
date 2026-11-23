package com.travelogue.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.BuildConfig
import com.travelogue.app.core.designsystem.components.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.travelogue.app.core.designsystem.theme.AppTheme
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.travelTypography
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.ThemeMode
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.storage.StorageUsage
import com.travelogue.app.core.util.CommonCurrencies
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.backup.BackupHealth
import com.travelogue.app.data.backup.BackupStatus
import com.travelogue.app.ui.backup.destinationName
import com.travelogue.app.ui.backup.healthLook
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.common.tabContentPadding
import com.travelogue.app.ui.navigation.Nav

@Composable
fun SettingsScreen(nav: Nav) {
    val vm = appViewModel { SettingsViewModel(this) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val hasDemo by vm.hasDemo.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backup by vm.backup.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDemoRemove by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.message.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = tabContentPadding(),
        verticalArrangement = Arrangement.spacedBy(Space.xxl),
    ) {
        item { LargeHeader("Your journal", "Settings") }

        item {
            Column {
                Overline("Theme", Modifier.padding(start = Space.gutter + 4.dp, bottom = Space.sm))
                ThemePicker(settings.theme, dark = Travel.colors.isDark, onSelect = vm::setAppTheme)
                Text(
                    "${settings.theme.title} is on. Your choice is saved on this phone.",
                    style = Travel.type.bodySmall, color = Travel.colors.inkFaint,
                    modifier = Modifier.padding(start = Space.gutter + 4.dp, end = Space.gutter, top = Space.sm),
                )
            }
        }

        item {
            SettingsGroup("Appearance") {
                Text("Brightness", style = Travel.type.body, color = Travel.colors.inkSoft, modifier = Modifier.padding(bottom = Space.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (m, l) ->
                        ChoicePill(
                            l, settings.themeMode == m, { vm.setTheme(m) },
                            icon = when (m) { ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto; ThemeMode.LIGHT -> Icons.Rounded.LightMode; ThemeMode.DARK -> Icons.Rounded.DarkMode },
                        )
                    }
                }
            }
        }

        item {
            SettingsGroup("Preferences") {
                DropdownField(
                    label = "Default currency",
                    options = if (settings.defaultCurrency in CommonCurrencies) CommonCurrencies else listOf(settings.defaultCurrency) + CommonCurrencies,
                    selected = settings.defaultCurrency,
                    optionLabel = { "$it  ·  ${Fmt.currencySymbol(it)}" },
                    onSelect = vm::setCurrency,
                )
                Spacer(Modifier.height(Space.sm))
                NavRow(Icons.Rounded.Group, "Travellers", "People you travel with", nav::travellers)
                NavRow(Icons.Rounded.Category, "Expense categories", "Default and custom categories", nav::categories)
                NavRow(Icons.Rounded.CurrencyExchange, "Exchange rates", "Bring every trip into ${settings.defaultCurrency}", nav::exchangeRates)
            }
        }

        item {
            SettingsGroup("Storage") {
                StorageCard(usage, counts.first, counts.second)
            }
        }

        item {
            SettingsGroup("Backup & restore") {
                BackupStatusRow(backup, nav::backup)
            }
        }

        item {
            SettingsGroup("Sample data") {
                if (hasDemo) {
                    ActionRow(Icons.Rounded.DeleteSweep, "Remove sample trips", "Deletes only the demo trips", Travel.colors.gold) { confirmDemoRemove = true }
                } else {
                    ActionRow(Icons.Rounded.AutoAwesome, "Load sample trips", "Explore the app with three demo trips", Travel.colors.gold, vm::loadDemo)
                }
            }
        }

        item {
            SettingsGroup("Danger zone") {
                ActionRow(Icons.Rounded.DeleteForever, "Clear all app data", "Permanently delete every trip, photo and setting", Travel.colors.danger) { confirmClear = true }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Travelogue", style = Travel.type.title, color = Travel.colors.ink)
                Text("Version ${BuildConfig.VERSION_NAME}", style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
                Spacer(Modifier.height(Space.sm))
                Text("Offline-first · Optional Drive backup · No tracking", style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
            }
        }
    }

    busy?.let {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            containerColor = Travel.colors.cardHigh,
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Travel.colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(Space.lg))
                    Text(it, style = Travel.type.body, color = Travel.colors.ink)
                }
            },
            confirmButton = {},
        )
    }
    if (confirmDemoRemove) {
        ConfirmDialog("Remove sample trips?", "The three demo trips and their photos will be deleted. Your own trips are not affected.", "Remove", vm::removeDemo, { confirmDemoRemove = false })
    }
    if (confirmClear) ClearDataDialog(onConfirm = vm::clearAll, onDismiss = { confirmClear = false })
}

/** One preview card per theme, each drawn in that theme's own colours, shapes and scenery. */
@Composable
private fun ThemePicker(selected: AppTheme, dark: Boolean, onSelect: (AppTheme) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        items(AppTheme.entries, key = { it.name }) { theme -> ThemeCard(theme, dark, theme == selected) { onSelect(theme) } }
    }
}

@Composable
private fun ThemeCard(theme: AppTheme, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val c = theme.colors(dark)
    val shape = theme.shapes.lg
    val edge by animateColorAsState(if (selected) c.accent else c.line, label = "themeEdge")
    Column(
        Modifier
            .width(158.dp)
            .pressable(onClick)
            .semantics { this.selected = selected; role = Role.RadioButton }
            .clip(shape)
            .background(c.card)
            .border(if (selected) 2.dp else 1.dp, edge, shape),
    ) {
        Box(Modifier.fillMaxWidth().height(104.dp)) {
            SceneArt(Modifier.matchParentSize(), theme = theme, dark = dark)
            if (selected) {
                Box(Modifier.align(Alignment.TopEnd).padding(Space.sm).size(26.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, "Selected", tint = c.onAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
        Column(Modifier.padding(horizontal = Space.md, vertical = Space.md)) {
            Text(theme.title.voice(), style = travelTypography(theme.flavour).titleSmall.copy(fontFamily = travelTypography(theme.flavour).title.fontFamily, fontSize = 17.sp), color = c.ink, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(theme.tagline, style = Travel.type.bodySmall, color = c.inkSoft, minLines = 2, maxLines = 2)
            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(c.accent, c.gold, c.secondary, c.bar).forEach { Box(Modifier.size(14.dp).clip(CircleShape).background(it)) }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = Space.gutter)) {
        Overline(title, Modifier.padding(start = 4.dp, bottom = Space.sm))
        SurfaceCard(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Shapes.md).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, Travel.colors.accent, size = 38.dp, iconSize = 19.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = Travel.type.titleSmall, color = Travel.colors.ink)
            Text(subtitle, style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Travel.colors.inkFaint)
    }
}

/** Whether the data is safe right now, at a glance; opens the backup screen. */
@Composable
private fun BackupStatusRow(backup: Pair<BackupStatus, Long>?, onClick: () -> Unit) {
    val status = backup?.first
    val look = status?.let { healthLook(it.health) }
    Row(
        Modifier.fillMaxWidth().clip(Shapes.md).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(look?.icon ?: Icons.Rounded.Cloud, look?.tint ?: Travel.colors.inkFaint, size = 44.dp, iconSize = 22.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(look?.headline ?: "Backup & restore", style = Travel.type.titleSmall, color = Travel.colors.ink)
            val latest = status?.latest
            Text(
                when {
                    latest != null -> "Last backup ${Fmt.ago(latest.at, backup.second)} · ${destinationName(latest)}"
                    status?.health == BackupHealth.NEVER -> "Your trips exist only on this phone"
                    else -> "Back up to Google Drive or a ZIP file"
                },
                style = Travel.type.bodySmall, color = Travel.colors.inkFaint,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Travel.colors.inkFaint)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Shapes.md).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, size = 38.dp, iconSize = 19.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = Travel.type.titleSmall, color = if (tint == Travel.colors.danger) tint else Travel.colors.ink)
            Text(subtitle, style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        }
    }
}

@Composable
private fun StorageCard(usage: StorageUsage?, trips: Int, photos: Int) {
    if (usage == null) { LoadingState(); return }
    val c = Travel.colors
    Text(Fmt.bytes(usage.totalBytes), style = Travel.type.number, color = c.ink)
    Text("used by $trips trips and $photos photos", style = Travel.type.bodySmall, color = c.inkFaint)
    Spacer(Modifier.height(Space.lg))
    val total = usage.totalBytes.coerceAtLeast(1).toFloat()
    val segments = listOf(
        Triple("Photos", usage.imageBytes, c.accent),
        Triple("Documents", usage.documentBytes, c.secondary),
        Triple("Database", usage.databaseBytes, c.gold),
    )
    Row(Modifier.fillMaxWidth().height(10.dp).clip(Shapes.pill).background(c.line)) {
        segments.forEach { (_, bytes, color) ->
            val w = bytes / total
            if (w > 0.002f) Box(Modifier.weight(w).fillMaxHeight().background(color))
        }
    }
    Spacer(Modifier.height(Space.md))
    segments.forEach { (label, bytes, color) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Text(label, style = Travel.type.body, color = c.inkSoft, modifier = Modifier.weight(1f))
            Text(Fmt.bytes(bytes), style = Travel.type.label, color = c.ink)
        }
    }
    Spacer(Modifier.height(Space.sm))
    Hairline()
    Spacer(Modifier.height(Space.sm))
    val low = usage.freeBytes < 500L * 1024 * 1024
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (low) Icons.Rounded.Warning else Icons.Rounded.PhoneAndroid, null, tint = if (low) c.danger else c.inkFaint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "${Fmt.bytes(usage.freeBytes)} free on this device" + if (low) " — storage is running low" else "",
            style = Travel.type.bodySmall, color = if (low) c.danger else c.inkFaint,
        )
    }
}

/** Two-step protection for the one irreversible action in the app: the user must type DELETE. */
@Composable
private fun ClearDataDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Travel.colors.cardHigh,
        shape = Shapes.lg,
        icon = { IconBadge(Icons.Rounded.DeleteForever, Travel.colors.danger, size = 52.dp, iconSize = 26.dp) },
        title = { Text("Clear all data?", style = Travel.type.title, color = Travel.colors.ink) },
        text = {
            Column {
                Text(
                    "Every trip, expense, diary entry, photo and document will be permanently deleted from this device. This cannot be undone. Consider exporting a backup first.",
                    style = Travel.type.body, color = Travel.colors.inkSoft,
                )
                Spacer(Modifier.height(Space.lg))
                FormField("Type DELETE to confirm", text, { text = it }, placeholder = "DELETE", capitalization = KeyboardCapitalization.Characters)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }, enabled = text.trim() == "DELETE") {
                Text("Delete everything", color = if (text.trim() == "DELETE") Travel.colors.danger else Travel.colors.inkFaint, style = Travel.type.label)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Travel.colors.inkSoft) } },
    )
}
