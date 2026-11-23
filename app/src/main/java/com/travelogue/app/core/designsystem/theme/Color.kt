package com.travelogue.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Raw palette. Monochrome first: crisp off-white and true ink in light, near-black in dark, with one
 * hot accent and one emerald used sparingly. Colour is reserved for data, status and photos.
 */
object Palette {
    val Canvas = Color(0xFFF5F5F3)
    val CanvasDeep = Color(0xFFECECE9)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF0E0E0E)
    val InkSoft = Color(0xFF5A5A5A)
    val InkFaint = Color(0xFF9B9B9B)
    val Line = Color(0xFFE4E4E0)

    val Ember = Color(0xFFDD552B)
    val EmberSoft = Color(0xFFFBE7DD)
    val Emerald = Color(0xFF0B6E4F)
    val EmeraldSoft = Color(0xFFDDEEE7)
    val Moss = Color(0xFF6C8362)
    val MossSoft = Color(0xFFE6ECE1)
    val Danger = Color(0xFFC8321E)
    val Success = Color(0xFF1B8A5A)

    // Dark
    val Night = Color(0xFF060606)
    val NightDeep = Color(0xFF000000)
    val NightCard = Color(0xFF121212)
    val NightCardHigh = Color(0xFF1C1C1C)
    val NightInk = Color(0xFFF5F5F5)
    val NightInkSoft = Color(0xFFB4B4B4)
    val NightInkFaint = Color(0xFF7A7A7A)
    val NightLine = Color(0xFF272727)
    val EmberNight = Color(0xFFFF7A45)
    val EmeraldNight = Color(0xFF3DDC97)
}

/** Semantic colors used by every screen. */
@Immutable
data class TravelColors(
    val background: Color,
    val backgroundDeep: Color,
    val card: Color,
    val cardHigh: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val line: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val secondary: Color,
    val secondarySoft: Color,
    val gold: Color,
    val goldSoft: Color,
    val sage: Color,
    val sageSoft: Color,
    val danger: Color,
    val success: Color,
    val chart: List<Color>,
    val isDark: Boolean,
    /** The docked bottom bar: its fill, its icons and labels, and the mark on the current tab. */
    val bar: Color = Color(0xFF0E0E0E),
    val onBar: Color = Color.White,
    val barAccent: Color = Color(0xFFFF7A45),
)

val LightTravelColors = TravelColors(
    background = Palette.Canvas,
    backgroundDeep = Palette.CanvasDeep,
    card = Palette.Card,
    cardHigh = Color(0xFFFFFFFF),
    ink = Palette.Ink,
    inkSoft = Palette.InkSoft,
    inkFaint = Palette.InkFaint,
    line = Palette.Line,
    accent = Palette.Ink,
    accentSoft = Color(0xFFEDEDEA),
    onAccent = Color.White,
    secondary = Palette.Emerald,
    secondarySoft = Palette.EmeraldSoft,
    gold = Palette.Ember,
    goldSoft = Palette.EmberSoft,
    sage = Palette.Moss,
    sageSoft = Palette.MossSoft,
    danger = Palette.Danger,
    success = Palette.Success,
    chart = listOf(
        Palette.Ink, Palette.Ember, Palette.Emerald, Color(0xFF3D4EDB), Color(0xFFE0A21A),
        Color(0xFF8A3F7A), Color(0xFF4A6078), Color(0xFFE0718A), Color(0xFFB59A76), Color(0xFF2E9C99), Color(0xFFA3A3A3),
    ),
    isDark = false,
)

val DarkTravelColors = TravelColors(
    background = Palette.Night,
    backgroundDeep = Palette.NightDeep,
    card = Palette.NightCard,
    cardHigh = Palette.NightCardHigh,
    ink = Palette.NightInk,
    inkSoft = Palette.NightInkSoft,
    inkFaint = Palette.NightInkFaint,
    line = Palette.NightLine,
    accent = Palette.NightInk,
    accentSoft = Color(0xFF242424),
    onAccent = Palette.Ink,
    secondary = Palette.EmeraldNight,
    secondarySoft = Color(0xFF10291F),
    gold = Palette.EmberNight,
    goldSoft = Color(0xFF33190E),
    sage = Color(0xFFA8BF9B),
    sageSoft = Color(0xFF1E261B),
    danger = Color(0xFFFF6B57),
    success = Color(0xFF4ADE94),
    chart = listOf(
        Palette.NightInk, Palette.EmberNight, Palette.EmeraldNight, Color(0xFF8C98FF), Color(0xFFF2C14E),
        Color(0xFFD08BC0), Color(0xFF8FA7BE), Color(0xFFF29AB0), Color(0xFFD1BA98), Color(0xFF6CCFCB), Color(0xFF8E8E8E),
    ),
    isDark = true,
    bar = Color(0xFF101010),
)

val LocalTravelColors = staticCompositionLocalOf { LightTravelColors }
