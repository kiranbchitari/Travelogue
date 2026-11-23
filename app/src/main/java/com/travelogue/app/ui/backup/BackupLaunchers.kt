package com.travelogue.app.ui.backup

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.travelogue.app.data.backup.DriveClient
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.WarningAmber
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
import com.travelogue.app.data.backup.ImportResult
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
    data class DrivePicker(val token: String, val files: List<DriveClient.DriveFile>) : BackupUiState
    /** Some trips (or the settings) didn't restore; the rest did. [strategy] is reused for a retry. */
    data class PartialImport(val result: ImportResult, val strategy: DuplicateStrategy) : BackupUiState
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
            val holidays = if (r.holidays > 0) ", ${daysOff(r.holidays)}" else ""
            BackupUiState.Done("Backup created", "${if (r.trips == 1) "1 trip" else "${r.trips} trips"}$holidays and ${if (r.files == 1) "1 file" else "${r.files} files"} (${Fmt.bytes(r.bytes)}) saved.")
        }
    }

    fun inspect(c: AppContainer, uri: Uri) = c.appScope.launch {
        run("Checking archive") {
            val session = c.backup.inspect(uri) { f, m -> _state.value = BackupUiState.Working("Checking archive", f, m) }
            BackupUiState.Preview(session)
        }
    }

    fun import(c: AppContainer, session: BackupManager.ImportSession, strategy: DuplicateStrategy) {
        // Claimed here, on the tap, not when the coroutine starts: a second tap on Import or Retry
        // must not start another import of the same (by then deleted) archive.
        if (_state.value is BackupUiState.Working) return
        _state.value = BackupUiState.Working("Importing", 0f, "Starting…")
        c.appScope.launch { runImport(c, session, strategy) }
    }

    private suspend fun runImport(c: AppContainer, session: BackupManager.ImportSession, strategy: DuplicateStrategy) {
        run("Importing") {
            val r = c.backup.import(session, strategy) { f, m -> _state.value = BackupUiState.Working("Importing", f, m) }
            if (r.isPartial) return@run BackupUiState.PartialImport(r, strategy)
            val parts = buildList {
                // A retry of settings alone has no trips to count.
                if (session.archives.isNotEmpty()) add(if (r.imported == 1) "1 trip restored." else "${r.imported} trips restored.")
                else add("Settings restored.")
                if (r.skipped > 0) add("${r.skipped} already on this device were skipped.")
                if (r.holidays > 0) add("${daysOff(r.holidays)} added to the holiday planner.")
                if (r.warnings.isNotEmpty()) add("\n" + r.warnings.take(6).joinToString("\n"))
            }
            BackupUiState.Done("Import complete", parts.joinToString(" "))
        }
    }

    /** Closes the partial-restore report, dropping the archive kept for a retry. */
    fun closePartial(c: AppContainer, result: ImportResult) {
        result.retry?.let(c.backup::discard)
        _state.value = BackupUiState.Idle
    }

    fun cancelPreview(c: AppContainer, session: BackupManager.ImportSession) {
        c.backup.discard(session)
        _state.value = BackupUiState.Idle
    }

    /** Shows a spinner while Google authorization is in flight. */
    fun connecting() { _state.value = BackupUiState.Working("Google Drive", 0f, "Connecting to your Google account…") }

    fun failed(message: String) { _state.value = BackupUiState.Failed(message) }

    /** Writes a full backup to app cache, then uploads it to the "Travelogue Backups" Drive folder. */
    fun driveBackup(c: AppContainer, context: Context, token: String) = c.appScope.launch {
        run("Backing up to Drive") {
            val name = BackupManager.backupFileName().removeSuffix(".zip") + "_" + LocalTime.now().format(DateTimeFormatter.ofPattern("HHmm")) + ".zip"
            val temp = File(context.cacheDir, "drive-${UUID.randomUUID()}.zip")
            try {
                val r = c.backup.exportAll(Uri.fromFile(temp)) { f, m -> _state.value = BackupUiState.Working("Backing up to Drive", f * 0.4f, m) }
                DriveClient(token).upload(temp, name) { f ->
                    _state.value = BackupUiState.Working("Backing up to Drive", 0.4f + f * 0.6f, "Uploading ${Fmt.bytes(temp.length())}")
                }
                val holidays = if (r.holidays > 0) ", ${daysOff(r.holidays)}" else ""
                BackupUiState.Done(
                    "Saved to Google Drive",
                    "${if (r.trips == 1) "1 trip" else "${r.trips} trips"}$holidays and ${if (r.files == 1) "1 file" else "${r.files} files"} " +
                        "(${Fmt.bytes(temp.length())}) uploaded to “App Backups › Travelogue Backups” as $name.",
                )
            } finally {
                temp.delete()
            }
        }
    }

    /** Lists the backups in Drive so the user can pick one to restore. */
    fun driveList(c: AppContainer, token: String) = c.appScope.launch {
        run("Google Drive") {
            _state.value = BackupUiState.Working("Google Drive", 0.2f, "Looking for backups…")
            val files = DriveClient(token).list()
            if (files.isEmpty()) BackupUiState.Done("No backups yet", "There are no Travelogue backups in your Google Drive. Use “Back up to Google Drive” first.")
            else BackupUiState.DrivePicker(token, files)
        }
    }

    /** Downloads the chosen backup and hands it to the normal import preview. */
    fun driveRestore(c: AppContainer, context: Context, token: String, file: DriveClient.DriveFile) = c.appScope.launch {
        run("Downloading backup") {
            val temp = File(context.cacheDir, "drive-${UUID.randomUUID()}.zip")
            try {
                DriveClient(token).download(file, temp) { f ->
                    _state.value = BackupUiState.Working("Downloading backup", f * 0.8f, "${file.name} · ${Fmt.bytes(file.size)}")
                }
                val session = c.backup.inspect(Uri.fromFile(temp)) { f, m -> _state.value = BackupUiState.Working("Checking archive", 0.8f + f * 0.2f, m) }
                BackupUiState.Preview(session)
            } finally {
                temp.delete()
            }
        }
    }

    private fun daysOff(n: Int) = if (n == 1) "1 day off" else "$n days off"

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
    val context = LocalContext.current
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
        is BackupUiState.DrivePicker -> DrivePickerDialog(s.files) { BackupController.driveRestore(c, context, s.token, it) }
        is BackupUiState.PartialImport -> PartialImportDialog(
            s.result,
            onRetry = s.result.retry?.let { retry -> { BackupController.import(c, retry, s.strategy) } },
            onClose = { BackupController.closePartial(c, s.result) },
        )
    }
}

/**
 * Google Drive backup and restore. Each action asks for the `drive.file` scope through the Google
 * Identity authorization API (showing Google's consent sheet the first time), then runs.
 */
class DriveActions(val backup: () -> Unit, val restore: () -> Unit)

@Composable
fun rememberDriveActions(): DriveActions {
    val c = appContainer()
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    fun proceed(op: String?, token: String) {
        when (op) {
            OP_BACKUP -> BackupController.driveBackup(c, context.applicationContext, token)
            OP_RESTORE -> BackupController.driveList(c, token)
            else -> BackupController.dismiss()
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val op = pending
        pending = null
        if (res.resultCode != Activity.RESULT_OK) { BackupController.dismiss(); return@rememberLauncherForActivityResult }
        val token = runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data).accessToken }.getOrNull()
        if (token == null) BackupController.failed("Google didn't grant access to Drive.") else proceed(op, token)
    }

    fun start(op: String) {
        pending = op
        BackupController.connecting()
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveClient.SCOPE))).build()
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { result ->
                val intent = result.pendingIntent
                if (result.hasResolution() && intent != null) {
                    launcher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                } else {
                    pending = null
                    result.accessToken?.let { proceed(op, it) } ?: BackupController.failed("Google didn't grant access to Drive.")
                }
            }
            .addOnFailureListener { e ->
                pending = null
                BackupController.failed("Couldn't connect to Google: ${e.message ?: e.javaClass.simpleName}")
            }
    }

    return remember { DriveActions(backup = { start(OP_BACKUP) }, restore = { start(OP_RESTORE) }) }
}

private const val OP_BACKUP = "backup"
private const val OP_RESTORE = "restore"

@Composable
private fun DrivePickerDialog(files: List<DriveClient.DriveFile>, onPick: (DriveClient.DriveFile) -> Unit) {
    AlertDialog(
        onDismissRequest = BackupController::dismiss,
        containerColor = Travel.colors.cardHigh,
        shape = Shapes.lg,
        icon = { IconBadge(Icons.Rounded.Restore, Travel.colors.accent, size = 52.dp, iconSize = 26.dp) },
        title = { Text("Restore from Drive", style = Travel.type.title, color = Travel.colors.ink) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text("Choose a backup. You'll see what's inside before anything is imported.", style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
                Spacer(Modifier.height(Space.md))
                files.forEach { f ->
                    Column(Modifier.fillMaxWidth().clip(Shapes.md).clickable { onPick(f) }.padding(vertical = 8.dp)) {
                        Text(f.name, style = Travel.type.titleSmall, color = Travel.colors.ink)
                        Text(driveDate(f.createdTime) + " · " + Fmt.bytes(f.size), style = Travel.type.bodySmall, color = Travel.colors.inkSoft)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = BackupController::dismiss) { Text("Cancel", color = Travel.colors.inkSoft) } },
    )
}

private fun driveDate(iso: String): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
}.getOrDefault(iso)

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
private fun PartialImportDialog(r: ImportResult, onRetry: (() -> Unit)?, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Travel.colors.cardHigh,
        shape = Shapes.lg,
        icon = { IconBadge(Icons.Rounded.WarningAmber, Travel.colors.gold, size = 52.dp, iconSize = 28.dp) },
        title = { Text("Restore not finished", style = Travel.type.title, color = Travel.colors.ink) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                val done = buildList {
                    add(if (r.imported == 1) "1 trip restored." else "${r.imported} trips restored.")
                    if (r.skipped > 0) add("${r.skipped} already on this device were skipped.")
                    if (r.holidays > 0) add(if (r.holidays == 1) "1 day off added to the holiday planner." else "${r.holidays} days off added to the holiday planner.")
                }
                Text(done.joinToString(" "), style = Travel.type.body, color = Travel.colors.inkSoft)
                if (r.failed.isNotEmpty()) {
                    Spacer(Modifier.height(Space.md))
                    Overline(if (r.failed.size == 1) "1 trip not restored" else "${r.failed.size} trips not restored")
                    r.failed.forEach { f ->
                        Spacer(Modifier.height(Space.sm))
                        Text(f.name, style = Travel.type.titleSmall, color = Travel.colors.ink)
                        Text(f.reason, style = Travel.type.bodySmall, color = Travel.colors.danger)
                    }
                }
                r.settingsError?.let {
                    Spacer(Modifier.height(Space.md))
                    Overline("Settings")
                    Text("Some settings may not have been restored. $it", style = Travel.type.bodySmall, color = Travel.colors.danger)
                }
                if (r.warnings.isNotEmpty()) {
                    Spacer(Modifier.height(Space.md))
                    Text(r.warnings.take(6).joinToString("\n"), style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
                }
                Spacer(Modifier.height(Space.md))
                Text(
                    "Everything listed as restored is complete. Retry tries only what failed.",
                    style = Travel.type.bodySmall, color = Travel.colors.inkFaint,
                )
            }
        },
        confirmButton = {
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry failed", color = Travel.colors.accent) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close", color = Travel.colors.inkSoft) } },
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
                    "${p.trips.size} trip(s) · ${p.totalFiles} files · ${Fmt.bytes(p.totalBytes)}" +
                        if (p.holidays > 0) " · ${p.holidays} holiday planner day(s)" else "",
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
