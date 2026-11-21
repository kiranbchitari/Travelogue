package com.travelogue.app.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel

/** Press feedback: a gentle scale-down used by every tappable card. */
@Composable
fun Modifier.pressable(onClick: (() -> Unit)?, enabled: Boolean = true): Modifier {
    if (onClick == null) return this
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.975f else 1f, label = "press")
    return this
        .scale(s)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/**
 * House voice for interface copy: headings, section titles and buttons are set in lowercase.
 * User-entered text (trip names, places, notes) never goes through this. Return [this] unchanged
 * to switch the whole app back to sentence case.
 */
fun String.voice(): String = lowercase()

/**
 * The single card style of the app: crisp flat surface, tight radius, hairline border and a barely
 * there shadow in light mode. Avoid nesting these.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = Shapes.lg,
    color: Color = Travel.colors.card,
    elevation: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(Space.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Travel.colors
    Column(
        modifier
            .pressable(onClick)
            .shadow(if (c.isDark) 0.dp else elevation * 8, shape, ambientColor = Color(0x0A000000), spotColor = Color(0x12000000))
            .clip(shape)
            .background(color)
            .border(1.dp, c.line, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** Small rounded-square icon chip, used for category icons, list leading icons, etc. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    background: Color = tint.copy(alpha = 0.14f),
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) {
    Box(modifier.size(size).clip(Shapes.md).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Section heading with optional trailing action ("See all"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title.voice(), style = Travel.type.title, color = Travel.colors.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
            }
        }
        if (actionLabel != null && onAction != null) {
            Row(
                Modifier.clip(Shapes.pill).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(actionLabel.uppercase(), style = Travel.type.overline, color = Travel.colors.ink)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Travel.colors.ink, modifier = Modifier.size(13.dp))
            }
        }
    }
}

/** Overline label: small, tracked, uppercase. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = Travel.colors.inkFaint) {
    Text(text.uppercase(), style = Travel.type.overline, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Key figure with a label underneath. Used in stat rows. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Travel.colors.accent,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    SurfaceCard(modifier, onClick = onClick, contentPadding = PaddingValues(Space.lg)) {
        if (icon != null) {
            IconBadge(icon, tint, size = 34.dp, iconSize = 18.dp)
            Spacer(Modifier.height(Space.md))
        }
        Text(value, style = Travel.type.numberSmall.copy(fontSize = 22.sp, lineHeight = 26.sp), color = Travel.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Overline(label, color = Travel.colors.inkSoft)
        if (caption != null) Text(caption, style = Travel.type.bodySmall, color = Travel.colors.inkFaint, maxLines = 1)
    }
}

/** Label/value line used in detail screens. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    valueColor: Color = Travel.colors.ink,
) {
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(Shapes.sm).clickable(onClick = onClick) else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Travel.colors.inkFaint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.md))
        }
        Text(label, style = Travel.type.body, color = Travel.colors.inkSoft, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Space.md))
        Text(value, style = Travel.type.label, color = valueColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Hairline divider in the theme line color. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Travel.colors.line))
}

/** Small squared-off status label, set in tracked capitals. */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Travel.colors.accent,
    background: Color = color.copy(alpha = 0.12f),
    icon: ImageVector? = null,
) {
    Row(
        modifier.clip(Shapes.xs).background(background).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text.uppercase(), style = Travel.type.overline.copy(letterSpacing = 0.1.em), color = color, maxLines = 1)
    }
}

/** Selectable pill, used for filters and choice rows. */
@Composable
fun ChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leadingDot: Color? = null,
) {
    val c = Travel.colors
    val bg by animateColorAsState(if (selected) c.ink else c.card, label = "pillBg")
    val fg by animateColorAsState(if (selected) c.onAccent else c.inkSoft, label = "pillFg")
    val edge by animateColorAsState(if (selected) c.ink else c.line, label = "pillEdge")
    Row(
        modifier
            .clip(Shapes.pill)
            .background(bg)
            .border(1.dp, edge, Shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingDot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(leadingDot))
            Spacer(Modifier.width(6.dp))
        }
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Travel.type.label, color = fg, maxLines = 1)
    }
}

/** Horizontally scrolling row of [ChoicePill]s. */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Space.gutter),
    dot: ((T) -> Color?)? = null,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        items(options.size) { i ->
            val o = options[i]
            ChoicePill(label(o), o == selected, { onSelect(o) }, leadingDot = dot?.invoke(o))
        }
    }
}

/** Thin rounded progress bar (budget usage etc). Turns danger color when over 100%. */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Travel.colors.accent,
    track: Color = Travel.colors.line,
    height: Dp = 6.dp,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "progress")
    val barColor = if (progress > 1f) Travel.colors.danger else color
    Box(modifier.fillMaxWidth().height(height).clip(Shapes.pill).background(track)) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(Shapes.pill).background(barColor))
    }
}

/**
 * A headline amount: the currency symbol and any decimals are dimmed so the whole number carries
 * the weight. [text] is an already formatted amount such as "₹5,46,931.50" or "₹2.4L".
 */
@Composable
fun MoneyText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = Travel.type.number,
    color: Color = Travel.colors.ink,
    dim: Color = Travel.colors.inkFaint,
) {
    val firstDigit = text.indexOfFirst { it.isDigit() }.coerceAtLeast(0)
    val lastSep = text.lastIndexOfAny(charArrayOf('.', ','))
    // Only a separator followed by exactly two digits at the very end is a decimal part.
    val decimals = if (lastSep >= 0 && text.length - lastSep == 3 && text.substring(lastSep + 1).all { it.isDigit() }) lastSep else text.length
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        pushStyle(androidx.compose.ui.text.SpanStyle(color = dim)); append(text.substring(0, firstDigit)); pop()
        append(text.substring(firstDigit, decimals))
        pushStyle(androidx.compose.ui.text.SpanStyle(color = dim)); append(text.substring(decimals)); pop()
    }
    Text(annotated, modifier = modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
