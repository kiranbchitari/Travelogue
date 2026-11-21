package com.travelogue.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Travel

/** Filled pill button in the accent color with a subtle warm gradient. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = Travel.colors
    val brush = Brush.horizontalGradient(listOf(c.accent, lerpColor(c.accent, c.gold, 0.25f)))
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .pressable(if (enabled && !loading) onClick else null)
            .shadow(if (c.isDark) 0.dp else 10.dp, Shapes.pill, spotColor = c.accent.copy(alpha = 0.4f), ambientColor = c.accent.copy(alpha = 0.2f))
            .clip(Shapes.pill)
            .background(brush)
            .heightIn(min = 52.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(color = c.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = c.onAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Travel.type.titleSmall, color = c.onAccent)
    }
}

/** Quiet button: ink text on a soft surface. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = Travel.colors.ink,
    background: Color = Travel.colors.backgroundDeep,
) {
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .pressable(if (enabled) onClick else null)
            .clip(Shapes.pill)
            .background(background)
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Travel.type.label, color = color)
    }
}

/** Round icon button. Used for back, overflow and quick actions. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = Travel.colors.ink,
    background: Color = Travel.colors.card,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Labelled round action (Call / SMS / WhatsApp / Copy). */
@Composable
fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Travel.colors.accent,
) {
    Column(modifier.clip(Shapes.md).clickable(onClick = onClick).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, tint, size = 44.dp, iconSize = 20.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, style = Travel.type.bodySmall, color = Travel.colors.inkSoft)
    }
}

/** Floating action button: accent circle with a soft glow. */
@Composable
fun AccentFab(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String? = null, label: String? = null) {
    val c = Travel.colors
    Row(
        modifier
            .pressable(onClick)
            .shadow(14.dp, Shapes.pill, spotColor = c.accent.copy(alpha = 0.5f))
            .clip(Shapes.pill)
            .background(Brush.linearGradient(listOf(c.accent, lerpColor(c.accent, c.gold, 0.3f))))
            .height(56.dp)
            .padding(horizontal = if (label == null) 16.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription, tint = c.onAccent, modifier = Modifier.size(24.dp))
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(label, style = Travel.type.titleSmall, color = c.onAccent)
        }
    }
}

fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t)
