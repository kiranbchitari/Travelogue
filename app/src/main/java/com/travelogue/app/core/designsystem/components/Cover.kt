package com.travelogue.app.core.designsystem.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.domain.CoverFrame
import java.io.File

/** Shape of the trip cards on Home and Trips (width : height); the framing screen edits at this shape. */
const val COVER_CARD_ASPECT = 1.12f

/**
 * A trip cover cropped around its chosen focus point, the same way in every place it appears.
 * Falls back to the generated gradient art when there is no cover.
 */
@Composable
fun CoverImage(
    relativePath: String?,
    frame: CoverFrame,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    fallbackSeed: String = "",
) {
    if (relativePath.isNullOrBlank()) {
        GradientArt(fallbackSeed, modifier)
        return
    }
    val context = LocalContext.current
    val file = remember(relativePath) { File(context.filesDir, relativePath) }
    FramedImage(file, frame, modifier, contentDescription)
}

/**
 * Draws [model] (a File or content Uri) to fill the box, positioned and zoomed by [frame].
 * [onImageSize] reports the loaded image's size, which the framing screen needs to turn drags into
 * focus changes.
 */
@Composable
private fun FramedImage(
    model: Any,
    frame: CoverFrame,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onImageSize: ((Size) -> Unit)? = null,
) {
    val context = LocalContext.current
    // Zoomed in, the default (frame-sized) decode would look soft, so ask for the full-size cover.
    val sharp = frame.zoom > 1.05f
    val request = remember(model, sharp) {
        ImageRequest.Builder(context).data(model).crossfade(true).apply { if (sharp) size(FULL_EDGE) }.build()
    }
    Box(modifier.clipToBounds().background(Travel.colors.backgroundDeep)) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(frame.x * 2 - 1, frame.y * 2 - 1),
            onSuccess = onImageSize?.let { report -> { state -> report(state.painter.intrinsicSize) } },
            modifier = Modifier.matchParentSize().graphicsLayer {
                scaleX = frame.zoom
                scaleY = frame.zoom
                transformOrigin = TransformOrigin(frame.x, frame.y)
            },
        )
    }
}

private const val FULL_EDGE = 2560

/**
 * Cover picker for the trip editor. Shows the photo at the shape of a trip card, framed as it will
 * appear, with buttons to frame it, take a photo or remove it.
 */
@Composable
fun CoverPhotoField(
    savedPath: String?,
    pending: Uri?,
    frame: CoverFrame,
    onPick: (Uri) -> Unit,
    onRemove: () -> Unit,
    onFrame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pick = rememberImagePicker(false) { it.firstOrNull()?.let(onPick) }
    val camera = rememberCameraCapture(onPick)
    val context = LocalContext.current
    val model: Any? = pending ?: savedPath?.takeIf { it.isNotBlank() }?.let { File(context.filesDir, it) }
    Column(modifier.fillMaxWidth()) {
        Text("Cover photograph", style = Travel.type.label, color = Travel.colors.inkSoft, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Box(
            Modifier.fillMaxWidth().aspectRatio(COVER_CARD_ASPECT).clip(Shapes.lg).background(Travel.colors.card)
                .then(if (model == null) Modifier.border(1.dp, Travel.colors.line, Shapes.lg) else Modifier)
                .clickable(onClick = if (model == null) pick else onFrame),
            contentAlignment = Alignment.Center,
        ) {
            if (model != null) {
                FramedImage(model, frame, Modifier.fillMaxSize())
                Row(
                    Modifier.align(Alignment.BottomStart).padding(10.dp).clip(Shapes.pill)
                        .background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onFrame)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Crop, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Choose what shows", style = Travel.type.label, color = Color.White)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Travel.colors.inkSoft, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Tap to choose a photo", style = Travel.type.body, color = Travel.colors.inkSoft)
                }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val bg = Travel.colors.card.copy(alpha = 0.9f)
                if (model != null) CircleIconButton(Icons.Rounded.AddPhotoAlternate, pick, size = 36.dp, iconSize = 18.dp, background = bg, contentDescription = "Choose another photo")
                CircleIconButton(Icons.Rounded.PhotoCamera, camera, size = 36.dp, iconSize = 18.dp, background = bg, contentDescription = "Take photo")
                if (model != null) CircleIconButton(Icons.Rounded.Close, onRemove, size = 36.dp, iconSize = 18.dp, background = bg, contentDescription = "Remove photo")
            }
        }
    }
}

/**
 * Full-screen "frame your cover" step: drag the photo to choose what shows and pinch (or use the
 * slider) to zoom. The big frame is a trip card with the trip's name where the card puts it; below
 * it, the same framing at the other shapes the cover appears in.
 */
@Composable
fun CoverFramerDialog(
    model: Any,
    tripName: String,
    initial: CoverFrame,
    onDone: (CoverFrame) -> Unit,
    onDismiss: () -> Unit,
) {
    var frame by remember { mutableStateOf(initial) }
    var imageSize by remember { mutableStateOf(Size.Zero) }
    var touching by remember { mutableStateOf(false) }
    val c = Travel.colors

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // The dialog has its own window: give it status bar icons that read on this background.
        val view = LocalView.current
        val darkIcons = !c.isDark
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { w ->
                WindowCompat.getInsetsController(w, view).apply {
                    isAppearanceLightStatusBars = darkIcons
                    isAppearanceLightNavigationBars = darkIcons
                }
            }
        }
        Column(Modifier.fillMaxSize().background(c.background).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = c.inkSoft) }
                Text(
                    "Frame your cover", style = Travel.type.title, color = c.ink,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                TextButton(onClick = { onDone(frame) }) { Text("Done", color = c.accent, style = Travel.type.label) }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter),
            ) {
                Text(
                    "Drag the photo to choose what shows. Pinch or use the slider to zoom in on part of it.",
                    style = Travel.type.bodySmall, color = c.inkFaint,
                )
                Spacer(Modifier.height(Space.md))

                Box(
                    Modifier.fillMaxWidth().aspectRatio(COVER_CARD_ASPECT).clip(Shapes.lg)
                        .semantics { contentDescription = "Cover frame. Drag to move the photo, pinch to zoom." }
                        .pointerInput(Unit) {
                            // Only to show the guide grid while a finger is down.
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                touching = true
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                } while (event.changes.any { it.pressed })
                                touching = false
                            }
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                frame = frame.zoomedBy(zoom).dragged(pan.x, pan.y, w, h, imageSize.width, imageSize.height)
                            }
                        },
                ) {
                    FramedImage(model, frame, Modifier.fillMaxSize(), onImageSize = { if (it.isUsable()) imageSize = it })
                    BottomScrim(0.8f, 0.88f)
                    Text(
                        tripName.ifBlank { "Your trip" }, style = Travel.type.headline, color = Color.White, maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(Space.xl),
                    )
                    if (touching) ThirdsGrid(Modifier.matchParentSize())
                }

                Spacer(Modifier.height(Space.lg))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ZoomOut, null, tint = c.inkFaint, modifier = Modifier.size(20.dp))
                    Slider(
                        value = frame.zoom,
                        onValueChange = { frame = frame.withZoom(it) },
                        valueRange = 1f..CoverFrame.MAX_ZOOM,
                        colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line),
                        modifier = Modifier.weight(1f).padding(horizontal = Space.sm).semantics { contentDescription = "Zoom" },
                    )
                    Icon(Icons.Rounded.ZoomIn, null, tint = c.inkFaint, modifier = Modifier.size(20.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { frame = CoverFrame.Center }, enabled = !frame.isCentered) {
                        Text("Reset to centre", color = if (frame.isCentered) c.inkFaint else c.accent)
                    }
                }

                Spacer(Modifier.height(Space.md))
                Overline("Also shown as")
                Spacer(Modifier.height(Space.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.Bottom) {
                    // Weight/width go on the preview itself (a Row child); the shape goes on its image.
                    FramePreview("Trip page", model, frame, aspect = 1.05f, modifier = Modifier.weight(1.1f))
                    FramePreview("Trips list", model, frame, aspect = 1.35f, modifier = Modifier.weight(1.4f))
                    FramePreview("Recent", model, frame, aspect = 68f / 84f, modifier = Modifier.width(56.dp))
                }
                Spacer(Modifier.height(Space.xxl))
            }
        }
    }
}

@Composable
private fun FramePreview(label: String, model: Any, frame: CoverFrame, aspect: Float, modifier: Modifier) {
    Column(modifier) {
        FramedImage(model, frame, Modifier.fillMaxWidth().aspectRatio(aspect).clip(Shapes.sm))
        Spacer(Modifier.height(4.dp))
        Text(label, style = Travel.type.bodySmall, color = Travel.colors.inkFaint, maxLines = 1)
    }
}

/** Rule-of-thirds guide drawn over the frame while the user is moving the photo. */
@Composable
private fun ThirdsGrid(modifier: Modifier) {
    Canvas(modifier) {
        val line = Color.White.copy(alpha = 0.55f)
        for (i in 1..2) {
            val x = size.width * i / 3
            val y = size.height * i / 3
            drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
    }
}

private fun Size.isUsable() = width.isFinite() && height.isFinite() && width > 0f && height > 0f
