package com.travelogue.app.ui.memories

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.components.*
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.core.util.Fmt
import com.travelogue.app.data.db.entity.PhotoOwner
import com.travelogue.app.ui.common.appViewModel
import com.travelogue.app.ui.navigation.Nav

@Composable
fun PhotoViewerScreen(nav: Nav, tripId: Long, photoId: Long, favouritesOnly: Boolean) {
    val vm = appViewModel(key = "viewer-$tripId-$photoId-$favouritesOnly") {
        PhotoViewerViewModel(trips, diary, journal, photos, tripId, favouritesOnly)
    }
    val list by vm.photosState.collectAsStateWithLifecycle()
    LightSystemBars()

    val photosList = list
    if (photosList == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    if (photosList.isEmpty()) {
        LaunchedEffect(Unit) { nav.back() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val initial = remember { photosList.indexOfFirst { it.photo.id == photoId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = initial) { photosList.size }
    var chrome by remember { mutableStateOf(true) }
    var editCaption by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val current = photosList[pager.currentPage.coerceIn(0, photosList.lastIndex)]

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { photosList[it].photo.id },
            beyondViewportPageCount = 1,
            pageSpacing = 12.dp,
        ) { page ->
            ZoomableImage(
                relativePath = photosList[page].photo.relativePath,
                isCurrent = pager.currentPage == page,
                onTap = { chrome = !chrome },
            )
        }

        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(Icons.Rounded.Close, nav::back, contentDescription = "Close", tint = Color.White, background = Color.White.copy(alpha = 0.14f))
                Spacer(Modifier.weight(1f))
                Text("${pager.currentPage + 1} / ${photosList.size}", style = Travel.type.label, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.weight(1f))
                CircleIconButton(
                    if (current.photo.isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    { vm.toggleFavourite(current.photo) },
                    contentDescription = if (current.photo.isFavourite) "Remove from favourites" else "Add to favourites",
                    tint = if (current.photo.isFavourite) Color(0xFFFF7A45) else Color.White,
                    background = Color.White.copy(alpha = 0.14f),
                )
                Spacer(Modifier.width(Space.sm))
                OverflowMenu(
                    listOf(
                        MenuItem("Edit caption", Icons.Rounded.EditNote) { editCaption = true },
                        MenuItem("Delete photo", Icons.Rounded.DeleteOutline, destructive = true) { confirmDelete = true },
                    ),
                    background = Color.White.copy(alpha = 0.14f),
                    tint = Color.White,
                )
            }
        }

        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .navigationBarsPadding()
                    .padding(horizontal = Space.gutter, vertical = Space.xl),
            ) {
                val p = current.photo
                if (p.caption.isNotBlank()) {
                    Text(p.caption, style = Travel.type.journal, color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(
                        "Add a caption",
                        style = Travel.type.body, color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.clip(Shapes.pill).clickable { editCaption = true }.padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(Space.sm))
                Text(
                    listOf(Fmt.dateTime(p.takenAt), current.tripName).filter { it.isNotBlank() }.joinToString(" · "),
                    style = Travel.type.bodySmall, color = Color.White.copy(alpha = 0.75f),
                )
                val canOpen = p.ownerId != null && p.ownerType in setOf(PhotoOwner.DIARY, PhotoOwner.HOTEL, PhotoOwner.PLACE)
                Text(
                    "From: ${current.source}" + if (canOpen) "  ›" else "",
                    style = Travel.type.bodySmall, color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp).then(
                        if (canOpen) Modifier.clickable {
                            val id = p.ownerId!!
                            when (p.ownerType) {
                                PhotoOwner.DIARY -> nav.diaryEntry(id)
                                PhotoOwner.HOTEL -> nav.hotel(id)
                                PhotoOwner.PLACE -> nav.place(id)
                                else -> Unit
                            }
                        } else Modifier,
                    ),
                )
            }
        }
    }

    if (editCaption) {
        var text by remember(current.photo.id) { mutableStateOf(current.photo.caption) }
        AlertDialog(
            onDismissRequest = { editCaption = false },
            containerColor = Travel.colors.cardHigh,
            shape = Shapes.lg,
            title = { Text("Caption", style = Travel.type.title, color = Travel.colors.ink) },
            text = {
                TextField(
                    value = text, onValueChange = { text = it.take(500) },
                    placeholder = { Text("What was happening here?") },
                    textStyle = Travel.type.bodyLarge, shape = Shapes.md, colors = fieldColors(), minLines = 2,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.setCaption(current.photo.id, text); editCaption = false }) { Text("Save", color = Travel.colors.accent) }
            },
            dismissButton = { TextButton(onClick = { editCaption = false }) { Text("Cancel", color = Travel.colors.inkSoft) } },
        )
    }
    if (confirmDelete) {
        val id = current.photo.id
        ConfirmDialog(
            title = "Delete this photo?",
            message = "It will be permanently removed from this device and from every place it appears in the app.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(id) },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * Pinch / double-tap zoom with pan. At 1× horizontal drags go to the pager; when zoomed, dragging
 * past the photo's edge swipes to the neighbouring photo (which opens at 1×).
 */
@Composable
private fun ZoomableImage(relativePath: String, isCurrent: Boolean, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(isCurrent) { if (!isCurrent) { scale = 1f; offset = Offset.Zero } }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1) / 2f
        val maxY = size.height * (s - 1) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero }
                        else {
                            val s = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            scale = s
                            offset = clamp((center - tap) * (s - 1), s)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // For a one-finger drag on a zoomed photo we decide, once the drag passes touch
                    // slop, whether it pans the photo or swipes to the next one. It swipes when the
                    // photo is already panned to that edge and the drag is mostly horizontal and
                    // outward. Until then nothing is consumed, so the pager can still take over.
                    var decided = false
                    var passToPager = false
                    var pending = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val fingers = event.changes.count { it.pressed }
                        when {
                            fingers > 1 -> {
                                decided = true; passToPager = false
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = newScale
                                offset = if (newScale <= 1.01f) Offset.Zero else clamp(offset + pan, newScale)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            scale > 1.01f && !passToPager -> {
                                if (!decided) {
                                    pending += pan
                                    if (pending.getDistance() > slop) {
                                        decided = true
                                        val maxX = size.width * (scale - 1) / 2f
                                        val horizontal = kotlin.math.abs(pending.x) > kotlin.math.abs(pending.y) * 1.2f
                                        val atRight = offset.x <= -maxX + 1f // showing the photo's right edge
                                        val atLeft = offset.x >= maxX - 1f   // showing the photo's left edge
                                        passToPager = horizontal && ((pending.x < 0 && atRight) || (pending.x > 0 && atLeft))
                                        if (!passToPager) {
                                            offset = clamp(offset + pending, scale)
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    }
                                } else {
                                    offset = clamp(offset + pan, scale)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        PhotoImage(
            relativePath,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
            contentScale = ContentScale.Fit,
            background = Color.Transparent,
        )
    }
}

/** Light status/navigation bar icons while the black viewer is showing; restores on exit. */
@Composable
private fun LightSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevStatus = controller?.isAppearanceLightStatusBars
        val prevNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (prevStatus != null) controller.isAppearanceLightStatusBars = prevStatus
            if (prevNav != null) controller.isAppearanceLightNavigationBars = prevNav
        }
    }
}
