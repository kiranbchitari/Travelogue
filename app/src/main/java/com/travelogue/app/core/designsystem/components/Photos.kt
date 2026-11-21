package com.travelogue.app.core.designsystem.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.data.db.entity.TripPhotoEntity
import java.io.File
import java.util.UUID
import kotlin.math.abs

/**
 * Loads an image stored in app storage by its relative path. Falls back to generated gradient art
 * (seeded by [fallbackSeed]) when there is no photo, so cards never look empty.
 */
@Composable
fun PhotoImage(
    relativePath: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackSeed: String = "",
    background: Color = Travel.colors.backgroundDeep,
) {
    if (relativePath.isNullOrBlank()) {
        GradientArt(fallbackSeed, modifier)
        return
    }
    val context = LocalContext.current
    val file = remember(relativePath) { File(context.filesDir, relativePath) }
    Box(modifier.background(background)) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(file).crossfade(true).build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** Shows a not-yet-saved picked image (content Uri). */
@Composable
fun UriImage(uri: Uri, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(uri).crossfade(true).build(),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.background(Travel.colors.backgroundDeep),
    )
}

private val ArtPalettes = listOf(
    listOf(Color(0xFFE9A27C), Color(0xFFC5603C), Color(0xFF6B3A2E)),
    listOf(Color(0xFF9CC5C0), Color(0xFF2F5D62), Color(0xFF1C3439)),
    listOf(Color(0xFFF1D08A), Color(0xFFC99A2E), Color(0xFF7A5A1E)),
    listOf(Color(0xFFC9D3B0), Color(0xFF7D8F5E), Color(0xFF3F4A2E)),
    listOf(Color(0xFFDDB7CF), Color(0xFF7A4E6B), Color(0xFF3B2434)),
    listOf(Color(0xFFB7C6D6), Color(0xFF52687D), Color(0xFF26323E)),
)

/** Deterministic, warm sunset illustration used where a photo is missing. */
@Composable
fun GradientArt(seed: String, modifier: Modifier = Modifier) {
    val idx = abs(seed.hashCode()) % ArtPalettes.size
    val p = ArtPalettes[idx]
    val sunX = 0.25f + (abs(seed.hashCode() / 7) % 50) / 100f
    Canvas(modifier.background(Brush.verticalGradient(listOf(p[0], p[1])))) {
        val w = size.width
        val h = size.height
        drawCircle(Color.White.copy(alpha = 0.85f), radius = minOf(w, h) * 0.16f, center = Offset(w * sunX, h * 0.52f))
        drawCircle(Color.White.copy(alpha = 0.18f), radius = minOf(w, h) * 0.26f, center = Offset(w * sunX, h * 0.52f))
        // Layered hills
        val hill1 = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h * 0.68f)
            cubicTo(w * 0.25f, h * 0.56f, w * 0.45f, h * 0.74f, w * 0.7f, h * 0.62f)
            cubicTo(w * 0.85f, h * 0.56f, w * 0.95f, h * 0.6f, w, h * 0.64f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(hill1, p[1].copy(alpha = 0.85f))
        val hill2 = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h * 0.8f)
            cubicTo(w * 0.3f, h * 0.7f, w * 0.6f, h * 0.88f, w, h * 0.76f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(hill2, p[2])
    }
}

/** Dark gradient over the bottom of a photo so white text stays legible. */
@Composable
fun BoxScope.BottomScrim(heightFraction: Float = 0.65f, strength: Float = 0.75f) {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(heightFraction)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = strength)))),
    )
}

// ---------------------------------------------------------------------------------------------
// Pickers (Android Photo Picker, SAF documents, camera). No storage permissions needed.
// ---------------------------------------------------------------------------------------------

/** Returns a function that opens the system Photo Picker for images. */
@Composable
fun rememberImagePicker(multiple: Boolean = true, onPicked: (List<Uri>) -> Unit): () -> Unit {
    val many = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { if (it.isNotEmpty()) onPicked(it) }
    val one = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { u -> onPicked(listOf(u)) } }
    val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    return { if (multiple) many.launch(req) else one.launch(req) }
}

/** Opens the Storage Access Framework picker for PDFs and images (ticket attachments). */
@Composable
fun rememberDocumentPicker(onPicked: (List<Uri>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) onPicked(it) }
    return { launcher.launch(arrayOf("application/pdf", "image/*")) }
}

/**
 * Captures a photo with the installed camera app into a temporary file (no CAMERA permission is
 * declared, so the system camera app handles it). The repositories then copy it into trip storage.
 */
@Composable
fun rememberCameraCapture(onCaptured: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pending
        if (ok && u != null) onCaptured(u)
    }
    return {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        val uri = com.travelogue.app.core.util.Intents.fileUri(context, file)
        pending = uri
        runCatching { launcher.launch(uri) }.onFailure {
            android.widget.Toast.makeText(context, "No camera app available", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Editable horizontal strip of photos for forms: saved photos, newly picked (unsaved) photos, and
 * tiles to add from the gallery or camera.
 */
@Composable
fun PhotoEditorStrip(
    saved: List<TripPhotoEntity>,
    pending: List<Uri>,
    onAddPhotos: (List<Uri>) -> Unit,
    onRemoveSaved: (TripPhotoEntity) -> Unit,
    onRemovePending: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    tileSize: Dp = 96.dp,
    allowCamera: Boolean = true,
) {
    val pick = rememberImagePicker(true, onAddPhotos)
    val camera = rememberCameraCapture { onAddPhotos(listOf(it)) }
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        item { AddTile(Icons.Rounded.AddPhotoAlternate, "Gallery", tileSize, pick) }
        if (allowCamera) item { AddTile(Icons.Rounded.PhotoCamera, "Camera", tileSize, camera) }
        items(saved, key = { "s${it.id}" }) { p ->
            RemovableTile(tileSize, { onRemoveSaved(p) }) { PhotoImage(p.relativePath, Modifier.fillMaxSize()) }
        }
        items(pending, key = { "p$it" }) { u ->
            RemovableTile(tileSize, { onRemovePending(u) }) { UriImage(u, Modifier.fillMaxSize()) }
        }
    }
}

/** Single-photo picker tile (cover, receipt, activity photo). */
@Composable
fun SinglePhotoField(
    label: String,
    savedPath: String?,
    pending: Uri?,
    onPick: (Uri) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    shape: Shape = Shapes.lg,
    allowCamera: Boolean = true,
) {
    val pick = rememberImagePicker(false) { it.firstOrNull()?.let(onPick) }
    val camera = rememberCameraCapture(onPick)
    Column(modifier.fillMaxWidth()) {
        Text(label, style = Travel.type.label, color = Travel.colors.inkSoft, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        val has = pending != null || !savedPath.isNullOrBlank()
        Box(
            Modifier.fillMaxWidth().height(height).clip(shape).background(Travel.colors.card)
                .then(if (!has) Modifier.border(1.5.dp, Travel.colors.line, shape) else Modifier)
                .clickable(onClick = pick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                pending != null -> UriImage(pending, Modifier.fillMaxSize())
                !savedPath.isNullOrBlank() -> PhotoImage(savedPath, Modifier.fillMaxSize())
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Travel.colors.accent, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Tap to choose a photo", style = Travel.type.body, color = Travel.colors.inkSoft)
                }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (allowCamera) CircleIconButton(Icons.Rounded.PhotoCamera, camera, size = 36.dp, iconSize = 18.dp, background = Travel.colors.card.copy(alpha = 0.9f), contentDescription = "Take photo")
                if (has) CircleIconButton(Icons.Rounded.Close, onRemove, size = 36.dp, iconSize = 18.dp, background = Travel.colors.card.copy(alpha = 0.9f), contentDescription = "Remove photo")
            }
        }
    }
}

@Composable
private fun AddTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Dp, onClick: () -> Unit) {
    Column(
        Modifier.size(size).clip(Shapes.md).background(Travel.colors.card).border(1.5.dp, Travel.colors.line, Shapes.md).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Travel.colors.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = Travel.type.bodySmall, color = Travel.colors.inkSoft)
    }
}

@Composable
private fun RemovableTile(size: Dp, onRemove: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(size).clip(Shapes.md)) {
        content()
        Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp)) }
    }
}

/** Tiny ring used for avatar initials (travellers). */
@Composable
fun InitialAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 36.dp, color: Color? = null) {
    val c = color ?: Travel.colors.chart[abs(name.hashCode()) % Travel.colors.chart.size]
    Box(modifier.size(size).clip(CircleShape).background(c.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Text(
            name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" },
            style = Travel.type.label,
            color = c,
        )
    }
}

/** Decorative dotted ring used behind timeline nodes. */
@Composable
fun TimelineNode(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Canvas(modifier.size(size)) {
        drawCircle(color.copy(alpha = 0.25f))
        drawCircle(color, radius = this.size.minDimension / 4)
        drawCircle(color, radius = this.size.minDimension / 2, style = Stroke(width = 1.5f))
    }
}

@Suppress("unused")
private fun Size.aspect() = width / height
