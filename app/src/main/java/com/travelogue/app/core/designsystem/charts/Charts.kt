package com.travelogue.app.core.designsystem.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.travelogue.app.core.designsystem.theme.Shapes
import com.travelogue.app.core.designsystem.theme.Space
import com.travelogue.app.core.designsystem.theme.Travel
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

data class ChartBar(val label: String, val value: Double, val color: Color? = null)
data class ChartSlice(val label: String, val value: Double, val color: Color)

/**
 * Vertical bar chart. Tap a bar to highlight it and show its value; tap again to clear.
 * Bars grow in on first composition.
 */
@Composable
fun BarChart(
    bars: List<ChartBar>,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    color: Color = Travel.colors.accent,
    initiallySelected: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
) {
    if (bars.isEmpty()) return
    val c = Travel.colors
    var selected by remember(bars) { mutableStateOf(initiallySelected) }
    val progress = remember(bars) { Animatable(0f) }
    LaunchedEffect(bars) { progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val maxV = max(bars.maxOf { it.value }, 0.0001)

    Column(modifier.fillMaxWidth()) {
        // Value bubble
        Box(Modifier.fillMaxWidth().height(28.dp)) {
            val s = selected
            if (s != null && s in bars.indices) {
                Text(
                    "${bars[s].label} · ${valueLabel(bars[s].value)}",
                    style = Travel.type.label,
                    color = c.background,
                    modifier = Modifier.align(Alignment.Center).clip(Shapes.pill).background(c.ink).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(bars) {
                    detectTapGestures { o ->
                        val slot = size.width / bars.size.toFloat()
                        val i = (o.x / slot).toInt().coerceIn(0, bars.lastIndex)
                        selected = if (selected == i) null else i
                        onSelect?.invoke(selected)
                    }
                },
        ) {
            val slot = size.width / bars.size
            val barW = (slot * 0.56f).coerceAtMost(36.dp.toPx())
            // Guide lines
            repeat(3) { g ->
                val y = size.height * (g + 1) / 4f
                drawLine(c.line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
            }
            bars.forEachIndexed { i, b ->
                val h = (b.value / maxV).toFloat() * size.height * progress.value
                val x = i * slot + (slot - barW) / 2
                val base = b.color ?: color
                val col = when {
                    selected == null -> base
                    selected == i -> base
                    else -> base.copy(alpha = 0.3f)
                }
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(col, col.copy(alpha = col.alpha * 0.65f)), startY = size.height - h, endY = size.height),
                    topLeft = Offset(x, size.height - h),
                    size = Size(barW, max(h, if (b.value > 0) 4f else 0f)),
                    cornerRadius = CornerRadius(barW / 2.4f),
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth()) {
            val step = labelStep(bars.size)
            bars.forEachIndexed { i, b ->
                Text(
                    if (i % step == 0) b.label else "",
                    style = Travel.type.bodySmall,
                    color = if (selected == i) c.ink else c.inkFaint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun labelStep(n: Int) = when {
    n <= 8 -> 1
    n <= 16 -> 2
    n <= 31 -> 4
    else -> n / 7
}

/**
 * Donut chart with tap-to-select slices. The centre shows the selected slice (or the total).
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    centerLabel: String,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    thickness: Dp = 26.dp,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
) {
    val c = Travel.colors
    val total = slices.sumOf { it.value }
    val progress = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    // The gesture block outlives recompositions, so it must read the latest selection through state.
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(slices) {
                    detectTapGestures { o ->
                        val cx = this.size.width / 2f
                        val cy = this.size.height / 2f
                        val r = hypot(o.x - cx, o.y - cy)
                        val outer = this.size.width / 2f
                        if (r < outer - thickness.toPx() * 1.6f || r > outer + 8) { currentOnSelect(null); return@detectTapGestures }
                        var angle = Math.toDegrees(atan2((o.y - cy).toDouble(), (o.x - cx).toDouble())).toFloat() + 90f
                        if (angle < 0) angle += 360f
                        var acc = 0f
                        slices.forEachIndexed { i, s ->
                            val sweep = (s.value / total * 360f).toFloat()
                            if (angle >= acc && angle < acc + sweep) { currentOnSelect(if (currentSelected == i) null else i); return@detectTapGestures }
                            acc += sweep
                        }
                    }
                },
        ) {
            val stroke = thickness.toPx()
            val inset = stroke / 2 + 6.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(c.line, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (total <= 0) return@Canvas
            var start = -90f
            val gap = if (slices.size > 1) 1.6f else 0f
            slices.forEachIndexed { i, s ->
                val sweep = (s.value / total * 360f).toFloat() * progress.value
                val isSel = selected == i
                val w = if (isSel) stroke * 1.25f else stroke
                val col = if (selected == null || isSel) s.color else s.color.copy(alpha = 0.35f)
                drawArc(col, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = thickness + 16.dp)) {
            val s = selected?.let { slices.getOrNull(it) }
            Text(
                s?.label ?: centerLabel,
                style = Travel.type.bodySmall,
                color = c.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                valueLabel(s?.value ?: total),
                style = Travel.type.numberSmall,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (s != null && total > 0) {
                Text("${Math.round(s.value / total * 100)}%", style = Travel.type.bodySmall, color = s.color)
            }
        }
    }
}

/** Legend rows for a donut, tappable to select the same slice. */
@Composable
fun ChartLegend(
    slices: List<ChartSlice>,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
) {
    val total = slices.sumOf { it.value }.coerceAtLeast(0.0001)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        slices.forEachIndexed { i, s ->
            val dim = selected != null && selected != i
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Shapes.sm)
                    .background(if (selected == i) s.color.copy(alpha = 0.1f) else Color.Transparent)
                    .clickable { onSelect(if (selected == i) null else i) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (dim) s.color.copy(alpha = 0.35f) else s.color))
                Spacer(Modifier.width(10.dp))
                Text(s.label, style = Travel.type.body, color = if (dim) Travel.colors.inkFaint else Travel.colors.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Math.round(s.value / total * 100)}%", style = Travel.type.bodySmall, color = Travel.colors.inkFaint)
                Spacer(Modifier.width(10.dp))
                Text(valueLabel(s.value), style = Travel.type.label, color = if (dim) Travel.colors.inkFaint else Travel.colors.ink)
            }
        }
    }
}

/**
 * Smooth area/line chart. Tap or drag to inspect a point.
 */
@Composable
fun AreaChart(
    points: List<ChartBar>,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    color: Color = Travel.colors.secondary,
) {
    if (points.isEmpty()) return
    val c = Travel.colors
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val progress = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val maxV = max(points.maxOf { it.value }, 0.0001)

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(28.dp)) {
            val s = selected
            if (s != null) {
                Text(
                    "${points[s].label} · ${valueLabel(points[s].value)}",
                    style = Travel.type.label,
                    color = c.background,
                    modifier = Modifier.align(Alignment.Center).clip(Shapes.pill).background(c.ink).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(points) {
                    detectTapGestures { o ->
                        val step = if (points.size > 1) size.width / (points.size - 1).toFloat() else size.width.toFloat()
                        val i = Math.round(o.x / step).coerceIn(0, points.lastIndex)
                        selected = if (selected == i) null else i
                    }
                },
        ) {
            val n = points.size
            val step = if (n > 1) size.width / (n - 1) else size.width
            val pts = points.mapIndexed { i, p ->
                Offset(if (n > 1) i * step else size.width / 2, size.height - (p.value / maxV).toFloat() * size.height * 0.9f * progress.value - 4f)
            }
            val line = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) {
                    val p0 = pts[i - 1]
                    val p1 = pts[i]
                    val mx = (p0.x + p1.x) / 2
                    cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(pts.last().x, size.height)
                lineTo(pts.first().x, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
            drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            selected?.let { i ->
                val p = pts[i]
                drawLine(c.inkFaint, Offset(p.x, 0f), Offset(p.x, size.height), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                drawCircle(c.card, 7.dp.toPx(), p)
                drawCircle(color, 5.dp.toPx(), p)
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().label, style = Travel.type.bodySmall, color = c.inkFaint)
            if (points.size > 2) Text(points[points.size / 2].label, style = Travel.type.bodySmall, color = c.inkFaint)
            if (points.size > 1) Text(points.last().label, style = Travel.type.bodySmall, color = c.inkFaint)
        }
    }
}

/** Circular budget ring with a label in the middle. */
@Composable
fun BudgetRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    thickness: Dp = 12.dp,
    color: Color = Travel.colors.accent,
    content: @Composable () -> Unit = {},
) {
    val c = Travel.colors
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) { anim.animateTo(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing)) }
    val col = if (progress > 1f) c.danger else color
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = thickness.toPx()
            val inset = s / 2
            val arc = Size(this.size.width - s, this.size.height - s)
            drawArc(c.line, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(s))
            drawArc(col, -90f, 360f * anim.value, false, Offset(inset, inset), arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}
