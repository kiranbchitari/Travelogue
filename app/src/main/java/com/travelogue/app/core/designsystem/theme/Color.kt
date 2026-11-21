package com.travelogue.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Raw palette. Warm paper, charcoal ink and a few earthy accents. */
object Palette {
    val Paper = Color(0xFFF6F1EA)
    val PaperDeep = Color(0xFFEFE8DE)
    val Card = Color(0xFFFFFCF8)
    val Ink = Color(0xFF1E1C1A)
    val InkSoft = Color(0xFF5F5852)
    val InkFaint = Color(0xFF9A928A)
    val Line = Color(0xFFE6DED3)

    val Terracotta = Color(0xFFC5603C)
    val TerracottaSoft = Color(0xFFF5E1D7)
    val Teal = Color(0xFF2F5D62)
    val TealSoft = Color(0xFFDCE8E6)
    val Ochre = Color(0xFFC99A2E)
    val OchreSoft = Color(0xFFF6EBCF)
    val Sage = Color(0xFF7D8F5E)
    val SageSoft = Color(0xFFE6EBDA)
    val Plum = Color(0xFF7A4E6B)
    val Rose = Color(0xFFD98B84)
    val Slate = Color(0xFF52687D)
    val Sand = Color(0xFFB79C7A)
    val Danger = Color(0xFFB3412E)
    val Success = Color(0xFF4F7D4A)

    // Dark
    val Night = Color(0xFF141211)
    val NightCard = Color(0xFF1F1C1A)
    val NightCardHigh = Color(0xFF292522)
    val NightInk = Color(0xFFF3EDE5)
    val NightInkSoft = Color(0xFFBDB3A8)
    val NightInkFaint = Color(0xFF857B71)
    val NightLine = Color(0xFF34302C)
    val TerracottaNight = Color(0xFFE38A66)
    val TealNight = Color(0xFF7FB2B0)
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
)

val LightTravelColors = TravelColors(
    background = Palette.Paper,
    backgroundDeep = Palette.PaperDeep,
    card = Palette.Card,
    cardHigh = Color(0xFFFFFFFF),
    ink = Palette.Ink,
    inkSoft = Palette.InkSoft,
    inkFaint = Palette.InkFaint,
    line = Palette.Line,
    accent = Palette.Terracotta,
    accentSoft = Palette.TerracottaSoft,
    onAccent = Color.White,
    secondary = Palette.Teal,
    secondarySoft = Palette.TealSoft,
    gold = Palette.Ochre,
    goldSoft = Palette.OchreSoft,
    sage = Palette.Sage,
    sageSoft = Palette.SageSoft,
    danger = Palette.Danger,
    success = Palette.Success,
    chart = listOf(
        Palette.Terracotta, Palette.Teal, Palette.Ochre, Palette.Sage, Palette.Plum,
        Palette.Slate, Palette.Rose, Palette.Sand, Color(0xFF8C6A4F), Color(0xFF4E8A86), Color(0xFFA3A3A3),
    ),
    isDark = false,
)

val DarkTravelColors = TravelColors(
    background = Palette.Night,
    backgroundDeep = Color(0xFF0E0C0B),
    card = Palette.NightCard,
    cardHigh = Palette.NightCardHigh,
    ink = Palette.NightInk,
    inkSoft = Palette.NightInkSoft,
    inkFaint = Palette.NightInkFaint,
    line = Palette.NightLine,
    accent = Palette.TerracottaNight,
    accentSoft = Color(0xFF3A2620),
    onAccent = Color(0xFF1E1C1A),
    secondary = Palette.TealNight,
    secondarySoft = Color(0xFF1E3133),
    gold = Color(0xFFE0B654),
    goldSoft = Color(0xFF3A3020),
    sage = Color(0xFFA9BA88),
    sageSoft = Color(0xFF2A2F22),
    danger = Color(0xFFE07A66),
    success = Color(0xFF86B57F),
    chart = listOf(
        Palette.TerracottaNight, Palette.TealNight, Color(0xFFE0B654), Color(0xFFA9BA88), Color(0xFFB98AAB),
        Color(0xFF8FA7BE), Color(0xFFE6A39C), Color(0xFFD1BA98), Color(0xFFB8957A), Color(0xFF7DBDB8), Color(0xFF8E8E8E),
    ),
    isDark = true,
)

val LocalTravelColors = staticCompositionLocalOf { LightTravelColors }
