package com.travelogue.app.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.IconBadge
import com.travelogue.app.core.designsystem.components.Overline
import com.travelogue.app.core.designsystem.components.ProgressBar
import com.travelogue.app.core.designsystem.components.Tag
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.backup.BackupException
import com.travelogue.app.data.backup.BackupManager
import com.travelogue.app.data.backup.BackupType
import com.travelogue.app.data.backup.DuplicateStrategy
import com.travelogue.app.di.AppContainer
import com.travelogue.app.ui.common.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Working(val title: String, val fraction: Float, val message: String) : BackupUiState
    data class Preview(val session: BackupManager.ImportSession) : BackupUiState
    data class Done(val title: String, val message: String) : BackupUiState
    data class Failed(val message: String) : BackupUiState
}

/** App-wide export/import runner. Work runs in the application scope so it survives navigation. */
object BackupController {
    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state

    fun dismiss() {
        (state.value as? BackupUiState.Preview)?.let { /* session discarded by caller */ }
        _state.value = BackupUiState.Idle
    }

    fun exportTrip(c: AppContainer, tripId: Long, uri: Uri) = c.appScope.launch {
        run("Exporting trip") {
            val r = c.backup.exportTrip(tripId, uri) { f, m -> _state.value = BackupUiState.Working("Exporting trip", f, m) }
            val extra = if (r.missingFiles > 0) "\n${r.missingFiles} missing file(s) were skipped." else ""
            BackupUiState.Done("Trip exported", "Saved the trip with ${if (r.files == 1) "1 file" else "${r.files} files"} (${Fmt.bytes(r.bytes)}).$extra")
        }
    }

    fun exportAll(c: AppContainer, uri: Uri) = c.appScope.launch {
        run("Creating backup") {
            val r = c.backup.exportAll(uri) { f, m -> _state.value = BackupUiState.Working("Creating backup", f, m) }
            BackupUiState.Done("Backup created", "${if (r.trips == 1) "1 trip" else "${r.trips} trips"} and ${if (r.files == 1) "1 file" else "${r.files} files"} (${Fmt.bytes(r.bytes)}) saved.")
        }
    }

    fun inspect(c: AppContainer, uri: Uri) = c.appScope.launch {
        run("Checking archive") {
            val session = c.backup.inspect(uri) { f, m -> _state.value = BackupUiState.Working("Checking archive", f, m) }
            BackupUiState.Preview(session)
        }
    }

    fun import(c: AppContainer, session: BackupManager.ImportSession, strategy: DuplicateStrategy) = c.appScope.launch {
        run("Importing") {
            val r = c.backup.import(session, strategy) { f, m -> _state.value = BackupUiState.Working("Importing", f, m) }
            val parts = buildList {
                add(if (r.imported == 1) "1 trip restored." else "${r.imported} trips restored.")
                if (r.skipped > 0) add("${r.skipped} already on this device were skipped.")
                if (r.warnings.isNotEmpty()) add("\n" + r.warnings.take(6).joinToString("\n"))
            }
            BackupUiState.Done("Import complete", parts.joinToString(" "))
        }
    }

    fun cancelPreview(c: AppContainer, session: BackupManager.ImportSession) {
        c.backup.discard(session)
        _state.value = BackupUiState.Idle
    }

    private suspend fun run(title: String, block: suspend () -> BackupUiState) {
        _state.value = BackupUiState.Working(title, 0f, "Starting…")
        _state.value = try {
            block()
        } catch (e: BackupException) {
            BackupUiState.Failed(e.message ?: "Something went wrong.")
        } catch (e: Exception) {
            BackupUiState.Failed(e.message ?: "Something went wrong.")
        }
    }
}

/**
 * Returns a function that asks the user where to save a trip ZIP (Storage Access Framework) and
 * exports the trip there, showing progress. Call from a menu item: `export(trip.id, trip.name)`.
 */
@Composable
fun rememberTripExporter(): (tripId: Long, tripName: String) -> Unit {
    val c = appContainer()
    var pendingTrip by rememberSaveable { mutableLongStateOf(0L) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && pendingTrip != 0L) BackupController.exportTrip(c, pendingTrip, uri)
    }
    return { id, name ->
        pendingTrip = id
        launcher.launch(BackupManager.suggestedFileName(name))
    }
}

/** Asks where to save a full backup ZIP and writes it. */
@Composable
fun rememberFullBackupExporter(): () -> Unit {
    val c = appContainer()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) BackupController.exportAll(c, uri)
    }
    return { launcher.launch(BackupManager.backupFileName()) }
}

/**
 * Returns a function that opens the SAF picker for a Travelogue ZIP (single trip or full backup) and
 * imports it with validation, duplicate handling and a progress dialog.
 */
@Composable
fun rememberBackupImporter(): () -> Unit {
    val c = appContainer()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) BackupController.inspect(c, uri)
    }
    return { launcher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
}

/** Dialogs for export/import progress, import preview and results. Placed once in AppRoot. */
@Composable
fun BackupHost() {
    val c = appContainer()
    val state by BackupController.state.collectAsStateWithLifecycle()
    when (val s = state) {
        BackupUiState.Idle -> Unit
        is BackupUiState.Working -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            containerColor = Travel.colors.cardHigh,
            shape = Shapes.lg,
            title = { Text(s.title, style = Travel.type.title, color = Travel.colors.ink) },
            text = {
                Column {
                    ProgressBar(s.fraction)
                    Spacer(Modifier.height(Space.md))
                    Text(s.message, style = Travel.type.body, color = Travel.colors.inkSoft)
                }
            },
            confirmButton = {},
        )
        is BackupUiState.Preview -> ImportPreviewDialog(
            s.session,
            onImport = { BackupController.import(c, s.session, it) },
            onCancel = { BackupController.cancelPreview(c, s.session) },
        )
        is BackupUiState.Done -> ResultDialog(Icons.Rounded.CheckCircle, Travel.colors.success, s.title, s.message)
        is BackupUiState.Failed -> ResultDialog(Icons.Rounded.ErrorOutline, Travel.colors.danger, "Couldn't complete", s.message)
    }
}

@Composable
private fun ResultDialog(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, message: String) {
    AlertDialog(
        onDismissRequest = BackupController::dismiss,
        containerColor = Travel.colors.cardHigh,
        shape = Shapes.lg,
        icon = { IconBadge(icon, tint, size = 52.dp, iconSize = 28.dp) },
        title = { Text(title, style = Travel.type.title, color = Travel.colors.ink) },
        text = { Text(message, style = Travel.type.body, color = Travel.colors.inkSoft) },
        confirmButton = { TextButton(onClick = BackupController::dismiss) { Text("OK", color = Travel.colors.accent) } },
    )
}

@Composable
private fun ImportPreviewDialog(session: BackupManager.ImportSession, onImport: (DuplicateStrategy) -> Unit, onCancel: () -> Unit) {
    val p = session.preview
    val dupes = p.duplicates
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Travel.colors.cardHigh,
        shape = Shapes.lg,
        icon = { IconBadge(Icons.Rounded.Inventory2, Travel.colors.accent, size = 52.dp, iconSize = 26.dp) },
        title = {
            Text(
                if (p.type == BackupType.FULL) "Restore backup" else "Import trip",
                style = Travel.type.title, color = Travel.colors.ink,
            )
        },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "${p.trips.size} trip(s) · ${p.totalFiles} files · ${Fmt.bytes(p.totalBytes)}",
                    style = Travel.type.bodySmall, color = Travel.colors.inkFaint,
                )
                Spacer(Modifier.height(Space.md))
                p.trips.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = Travel.type.titleSmall, color = Travel.colors.ink)
                            Text(t.dates, style = Travel.type.bodySmall, color = Travel.colors.inkSoft)
                            Text(t.counts, style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
                        }
                        if (t.alreadyExists) Tag("On device", color = Travel.colors.gold)
                    }
                }
                if (dupes.isNotEmpty()) {
                    Spacer(Modifier.height(Space.md))
                    Overline("Already imported")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${dupes.size} trip(s) already exist on this device. Replace them with the archive version, keep both copies, or skip them.",
                        style = Travel.type.body, color = Travel.colors.inkSoft,
                    )
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (dupes.isEmpty()) {
                    TextButton(onClick = { onImport(DuplicateStrategy.SKIP) }) { Text("Import", color = Travel.colors.accent, style = Travel.type.label) }
                } else {
                    TextButton(onClick = { onImport(DuplicateStrategy.REPLACE) }) { Text("Replace existing", color = Travel.colors.accent, style = Travel.type.label) }
                    TextButton(onClick = { onImport(DuplicateStrategy.KEEP_BOTH) }) { Text("Keep both", color = Travel.colors.ink, style = Travel.type.label) }
                    if (dupes.size < p.trips.size) {
                        TextButton(onClick = { onImport(DuplicateStrategy.SKIP) }) { Text("Import new only", color = Travel.colors.ink, style = Travel.type.label) }
                    }
                }
                TextButton(onClick = onCancel) { Text("Cancel", color = Travel.colors.inkSoft, style = Travel.type.label) }
            }
        },
    )
}
