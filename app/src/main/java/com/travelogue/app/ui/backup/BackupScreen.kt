package com.travelogue.app.ui.backup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.storage.StorageUsage
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.backup.BackupHealth
import com.travelogue.app.data.backup.BackupHistory
import com.travelogue.app.data.backup.BackupRecord
import com.travelogue.app.data.backup.BackupStatus
import com.travelogue.app.data.backup.ContentCounts
import com.travelogue.app.data.backup.backupStatus
import com.travelogue.app.di.AppContainer
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

data class BackupScreenUi(
    val history: BackupHistory = BackupHistory(),
    val counts: ContentCounts? = null,
    val usage: StorageUsage? = null,
    val status: BackupStatus = BackupStatus(BackupHealth.EMPTY, null),
    val now: Long = System.currentTimeMillis(),
)

/** Ticks once a minute so "5 minutes ago" and the stale check stay true while the screen is open. */
fun minuteClock(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(60_000)
    }
}

class BackupViewModel(c: AppContainer) : ViewModel() {
    private val counts = c.backup.observeContents()
    /** Media size follows the counts: a photo added or removed changes both. */
    private val usage = counts.map { runCatching { c.media.usage() }.getOrNull() }.onStart { emit(null) }

    val ui: StateFlow<BackupScreenUi> = combine(c.settings.backupHistory, counts, usage, minuteClock()) { history, n, u, now ->
        BackupScreenUi(history, n, u, backupStatus(history, n, now), now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackupScreenUi())
}

/**
 * Everything about backups in one place: whether the data is safe right now, what a backup
 * contains, when each kind last ran, live progress, and restoring.
 */
@Composable
fun BackupScreen(nav: Nav) {
    val vm = appViewModel { BackupViewModel(this) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val job by BackupController.state.collectAsStateWithLifecycle()
    val drive = rememberDriveActions()
    val exportAll = rememberFullBackupExporter()
    val importBackup = rememberBackupImporter()
    val working = job as? BackupUiState.Working

    ScreenScaffold("Backup & restore", onBack = nav::back) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.gutter, end = Space.gutter,
                top = padding.calculateTopPadding() + Space.sm,
                bottom = padding.calculateBottomPadding() + Space.huge,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.xxl),
        ) {
            item {
                StatusCard(
                    ui,
                    busy = working != null,
                    onDrive = drive.backup,
                    onZip = exportAll,
                )
            }
            if (working != null) {
                item {
                    Section("In progress") {
                        Text(working.title, style = Travel.type.titleSmall, color = Travel.colors.ink)
                        Spacer(Modifier.height(Space.md))
                        BackupProgress(working)
                    }
                }
            }
            item { Section("What gets backed up") { ContentsCard(ui.counts, ui.usage) } }
            item { Section("Last backups") { HistoryCard(ui.history, ui.now) } }
            item {
                Section("Restore") {
                    LinkRow(Icons.Rounded.CloudSync, "Restore from Google Drive", "Pick one of your Drive backups", drive.restore)
                    LinkRow(Icons.Rounded.Restore, "Import backup or trip", "Restore from a Travelogue ZIP file", importBackup)
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "You always see what's inside a backup before anything is restored. Trips already on this phone can be skipped, replaced or kept side by side.",
                        style = Travel.type.bodySmall, color = Travel.colors.inkFaint,
                    )
                }
            }
        }
    }
}

// Status ------------------------------------------------------------------------------------------

/** Colour, icon and headline for a backup health. Shared with the Settings row. */
data class HealthLook(val tint: Color, val icon: ImageVector, val headline: String)

@Composable
fun healthLook(health: BackupHealth): HealthLook {
    val c = Travel.colors
    return when (health) {
        BackupHealth.EMPTY -> HealthLook(c.secondary, Icons.Rounded.CloudQueue, "Nothing to back up yet")
        BackupHealth.NEVER -> HealthLook(c.danger, Icons.Rounded.CloudOff, "Not backed up yet")
        BackupHealth.CURRENT -> HealthLook(c.success, Icons.Rounded.CloudDone, "Your data is backed up")
        BackupHealth.CHANGED -> HealthLook(c.gold, Icons.Rounded.CloudSync, "Backup is out of date")
        BackupHealth.STALE -> HealthLook(c.gold, Icons.Rounded.Schedule, "Time for a fresh backup")
    }
}

/** "Google Drive" or "ZIP file". */
fun destinationName(r: BackupRecord): String = if (r.isDrive) "Google Drive" else "ZIP file"

@Composable
private fun StatusCard(ui: BackupScreenUi, busy: Boolean, onDrive: () -> Unit, onZip: () -> Unit) {
    val s = ui.status
    val look = healthLook(s.health)
    val c = Travel.colors
    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(look.icon, look.tint, size = 52.dp, iconSize = 28.dp)
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text(look.headline, style = Travel.type.title, color = c.ink)
                Spacer(Modifier.height(2.dp))
                val latest = s.latest
                Text(
                    if (latest != null) "Last backup ${Fmt.ago(latest.at, ui.now)} · ${destinationName(latest)}"
                    else if (s.health == BackupHealth.NEVER) "Your trips exist only on this phone"
                    else "Add a trip, then back it up here",
                    style = Travel.type.bodySmall, color = c.inkSoft,
                )
            }
        }
        Spacer(Modifier.height(Space.lg))
        when (s.health) {
            BackupHealth.NEVER -> Body("If this phone is lost, reset or replaced, everything in Travelogue goes with it. A backup takes a minute.")
            BackupHealth.EMPTY -> Body("Nothing is stored yet, so there is nothing to lose.")
            BackupHealth.CURRENT -> Body("Nothing has been added or removed since. Edits you make from now on go into your next backup.")
            BackupHealth.STALE -> {
                val days = TimeUnit.MILLISECONDS.toDays(ui.now - (s.latest?.at ?: ui.now))
                Body("Nothing has been added or removed since, but changes made in the last $days days aren't in it.")
            }
            BackupHealth.CHANGED -> {
                Overline("Not in your last backup", color = c.inkSoft)
                Spacer(Modifier.height(Space.sm))
                s.changes.take(MAX_CHANGES).forEach { line ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FiberManualRecord, null, tint = look.tint, modifier = Modifier.size(8.dp))
                        Spacer(Modifier.width(Space.sm))
                        Text(line, style = Travel.type.body, color = c.ink)
                    }
                }
                if (s.changes.size > MAX_CHANGES) {
                    Text("and ${s.changes.size - MAX_CHANGES} more", style = Travel.type.bodySmall, color = c.inkFaint, modifier = Modifier.padding(start = 16.dp, top = 2.dp))
                }
            }
        }
        Spacer(Modifier.height(Space.lg))
        PrimaryButton("Back up to Google Drive", onDrive, Modifier.fillMaxWidth(), icon = Icons.Rounded.CloudUpload, enabled = !busy)
        Spacer(Modifier.height(Space.sm))
        SecondaryButton("Save ZIP to phone", onZip, Modifier.fillMaxWidth(), icon = Icons.Rounded.FolderZip, enabled = !busy)
        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, null, tint = c.inkFaint, modifier = Modifier.size(16.dp).padding(top = 1.dp))
            Spacer(Modifier.width(Space.sm))
            Text(
                "Backups don't run on their own. Tap Back up after adding anything you'd hate to lose.",
                style = Travel.type.bodySmall, color = c.inkFaint,
            )
        }
    }
}

private const val MAX_CHANGES = 5

@Composable
private fun Body(text: String) = Text(text, style = Travel.type.body, color = Travel.colors.inkSoft)

// Contents ----------------------------------------------------------------------------------------

@Composable
private fun ContentsCard(counts: ContentCounts?, usage: StorageUsage?) {
    val c = Travel.colors
    if (counts == null) { LoadingState(); return }
    val cells = listOf(
        Triple(Icons.Rounded.Luggage, counts.trips, "Trips"),
        Triple(Icons.Rounded.PhotoLibrary, counts.photos, "Photos"),
        Triple(Icons.AutoMirrored.Rounded.ReceiptLong, counts.expenses, "Expenses"),
        Triple(Icons.Rounded.AutoStories, counts.diary, "Diary entries"),
        Triple(Icons.Rounded.Description, counts.documents, "Documents"),
        Triple(Icons.Rounded.Place, counts.places, "Places"),
    )
    cells.chunked(3).forEachIndexed { i, row ->
        if (i > 0) Spacer(Modifier.height(Space.lg))
        Row(Modifier.fillMaxWidth()) {
            row.forEach { (icon, n, label) -> CountCell(icon, n, label, Modifier.weight(1f)) }
        }
    }
    Spacer(Modifier.height(Space.lg))
    Hairline()
    Spacer(Modifier.height(Space.xs))
    InfoRow("Itinerary activities", "${counts.activities}")
    InfoRow("Hotels and bookings", "${counts.hotels + counts.bookings}")
    InfoRow("Days off in the holiday planner", "${counts.holidays}")
    InfoRow("Travellers", "${counts.travellers}")
    if (usage != null) InfoRow("Photos and documents", Fmt.bytes(usage.imageBytes + usage.documentBytes))
    Spacer(Modifier.height(Space.sm))
    Text(
        "Every backup is complete: all of the above, plus expense categories, exchange rates, holiday planner names and app settings.",
        style = Travel.type.bodySmall, color = c.inkFaint,
    )
}

@Composable
private fun CountCell(icon: ImageVector, n: Int, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        IconBadge(icon, Travel.colors.accent, size = 32.dp, iconSize = 17.dp)
        Spacer(Modifier.height(Space.sm))
        Text("$n", style = Travel.type.numberSmall, color = Travel.colors.ink, maxLines = 1)
        Text(label, style = Travel.type.bodySmall, color = Travel.colors.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// History -----------------------------------------------------------------------------------------

@Composable
private fun HistoryCard(history: BackupHistory, now: Long) {
    HistoryRow(Icons.Rounded.Cloud, "Google Drive", history.drive, now, never = "Never backed up to Drive")
    Text(
        "Saved in App Backups › Travelogue Backups in your Google Drive. Travelogue can only see the files it created there.",
        style = Travel.type.bodySmall, color = Travel.colors.inkFaint, modifier = Modifier.padding(start = 50.dp),
    )
    Spacer(Modifier.height(Space.md))
    Hairline()
    Spacer(Modifier.height(Space.md))
    HistoryRow(Icons.Rounded.FolderZip, "ZIP file", history.file, now, never = "Never saved as a ZIP")
    Text(
        "Saved wherever you chose. Keep a copy off this phone, such as on a computer, so it survives losing the phone.",
        style = Travel.type.bodySmall, color = Travel.colors.inkFaint, modifier = Modifier.padding(start = 50.dp),
    )
}

@Composable
private fun HistoryRow(icon: ImageVector, title: String, r: BackupRecord?, now: Long, never: String) {
    val c = Travel.colors
    Row(verticalAlignment = Alignment.Top) {
        IconBadge(icon, if (r != null) c.secondary else c.inkFaint, size = 38.dp, iconSize = 19.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = Travel.type.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
                if (r != null) Text(Fmt.bytes(r.bytes), style = Travel.type.label, color = c.ink)
            }
            if (r == null) {
                Text(never, style = Travel.type.bodySmall, color = c.inkFaint)
            } else {
                Text("${Fmt.ago(r.at, now).replaceFirstChar { it.uppercase() }} · ${Fmt.dateTime(r.at)}", style = Travel.type.bodySmall, color = c.inkSoft)
                Text(contentsSummary(r), style = Travel.type.bodySmall, color = c.inkSoft)
                Text(r.fileName, style = Travel.type.bodySmall, color = c.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.missingFiles > 0) {
                    Text(
                        "${r.missingFiles} file(s) were already missing from the phone and aren't in it",
                        style = Travel.type.bodySmall, color = c.gold,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(Space.xs))
}

private fun contentsSummary(r: BackupRecord): String {
    val n = r.contents
    fun part(count: Int, one: String, many: String) = "$count ${if (count == 1) one else many}"
    return listOf(
        part(n.trips, "trip", "trips"),
        part(n.photos, "photo", "photos"),
        part(n.expenses, "expense", "expenses"),
        part(n.diary, "diary entry", "diary entries"),
    ).joinToString(" · ")
}

// Shared pieces -----------------------------------------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Overline(title, Modifier.padding(start = 4.dp, bottom = Space.sm))
        SurfaceCard(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Shapes.md).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, Travel.colors.secondary, size = 38.dp, iconSize = 19.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = Travel.type.titleSmall, color = Travel.colors.ink)
            Text(subtitle, style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Travel.colors.inkFaint)
    }
}
