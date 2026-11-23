package com.travelogue.app.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.travelogue.app.core.designsystem.theme.Scenery
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel

/**
 * Standard screen frame for pushed (non-tab) screens: warm background, compact top bar with a round
 * back button and serif title, optional FAB and bottom bar. Handles status/navigation bar insets.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingAction: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.travelBackdrop(),
        containerColor = Color.Transparent,
        contentColor = Travel.colors.ink,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopBar(title = title, subtitle = subtitle, onBack = onBack, actions = actions) },
        floatingActionButton = floatingAction,
        bottomBar = bottomBar,
        snackbarHost = { snackbarHost?.let { SnackbarHost(it) } },
        content = content,
    )
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .travelBackdrop()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Space.lg, vertical = Space.sm)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back")
            Spacer(Modifier.width(Space.md))
        }
        Column(Modifier.weight(1f)) {
            Text(title.voice(), style = Travel.type.title, color = Travel.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Travel.type.bodySmall, color = if (Travel.theme.hasBackdrop) Travel.colors.inkSoft else Travel.colors.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Editorial header for top-level tabs: overline + large serif title + optional trailing content. */
@Composable
fun LargeHeader(
    overline: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, top = Space.lg, bottom = Space.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            // Over scenery the faintest ink is too weak to read.
            Overline(overline, color = if (Travel.theme.hasBackdrop) Travel.colors.inkSoft else Travel.colors.inkFaint)
            Spacer(Modifier.height(6.dp))
            Text(title.voice(), style = Travel.type.display, color = Travel.colors.ink)
        }
        trailing()
    }
}

/** Overflow menu (⋮) with simple text items. */
data class MenuItem(val label: String, val icon: ImageVector? = null, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
fun OverflowMenu(items: List<MenuItem>, modifier: Modifier = Modifier, background: Color = Travel.colors.card, tint: Color = Travel.colors.ink) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        CircleIconButton(Icons.Rounded.MoreVert, { open = true }, contentDescription = "More options", background = background, tint = tint)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Travel.colors.cardHigh, shape = Shapes.md) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, style = Travel.type.body, color = if (item.destructive) Travel.colors.danger else Travel.colors.ink) },
                    leadingIcon = item.icon?.let { { Icon(it, null, tint = if (item.destructive) Travel.colors.danger else Travel.colors.inkSoft) } },
                    onClick = { open = false; item.onClick() },
                )
            }
        }
    }
}

/**
 * Empty state: the icon on a disc, set in a round window onto the theme's scenery (or inside
 * hairline rings in the classic look). Every list in the app uses this rather than a blank screen.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = Travel.colors
    Column(modifier.fillMaxWidth().padding(horizontal = Space.xxxl, vertical = Space.huge), horizontalAlignment = Alignment.CenterHorizontally) {
        if (Travel.theme.scenery == Scenery.RINGS) {
            // Concentric hairline rings around a solid ink disc.
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(136.dp).border(1.dp, c.line.copy(alpha = 0.6f), CircleShape))
                Box(Modifier.size(104.dp).border(1.dp, c.line, CircleShape))
                Box(Modifier.size(72.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = c.onAccent, modifier = Modifier.size(30.dp))
                }
            }
        } else {
            Box(Modifier.size(width = 156.dp, height = 150.dp)) {
                SceneArt(Modifier.align(Alignment.TopCenter).size(140.dp).clip(CircleShape).border(4.dp, c.card, CircleShape))
                Box(
                    Modifier.align(Alignment.BottomEnd).size(60.dp).clip(CircleShape).background(c.background).padding(4.dp).clip(CircleShape).background(c.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = c.onAccent, modifier = Modifier.size(26.dp))
                }
            }
        }
        Spacer(Modifier.height(Space.xxl))
        Text(title.voice(), style = Travel.type.title, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.sm))
        Text(message, style = Travel.type.body, color = c.inkSoft, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.xl))
            PrimaryButton(actionLabel, onAction)
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(Space.huge), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Travel.colors.accent, strokeWidth = 1.5.dp, modifier = Modifier.size(28.dp))
    }
}

/** Confirmation dialog for destructive actions. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Travel.colors.cardHigh,
        shape = Shapes.lg,
        title = { Text(title.voice(), style = Travel.type.title, color = Travel.colors.ink) },
        text = { Text(message, style = Travel.type.body, color = Travel.colors.inkSoft) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, style = Travel.type.label, color = if (destructive) Travel.colors.danger else Travel.colors.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = Travel.type.label, color = Travel.colors.inkSoft) } },
    )
}

/** Bottom sheet with app styling. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelSheet(onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Travel.colors.background,
        shape = Shapes.xl.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp), bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 4.dp).clip(Shapes.pill).background(Travel.colors.line))
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter).padding(bottom = Space.xxl).navigationBarsPadding()) {
            if (title != null) {
                Text(title.voice(), style = Travel.type.title, color = Travel.colors.ink)
                Spacer(Modifier.height(Space.lg))
            }
            content()
        }
    }
}

/** Fades content in when it first appears (lists, cards). */
@Composable
fun FadeIn(visible: Boolean = true, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) { content() }
}

/**
 * For hero-image screens with floating buttons: a paper-coloured strip behind the status bar that
 * fades in once the hero has scrolled away, so the clock and icons stay legible over content.
 */
@Composable
fun BoxScope.ScrollStatusScrim(state: androidx.compose.foundation.lazy.LazyListState, threshold: Int = 1) {
    val visible by remember(state) { derivedStateOf { state.firstVisibleItemIndex >= threshold } }
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (visible) 1f else 0f, label = "scrim")
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Travel.colors.background.copy(alpha = 0.96f * alpha)),
    )
}
