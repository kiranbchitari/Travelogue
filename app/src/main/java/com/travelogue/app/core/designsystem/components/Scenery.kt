package com.travelogue.app.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.travelogue.app.core.designsystem.theme.AppTheme
import com.travelogue.app.core.designsystem.theme.ScenePalette
import com.travelogue.app.core.designsystem.theme.Scenery
import com.travelogue.app.core.designsystem.theme.Travel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * The illustrations that give each theme its mood. Everything is drawn with paths, so there are no
 * image assets and the art stays sharp at any size, from a 34dp thumbnail to a full-width header.
 *
 * A scene is drawn into the box (0,0)-(w,h); callers clip. Scenes are composed with their weight on
 * the right, because headings sit on the left when a scene is used as a screen backdrop.
 */

/** How tall the scenery behind the top of a screen is. Below it the page is plain. */
private val BackdropHeight = 250.dp

/**
 * The page background of the current theme: its background colour with a quiet version of its
 * scenery across the top, fading into the page. Apply to anything full-width that starts at the
 * top edge of the screen (the app root, pushed screens, their top bars) and the pieces line up.
 */
@Composable
fun Modifier.travelBackdrop(): Modifier {
    val theme = Travel.theme
    val c = Travel.colors
    if (!theme.hasBackdrop) return background(c.background)
    val palette = remember(theme, c) { theme.scene(c.isDark).toward(c.background, if (c.isDark) 0.5f else 0.42f) }
    return drawWithCache {
        val bandHeight = BackdropHeight.toPx()
        val fade = Brush.verticalGradient(0.3f to Color.Transparent, 1f to c.background, startY = 0f, endY = bandHeight)
        onDrawBehind {
            drawRect(c.background)
            clipRect(0f, 0f, size.width, min(size.height, bandHeight)) {
                drawScene(theme.scenery, palette, size.width, bandHeight, backdrop = true)
                drawRect(fade, size = Size(size.width, bandHeight))
            }
        }
    }
}

/**
 * A theme's scenery at full strength, filling the box. [seed] picks a time of day and which way the
 * scene faces, so two covers side by side are not identical.
 */
@Composable
fun SceneArt(modifier: Modifier = Modifier, theme: AppTheme = Travel.theme, seed: Int = 0, dark: Boolean = Travel.colors.isDark) {
    val palette = remember(theme, dark, seed) { theme.scene(dark).mood(seed) }
    val mirrored = (seed / 3) % 2 == 1
    Canvas(modifier.clipToBounds()) {
        if (mirrored) scale(-1f, 1f) { drawScene(theme.scenery, palette, size.width, size.height, seed) }
        else drawScene(theme.scenery, palette, size.width, size.height, seed)
    }
}

/** A small flourish in the theme's style, drawn under section titles. Nothing in the classic look. */
@Composable
fun ThemeFlourish(modifier: Modifier = Modifier) {
    val theme = Travel.theme
    if (theme.scenery == Scenery.RINGS) return
    val c = Travel.colors
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = h * 0.32f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (theme.scenery) {
            Scenery.BEACH -> drawPath(waveLine(w, h * 0.5f, h * 0.42f, 3), c.accent, style = stroke)
            Scenery.MOUNTAIN -> {
                val peaks = Path().apply {
                    moveTo(0f, h * 0.85f); lineTo(w * 0.22f, h * 0.15f); lineTo(w * 0.42f, h * 0.85f)
                    lineTo(w * 0.68f, h * 0.05f); lineTo(w, h * 0.85f)
                }
                drawPath(peaks, c.accent, style = stroke)
            }
            Scenery.SUNSET -> listOf(c.gold to 1f, c.accent to 0.68f, c.secondary to 0.4f).forEachIndexed { i, (color, length) ->
                val y = h * (0.16f + i * 0.34f)
                drawLine(color, Offset(0f, y), Offset(w * length, y), strokeWidth = h * 0.2f, cap = StrokeCap.Round)
            }
            Scenery.AIRMAIL -> airmailStripes(0f, h * 0.2f, w, h * 0.6f, c.gold, c.accent)
            Scenery.FIESTA -> listOf(c.accent, c.gold, c.secondary, c.sage).forEachIndexed { i, color ->
                drawCircle(color, h * (if (i % 2 == 0) 0.36f else 0.26f), Offset(h * 0.4f + i * w * 0.27f, h * (if (i % 2 == 0) 0.42f else 0.62f)))
            }
            Scenery.RINGS -> Unit
        }
    }
}

/**
 * Draws a scene into (0,0)-(w,h). With [backdrop] the scene is going behind a screen's heading, so
 * anything that would cross the heading's text is moved clear of it.
 */
fun DrawScope.drawScene(kind: Scenery, p: ScenePalette, w: Float, h: Float, seed: Int = 0, backdrop: Boolean = false) {
    when (kind) {
        Scenery.RINGS -> rings(p, w, h)
        Scenery.BEACH -> beach(p, w, h, seed)
        Scenery.MOUNTAIN -> mountains(p, w, h, seed)
        Scenery.SUNSET -> sunset(p, w, h, seed)
        Scenery.AIRMAIL -> airmail(p, w, h, seed, backdrop)
        Scenery.FIESTA -> fiesta(p, w, h, seed, backdrop)
    }
}

// Scenes --------------------------------------------------------------------------------------

/** The classic look in miniature: an ink disc inside hairline rings on plain paper. */
private fun DrawScope.rings(p: ScenePalette, w: Float, h: Float) {
    val u = min(w, h)
    val c = Offset(w * 0.5f, h * 0.5f)
    drawRect(p.skyBottom, size = Size(w, h))
    drawCircle(p.far, u * 0.44f, c, style = Stroke(u * 0.012f))
    drawCircle(p.mid, u * 0.33f, c, style = Stroke(u * 0.012f))
    drawCircle(p.sun, u * 0.2f, c)
    drawCircle(p.detail, u * 0.035f, Offset(c.x + u * 0.31f, c.y - u * 0.11f))
}

private fun DrawScope.beach(p: ScenePalette, w: Float, h: Float, seed: Int) {
    val u = min(w, h)
    sky(p, w, h)
    if (p.night) stars(p, w, h, seed)
    sunOrMoon(p, Offset(w * 0.74f, h * 0.25f), u * 0.115f)
    if (!p.night) {
        cloud(p, w * 0.2f, h * 0.2f, u * 0.085f)
        cloud(p, w * 0.5f, h * 0.36f, u * 0.055f)
    }
    val ink = lerp(p.detail, p.skyTop, 0.25f)
    bird(ink, w * 0.36f, h * 0.13f, u * 0.028f)
    bird(ink, w * 0.44f, h * 0.19f, u * 0.02f)

    drawPath(waveBand(w, h, h * 0.56f, u * 0.012f, 5, 0f), p.far)
    // A little sailboat out on the far water.
    val boat = Offset(w * 0.3f, h * 0.565f)
    val s = u * 0.05f
    drawPath(Path().apply { moveTo(boat.x, boat.y - s * 1.5f); lineTo(boat.x + s * 0.8f, boat.y - s * 0.2f); lineTo(boat.x, boat.y - s * 0.2f); close() }, p.mist)
    drawPath(Path().apply { moveTo(boat.x - s * 0.7f, boat.y - s * 0.1f); lineTo(boat.x + s * 0.9f, boat.y - s * 0.1f); lineTo(boat.x + s * 0.55f, boat.y + s * 0.25f); lineTo(boat.x - s * 0.45f, boat.y + s * 0.25f); close() }, p.detail)

    drawPath(waveBand(w, h, h * 0.66f, u * 0.016f, 4, 0.5f), p.mid)
    val foam = p.mist.copy(alpha = 0.45f)
    drawPath(waveLine(w, h * 0.725f, u * 0.012f, 6), foam, style = Stroke(u * 0.008f, cap = StrokeCap.Round))
    drawPath(hill(w, h, 0.85f, 0.76f, 0.9f, 0.78f), p.near)

    val trunk = lerp(p.detail, p.near, 0.35f)
    palm(Offset(w * 0.95f, h * 0.84f), h * 0.36f, -0.5f, trunk, lerp(p.detail, p.mid, 0.25f))
    palm(Offset(w * 0.84f, h * 0.86f), h * 0.52f, 0.7f, trunk, p.detail)
}

private fun DrawScope.mountains(p: ScenePalette, w: Float, h: Float, seed: Int) {
    val u = min(w, h)
    sky(p, w, h)
    if (p.night) stars(p, w, h, seed)
    sunOrMoon(p, Offset(w * 0.52f, h * 0.24f), u * 0.095f)
    if (!p.night) {
        cloud(p, w * 0.18f, h * 0.24f, u * 0.085f)
        cloud(p, w * 0.86f, h * 0.15f, u * 0.055f)
    }
    val range = Path().apply {
        moveTo(0f, h * 0.62f)
        listOf(0.12f to 0.5f, 0.24f to 0.58f, 0.38f to 0.44f, 0.5f to 0.56f, 0.62f to 0.46f, 0.78f to 0.58f, 0.9f to 0.5f, 1f to 0.56f)
            .forEach { (x, y) -> lineTo(w * x, h * y) }
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(range, p.far)

    val shade = lerp(p.mid, p.near, 0.55f)
    peak(Offset(w * 0.02f, h * 0.82f), Offset(w * 0.25f, h * 0.43f), Offset(w * 0.5f, h * 0.82f), lerp(p.mid, p.far, 0.4f), lerp(shade, p.far, 0.4f), p.detail, 0.28f)
    peak(Offset(w * 0.4f, h * 0.82f), Offset(w * 0.7f, h * 0.19f), Offset(w * 0.99f, h * 0.82f), p.mid, shade, p.detail, 0.32f)
    peak(Offset(w * 0.72f, h * 0.82f), Offset(w * 0.93f, h * 0.36f), Offset(w * 1.14f, h * 0.82f), lerp(p.mid, p.near, 0.3f), lerp(shade, p.near, 0.4f), p.detail, 0.26f)

    drawPath(hill(w, h, 0.8f, 0.7f, 0.86f, 0.74f), p.near)
    val pineColor = lerp(p.near, Color.Black, if (p.night) 0.35f else 0.28f)
    listOf(0.06f to 0.15f, 0.14f to 0.2f, 0.22f to 0.13f, 0.52f to 0.14f, 0.6f to 0.19f, 0.87f to 0.17f, 0.95f to 0.12f).forEach { (x, size) ->
        // Stand each pine on the slope beneath it.
        pine(Offset(w * x, h * (hillY(x, 0.8f, 0.7f, 0.86f, 0.74f) + 0.035f)), h * size, pineColor)
    }
    val ink = lerp(p.near, p.skyTop, 0.2f)
    bird(ink, w * 0.3f, h * 0.16f, u * 0.024f)
    bird(ink, w * 0.37f, h * 0.2f, u * 0.018f)
}

private fun DrawScope.sunset(p: ScenePalette, w: Float, h: Float, seed: Int) {
    val u = min(w, h)
    sky(p, w, h)
    if (p.night) stars(p, w, h, seed)
    // A retro sun, sliced into thinning bands as it sinks.
    val c = Offset(w * 0.66f, h * 0.53f)
    val r = u * 0.25f
    drawCircle(p.sun.copy(alpha = 0.18f), r * 1.35f, c)
    clipRect(0f, 0f, w, c.y + r * 0.08f) { drawCircle(p.sun, r, c) }
    var y = c.y + r * 0.08f
    var gap = r * 0.055f
    var band = r * 0.17f
    repeat(5) {
        y += gap
        clipRect(0f, y, w, y + band) { drawCircle(p.sun, r, c) }
        y += band
        gap *= 1.35f
        band *= 0.78f
    }
    bird(p.detail, w * 0.4f, h * 0.13f, u * 0.028f)
    bird(p.detail, w * 0.48f, h * 0.19f, u * 0.02f)
    bird(p.detail, w * 0.33f, h * 0.2f, u * 0.018f)

    val mesas = Path().apply {
        moveTo(0f, h * 0.67f)
        listOf(
            0.08f to 0.67f, 0.11f to 0.57f, 0.29f to 0.57f, 0.32f to 0.67f, 0.5f to 0.67f,
            0.53f to 0.61f, 0.63f to 0.61f, 0.66f to 0.69f, 0.84f to 0.69f, 0.87f to 0.63f, 1f to 0.63f,
        ).forEach { (x, yy) -> lineTo(w * x, h * yy) }
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(mesas, p.far)
    drawPath(hill(w, h, 0.77f, 0.67f, 0.83f, 0.71f), p.mid)
    drawPath(hill(w, h, 0.88f, 0.8f, 0.92f, 0.84f), p.near)
    cactus(Offset(w * 0.14f, h * 0.88f), h * 0.27f, p.detail)
    cactus(Offset(w * 0.9f, h * 0.87f), h * 0.19f, p.detail)
    cactus(Offset(w * 0.27f, h * 0.9f), h * 0.12f, p.detail)
}

private fun DrawScope.airmail(p: ScenePalette, w: Float, h: Float, seed: Int, backdrop: Boolean) {
    val u = min(w, h)
    drawRect(p.skyBottom, size = Size(w, h))
    drawRect(Brush.verticalGradient(listOf(p.skyTop, p.skyBottom), startY = 0f, endY = h * 0.7f), size = Size(w, h * 0.7f))
    if (p.night) stars(p, w, h, seed)
    drawPath(hill(w, h, 0.84f, 0.74f, 0.88f, 0.78f), p.far)
    drawPath(hill(w, h, 0.9f, 0.88f, 0.8f, 0.9f), p.mid)

    // A postage stamp, top right, with a tiny landscape printed on it.
    val stampW = u * 0.3f
    val stampH = u * 0.37f
    val stamp = Offset(w - stampW - w * 0.07f, h * 0.17f)
    drawRoundRect(p.mist, stamp, Size(stampW, stampH), CornerRadius(u * 0.012f))
    val teeth = u * 0.014f
    var tx = stamp.x + teeth * 1.5f
    while (tx < stamp.x + stampW - teeth) {
        drawCircle(p.skyBottom, teeth, Offset(tx, stamp.y)); drawCircle(p.skyBottom, teeth, Offset(tx, stamp.y + stampH))
        tx += teeth * 3f
    }
    var ty = stamp.y + teeth * 1.5f
    while (ty < stamp.y + stampH - teeth) {
        drawCircle(p.skyBottom, teeth, Offset(stamp.x, ty)); drawCircle(p.skyBottom, teeth, Offset(stamp.x + stampW, ty))
        ty += teeth * 3f
    }
    val inset = u * 0.035f
    val picture = Offset(stamp.x + inset, stamp.y + inset)
    val pw = stampW - inset * 2
    val ph = stampH - inset * 2
    drawRect(lerp(p.skyTop, p.mist, 0.25f), picture, Size(pw, ph))
    drawCircle(p.sun, pw * 0.17f, Offset(picture.x + pw * 0.68f, picture.y + ph * 0.3f))
    drawPath(Path().apply {
        moveTo(picture.x, picture.y + ph); lineTo(picture.x + pw * 0.36f, picture.y + ph * 0.42f); lineTo(picture.x + pw * 0.58f, picture.y + ph * 0.72f)
        lineTo(picture.x + pw * 0.76f, picture.y + ph * 0.55f); lineTo(picture.x + pw, picture.y + ph); close()
    }, p.detail)

    // The postmark that cancels it: rings on the stamp's corner, wavy lines running across it.
    val ink = p.detail.copy(alpha = 0.6f)
    val mark = Offset(stamp.x - u * 0.02f, stamp.y + stampH * 0.86f)
    val line = Stroke(u * 0.007f, cap = StrokeCap.Round)
    drawCircle(ink, u * 0.11f, mark, style = line)
    drawCircle(ink, u * 0.075f, mark, style = line)
    repeat(3) { i ->
        val y = mark.y - u * 0.045f + i * u * 0.045f
        val x0 = mark.x + u * 0.12f
        val wave = Path().apply {
            moveTo(x0, y)
            val seg = u * 0.045f
            repeat(5) { k -> quadraticTo(x0 + seg * (k + 0.5f), y + (if (k % 2 == 0) -seg * 0.5f else seg * 0.5f), x0 + seg * (k + 1), y) }
        }
        drawPath(wave, ink, style = line)
    }

    // The route flown: a pin, a dashed arc and a paper plane at the end of it.
    // Behind a heading it hops across the clear strip above the text instead.
    val from = if (backdrop) Offset(w * 0.3f, h * 0.2f) else Offset(w * 0.09f, h * 0.7f)
    val bend = if (backdrop) Offset(w * 0.43f, h * 0.02f) else Offset(w * 0.3f, h * 0.1f)
    val to = if (backdrop) Offset(w * 0.56f, h * 0.15f) else Offset(w * 0.56f, h * 0.36f)
    val route = Path().apply { moveTo(from.x, from.y); quadraticTo(bend.x, bend.y, to.x, to.y) }
    drawPath(route, p.detail, style = Stroke(u * 0.009f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.03f, u * 0.028f))))
    drawCircle(p.sun.copy(alpha = 0.25f), u * 0.04f, from)
    drawCircle(p.sun, u * 0.02f, from)
    val heading = atan2(to.y - bend.y, to.x - bend.x) * 180f / PI.toFloat()
    val s = u * 0.06f
    val tip = Offset(to.x + s * 0.9f * cos(heading * PI.toFloat() / 180f), to.y + s * 0.9f * sin(heading * PI.toFloat() / 180f))
    rotate(heading, tip) {
        drawPath(Path().apply { moveTo(tip.x + s, tip.y); lineTo(tip.x - s, tip.y - s * 0.62f); lineTo(tip.x - s * 0.5f, tip.y); lineTo(tip.x - s, tip.y + s * 0.62f); close() }, p.detail)
        drawPath(Path().apply { moveTo(tip.x + s, tip.y); lineTo(tip.x - s * 0.5f, tip.y); lineTo(tip.x - s, tip.y + s * 0.62f); close() }, lerp(p.detail, Color.Black, 0.22f))
    }

    val t = u * 0.04f
    airmailStripes(0f, 0f, w, t, p.sun, p.detail)
    airmailStripes(0f, h - t, w, t, p.sun, p.detail)
}

private fun DrawScope.fiesta(p: ScenePalette, w: Float, h: Float, seed: Int, backdrop: Boolean) {
    val u = min(w, h)
    val party = listOf(p.detail, p.sun, p.near, p.mid, p.far)
    sky(p, w, h)
    if (p.night) stars(p, w, h, seed)
    val sun = Offset(w * 0.87f, h * 0.2f)
    if (!p.night) repeat(12) { i ->
        rotate(i * 30f, sun) { drawLine(p.sun.copy(alpha = 0.7f), Offset(sun.x, sun.y - u * 0.14f), Offset(sun.x, sun.y - u * 0.19f), strokeWidth = u * 0.014f, cap = StrokeCap.Round) }
    }
    sunOrMoon(p, sun, u * 0.1f)
    if (!p.night) cloud(p, w * 0.16f, h * 0.36f, u * 0.07f)

    drawPath(hill(w, h, 0.72f, 0.6f, 0.78f, 0.66f), p.far)
    drawPath(hill(w, h, 0.8f, 0.86f, 0.68f, 0.8f), p.mid)
    drawPath(hill(w, h, 0.9f, 0.8f, 0.94f, 0.84f), p.near)

    balloon(Offset(w * (if (backdrop) 0.72f else 0.64f), h * 0.38f), u * 0.125f, p.detail, p.sun, p.mist)
    balloon(Offset(w * (if (backdrop) 0.53f else 0.4f), h * (if (backdrop) 0.19f else 0.27f)), u * (if (backdrop) 0.055f else 0.07f), p.mid, p.mist, p.sun)
    balloon(Offset(w * 0.9f, h * 0.55f), u * 0.058f, p.near, p.sun, p.mist)

    // Confetti drifting down.
    val r = Random(seed * 131 + 17)
    repeat(34) { i ->
        val at = Offset(r.nextFloat() * w, r.nextFloat() * h * 0.72f)
        val color = party[i % party.size].copy(alpha = 0.9f)
        val size = u * (0.012f + r.nextFloat() * 0.012f)
        if (i % 3 == 0) drawCircle(color, size * 0.55f, at)
        else rotate(r.nextFloat() * 180f, at) { drawRoundRect(color, Offset(at.x - size, at.y - size * 0.35f), Size(size * 2, size * 0.7f), CornerRadius(size * 0.2f)) }
    }
    bunting(Offset(-w * 0.02f, h * 0.02f), Offset(w * 1.02f, h * 0.05f), h * 0.11f, 11, u * 0.075f, party, lerp(p.detail, Color.Black, 0.4f))
}

// Building blocks -----------------------------------------------------------------------------

private fun DrawScope.sky(p: ScenePalette, w: Float, h: Float) =
    drawRect(Brush.verticalGradient(listOf(p.skyTop, p.skyBottom), startY = 0f, endY = h * 0.85f), size = Size(w, h))

private fun DrawScope.sunOrMoon(p: ScenePalette, c: Offset, r: Float) {
    drawCircle(p.sun.copy(alpha = 0.14f), r * 1.9f, c)
    drawCircle(p.sun.copy(alpha = 0.2f), r * 1.4f, c)
    drawCircle(p.sun, r, c)
    // At night the disc is a moon: bite a crescent out of it with the sky behind.
    if (p.night) drawCircle(lerp(p.skyTop, p.skyBottom, 0.3f), r * 0.82f, Offset(c.x + r * 0.42f, c.y - r * 0.3f))
}

private fun DrawScope.stars(p: ScenePalette, w: Float, h: Float, seed: Int) {
    val u = min(w, h)
    val r = Random(seed * 31 + 7)
    repeat(30) {
        drawCircle(p.mist.copy(alpha = 0.3f + r.nextFloat() * 0.55f), u * (0.004f + r.nextFloat() * 0.006f), Offset(r.nextFloat() * w, r.nextFloat() * h * 0.6f))
    }
}

private fun DrawScope.cloud(p: ScenePalette, cx: Float, cy: Float, s: Float) {
    val color = lerp(p.skyTop, p.mist, 0.82f)
    drawRoundRect(color, Offset(cx - s * 1.1f, cy - s * 0.28f), Size(s * 2.2f, s * 0.56f), CornerRadius(s * 0.28f))
    drawCircle(color, s * 0.42f, Offset(cx - s * 0.38f, cy - s * 0.3f))
    drawCircle(color, s * 0.56f, Offset(cx + s * 0.24f, cy - s * 0.42f))
}

private fun DrawScope.bird(color: Color, x: Float, y: Float, s: Float) {
    val wings = Path().apply {
        moveTo(x - s, y - s * 0.35f)
        quadraticTo(x - s * 0.5f, y - s * 0.75f, x, y)
        quadraticTo(x + s * 0.5f, y - s * 0.75f, x + s, y - s * 0.35f)
    }
    drawPath(wings, color, style = Stroke(s * 0.24f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Height (as a fraction of the scene) of a [hill] with the same four numbers at horizontal position [x]. */
private fun hillY(x: Float, y0: Float, y1: Float, y2: Float, y3: Float): Float {
    val m = 1 - x
    return m * m * m * y0 + 3 * m * m * x * y1 + 3 * m * x * x * y2 + x * x * x * y3
}

/** Ground that rolls from the left edge (at height y0) to the right (y3), filled down to the bottom. */
private fun hill(w: Float, h: Float, y0: Float, y1: Float, y2: Float, y3: Float) = Path().apply {
    moveTo(0f, h * y0)
    cubicTo(w / 3f, h * y1, w * 2f / 3f, h * y2, w, h * y3)
    lineTo(w, h); lineTo(0f, h); close()
}

/** An open wavy line across the width. */
private fun waveLine(w: Float, y: Float, amp: Float, waves: Int) = Path().apply {
    val seg = w / (waves * 2)
    moveTo(0f, y)
    repeat(waves * 2) { i -> quadraticTo(seg * (i + 0.5f), y + (if (i % 2 == 0) -amp else amp), seg * (i + 1), y) }
}

/** Water: a wavy top edge at [y], filled down to the bottom. [phase] slides the waves sideways. */
private fun waveBand(w: Float, h: Float, y: Float, amp: Float, waves: Int, phase: Float) = Path().apply {
    val seg = w / (waves * 2)
    val start = -phase * seg * 2
    moveTo(start, y)
    repeat(waves * 2 + 2) { i -> quadraticTo(start + seg * (i + 0.5f), y + (if (i % 2 == 0) -amp else amp), start + seg * (i + 1), y) }
    lineTo(w + seg * 2, h); lineTo(start, h); close()
}

private fun DrawScope.palm(base: Offset, height: Float, lean: Float, trunk: Color, leaf: Color) {
    val top = Offset(base.x + lean * height * 0.3f, base.y - height)
    val stem = Path().apply { moveTo(base.x, base.y); quadraticTo(base.x + lean * height * 0.02f, base.y - height * 0.6f, top.x, top.y) }
    drawPath(stem, trunk, style = Stroke(height * 0.065f, cap = StrokeCap.Round))
    val reach = height * 0.52f
    listOf(-1f to 0.34f, -0.82f to -0.22f, -0.3f to -0.52f, 0.32f to -0.5f, 0.84f to -0.2f, 1f to 0.36f).forEach { (dx, dy) ->
        val tip = Offset(top.x + dx * reach, top.y + dy * reach)
        val mid = Offset((top.x + tip.x) / 2, (top.y + tip.y) / 2)
        val frond = Path().apply {
            moveTo(top.x, top.y)
            quadraticTo(mid.x, mid.y - reach * 0.62f, tip.x, tip.y)
            quadraticTo(mid.x, mid.y - reach * 0.2f, top.x, top.y)
            close()
        }
        drawPath(frond, leaf)
    }
    drawCircle(trunk, height * 0.045f, Offset(top.x - height * 0.035f, top.y + height * 0.03f))
    drawCircle(trunk, height * 0.045f, Offset(top.x + height * 0.045f, top.y + height * 0.045f))
}

private fun DrawScope.pine(base: Offset, height: Float, color: Color) {
    drawRect(lerp(color, Color.Black, 0.2f), Offset(base.x - height * 0.04f, base.y - height * 0.2f), Size(height * 0.08f, height * 0.2f))
    repeat(3) { i ->
        val top = base.y - height + i * height * 0.2f
        val bottom = top + height * 0.46f
        val half = height * (0.17f + i * 0.07f)
        drawPath(Path().apply { moveTo(base.x, top); lineTo(base.x + half, bottom); lineTo(base.x - half, bottom); close() }, color)
    }
}

/** A mountain with a shaded right face and a jagged snow cap covering the top [snow] of it. */
private fun DrawScope.peak(left: Offset, top: Offset, right: Offset, color: Color, shade: Color, snowColor: Color, snow: Float) {
    drawPath(Path().apply { moveTo(left.x, left.y); lineTo(top.x, top.y); lineTo(right.x, right.y); close() }, color)
    val foot = Offset(top.x + (right.x - top.x) * 0.22f, right.y)
    drawPath(Path().apply { moveTo(top.x, top.y); lineTo(right.x, right.y); lineTo(foot.x, foot.y); close() }, shade)
    val l = Offset(top.x + (left.x - top.x) * snow, top.y + (left.y - top.y) * snow)
    val r = Offset(top.x + (right.x - top.x) * snow, top.y + (right.y - top.y) * snow)
    val drop = (l.y - top.y) * 0.32f
    val cap = Path().apply {
        moveTo(top.x, top.y); lineTo(r.x, r.y)
        val steps = 5
        for (i in 1 until steps) {
            val x = r.x + (l.x - r.x) * i / steps
            lineTo(x, l.y + if (i % 2 == 1) -drop else drop * 0.4f)
        }
        lineTo(l.x, l.y); close()
    }
    drawPath(cap, snowColor)
}

private fun DrawScope.cactus(base: Offset, height: Float, color: Color) {
    val t = height * 0.2f
    val limb = Stroke(t, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(color, base, Offset(base.x, base.y - height), strokeWidth = t, cap = StrokeCap.Round)
    drawPath(Path().apply { moveTo(base.x, base.y - height * 0.38f); lineTo(base.x - height * 0.3f, base.y - height * 0.38f); lineTo(base.x - height * 0.3f, base.y - height * 0.68f) }, color, style = limb.copy(width = t * 0.8f))
    drawPath(Path().apply { moveTo(base.x, base.y - height * 0.54f); lineTo(base.x + height * 0.28f, base.y - height * 0.54f); lineTo(base.x + height * 0.28f, base.y - height * 0.86f) }, color, style = limb.copy(width = t * 0.8f))
}

private fun Stroke.copy(width: Float) = Stroke(width, miter, cap, join, pathEffect)

private fun DrawScope.balloon(c: Offset, r: Float, body: Color, stripe: Color, core: Color) {
    val rope = lerp(body, Color.Black, 0.45f)
    drawLine(rope, Offset(c.x - r * 0.5f, c.y + r * 0.8f), Offset(c.x - r * 0.17f, c.y + r * 1.62f), strokeWidth = r * 0.04f)
    drawLine(rope, Offset(c.x + r * 0.5f, c.y + r * 0.8f), Offset(c.x + r * 0.17f, c.y + r * 1.62f), strokeWidth = r * 0.04f)
    drawPath(Path().apply {
        moveTo(c.x - r * 0.84f, c.y + r * 0.54f); lineTo(c.x - r * 0.22f, c.y + r * 1.38f)
        lineTo(c.x + r * 0.22f, c.y + r * 1.38f); lineTo(c.x + r * 0.84f, c.y + r * 0.54f); close()
    }, body)
    drawCircle(body, r, c)
    drawOval(stripe, Offset(c.x - r * 0.56f, c.y - r), Size(r * 1.12f, r * 2f))
    drawOval(core, Offset(c.x - r * 0.2f, c.y - r), Size(r * 0.4f, r * 2f))
    drawRoundRect(rope, Offset(c.x - r * 0.2f, c.y + r * 1.6f), Size(r * 0.4f, r * 0.3f), CornerRadius(r * 0.06f))
}

/** A sagging string of triangular flags from [from] to [to]. */
private fun DrawScope.bunting(from: Offset, to: Offset, sag: Float, flags: Int, flagHeight: Float, colors: List<Color>, cord: Color) {
    val bend = Offset((from.x + to.x) / 2, (from.y + to.y) / 2 + sag * 2)
    fun at(t: Float): Offset {
        val m = 1 - t
        return Offset(m * m * from.x + 2 * m * t * bend.x + t * t * to.x, m * m * from.y + 2 * m * t * bend.y + t * t * to.y)
    }
    repeat(flags) { i ->
        val a = at((i + 0.12f) / flags)
        val b = at((i + 0.88f) / flags)
        drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo((a.x + b.x) / 2, (a.y + b.y) / 2 + flagHeight); close() }, colors[i % colors.size])
    }
    drawPath(Path().apply { moveTo(from.x, from.y); quadraticTo(bend.x, bend.y, to.x, to.y) }, cord, style = Stroke(flagHeight * 0.05f))
}

/** The slanted red-and-blue edging of an airmail envelope. */
private fun DrawScope.airmailStripes(x: Float, y: Float, width: Float, t: Float, red: Color, blue: Color) {
    clipRect(x, y, x + width, y + t) {
        var at = x - t
        var i = 0
        while (at < x + width) {
            drawPath(Path().apply { moveTo(at, y + t); lineTo(at + t * 0.7f, y); lineTo(at + t * 2.1f, y); lineTo(at + t * 1.4f, y + t); close() }, if (i % 2 == 0) red else blue)
            at += t * 2.6f
            i++
        }
    }
}
