package com.travelogue.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** The illustration a theme draws behind headers, on photo-less covers and in empty states. */
enum class Scenery { RINGS, BEACH, MOUNTAIN, SUNSET, AIRMAIL, FIESTA }

/** How the bottom bar marks the current tab: a short rule above it, or a bright pill behind its icon. */
enum class BarStyle { RULE, PILL }

/**
 * A complete look the user picks in Settings. Each one brings its own colours (light and dark),
 * corner shapes, headline lettering, bottom bar and scenery, so switching changes the whole mood of
 * the app rather than just a tint. [CLASSIC] is the original monochrome look.
 *
 * The name is what is saved, so entries can be reordered but not renamed.
 */
enum class AppTheme(
    val title: String,
    val tagline: String,
    val scenery: Scenery,
    val flavour: TypeFlavour,
    val barStyle: BarStyle,
) {
    CLASSIC("Ink & paper", "Quiet, monochrome, editorial", Scenery.RINGS, TypeFlavour.CRISP, BarStyle.RULE),
    SEASIDE("Beach day", "Sun, surf and swaying palms", Scenery.BEACH, TypeFlavour.PLAYFUL, BarStyle.PILL),
    SUMMIT("Mountain air", "Pine trails and snowy peaks", Scenery.MOUNTAIN, TypeFlavour.SOFT, BarStyle.PILL),
    SUNDOWN("Golden hour", "Desert roads at sunset", Scenery.SUNSET, TypeFlavour.PLAYFUL, BarStyle.PILL),
    AIRMAIL("Postcard", "Stamps, postmarks and paper planes", Scenery.AIRMAIL, TypeFlavour.SOFT, BarStyle.RULE),
    FIESTA("Carnival", "Balloons, bunting and confetti", Scenery.FIESTA, TypeFlavour.PLAYFUL, BarStyle.PILL);

    /** Whether screens get the scenery painted behind their top area. The classic look stays plain. */
    val hasBackdrop: Boolean get() = this != CLASSIC

    fun colors(dark: Boolean): TravelColors = when (this) {
        CLASSIC -> if (dark) DarkTravelColors else LightTravelColors
        SEASIDE -> if (dark) SeasideDark else SeasideLight
        SUMMIT -> if (dark) SummitDark else SummitLight
        SUNDOWN -> if (dark) SundownDark else SundownLight
        AIRMAIL -> if (dark) AirmailDark else AirmailLight
        FIESTA -> if (dark) FiestaDark else FiestaLight
    }

    fun scene(dark: Boolean): ScenePalette = when (this) {
        CLASSIC -> if (dark) ClassicNightScene else ClassicScene
        SEASIDE -> if (dark) SeasideNightScene else SeasideScene
        SUMMIT -> if (dark) SummitNightScene else SummitScene
        SUNDOWN -> if (dark) SundownNightScene else SundownScene
        AIRMAIL -> if (dark) AirmailNightScene else AirmailScene
        FIESTA -> if (dark) FiestaNightScene else FiestaScene
    }

    val shapes: TravelShapes
        get() = when (this) {
            CLASSIC -> CrispShapes
            SEASIDE -> travelShapes(xs = 8, sm = 14, md = 16, lg = 24, xl = 30, button = 28)
            SUMMIT -> travelShapes(xs = 6, sm = 12, md = 14, lg = 20, xl = 28, button = 16)
            SUNDOWN -> travelShapes(xs = 8, sm = 14, md = 16, lg = 22, xl = 30, button = 28)
            AIRMAIL -> travelShapes(xs = 2, sm = 5, md = 6, lg = 8, xl = 16, button = 6)
            FIESTA -> travelShapes(xs = 9, sm = 16, md = 18, lg = 28, xl = 34, button = 28)
        }
}

val LocalAppTheme = staticCompositionLocalOf { AppTheme.CLASSIC }

/**
 * Colours of a theme's scenery, back to front. [sun] is the moon at night, [mist] is clouds by day
 * and stars by night, and [detail] is the small foreground things (palms, pines, cacti, the plane).
 */
@Immutable
data class ScenePalette(
    val skyTop: Color,
    val skyBottom: Color,
    val sun: Color,
    val far: Color,
    val mid: Color,
    val near: Color,
    val detail: Color,
    val mist: Color,
    val night: Boolean,
) {
    /** Every colour pulled [amount] of the way to [target]: a quiet version to sit behind text. */
    fun toward(target: Color, amount: Float) = copy(
        skyTop = lerp(skyTop, target, amount), skyBottom = lerp(skyBottom, target, amount), sun = lerp(sun, target, amount),
        far = lerp(far, target, amount), mid = lerp(mid, target, amount), near = lerp(near, target, amount),
        detail = lerp(detail, target, amount), mist = lerp(mist, target, amount),
    )

    /** A different time of day for the same scene (0 = as designed), so covers don't all match. */
    fun mood(variant: Int): ScenePalette {
        val (top, bottom) = when (variant % 3) {
            1 -> if (night) Color(0xFF2B1B5A) to Color(0xFF5A2E63) else Color(0xFFFFA869) to Color(0xFFFFE3B5)
            2 -> if (night) Color(0xFF07122B) to Color(0xFF1B3550) else Color(0xFF7B6AD0) to Color(0xFFFFB7A8)
            else -> return this
        }
        // Mostly the new sky: a half-and-half mix of two opposite hues just turns grey.
        val amount = if (night) 0.6f else 0.8f
        return copy(skyTop = lerp(skyTop, top, amount), skyBottom = lerp(skyBottom, bottom, amount * 0.75f))
    }
}

private val ChartTailLight = listOf(
    Color(0xFF3D4EDB), Color(0xFFE0A21A), Color(0xFF8A3F7A), Color(0xFF4A6078),
    Color(0xFFE0718A), Color(0xFFB59A76), Color(0xFF2E9C99), Color(0xFFA3A3A3),
)
private val ChartTailDark = listOf(
    Color(0xFF8C98FF), Color(0xFFF2C14E), Color(0xFFD08BC0), Color(0xFF8FA7BE),
    Color(0xFFF29AB0), Color(0xFFD1BA98), Color(0xFF6CCFCB), Color(0xFF8E8E8E),
)

/** Builds a theme's colours from its few defining ones; tints ("soft" colours) are mixed from them. */
private fun palette(
    dark: Boolean,
    background: Long, backgroundDeep: Long, card: Long, cardHigh: Long,
    ink: Long, inkSoft: Long, inkFaint: Long, line: Long,
    accent: Long, onAccent: Long, secondary: Long, gold: Long, sage: Long,
    bar: Long, barAccent: Long,
): TravelColors {
    fun soft(c: Long) = lerp(Color(card), Color(c), if (dark) 0.22f else 0.14f)
    return TravelColors(
        background = Color(background), backgroundDeep = Color(backgroundDeep), card = Color(card), cardHigh = Color(cardHigh),
        ink = Color(ink), inkSoft = Color(inkSoft), inkFaint = Color(inkFaint), line = Color(line),
        accent = Color(accent), accentSoft = soft(accent), onAccent = Color(onAccent),
        secondary = Color(secondary), secondarySoft = soft(secondary),
        gold = Color(gold), goldSoft = soft(gold),
        sage = Color(sage), sageSoft = soft(sage),
        danger = if (dark) Color(0xFFFF6B57) else Color(0xFFC8321E),
        success = if (dark) Color(0xFF4ADE94) else Color(0xFF1B8A5A),
        chart = listOf(Color(accent), Color(gold), Color(secondary)) + if (dark) ChartTailDark else ChartTailLight,
        isDark = dark,
        bar = Color(bar), onBar = Color.White, barAccent = Color(barAccent),
    )
}

private fun scene(night: Boolean, skyTop: Long, skyBottom: Long, sun: Long, far: Long, mid: Long, near: Long, detail: Long, mist: Long = 0xFFFFFFFF) =
    ScenePalette(Color(skyTop), Color(skyBottom), Color(sun), Color(far), Color(mid), Color(near), Color(detail), Color(mist), night)

// Ink & paper ---------------------------------------------------------------------------------
private val ClassicScene = scene(false, 0xFFF5F5F3, 0xFFF5F5F3, 0xFF0E0E0E, 0xFFDADAD5, 0xFFC9C9C3, 0xFF0E0E0E, 0xFFDD552B)
private val ClassicNightScene = scene(true, 0xFF060606, 0xFF060606, 0xFFF5F5F5, 0xFF2E2E2E, 0xFF3A3A3A, 0xFFF5F5F5, 0xFFFF7A45)

// Beach day: warm sand, lagoon teal, coral ----------------------------------------------------
private val SeasideLight = palette(
    dark = false,
    background = 0xFFFFF7E8, backgroundDeep = 0xFFF9EBD2, card = 0xFFFFFFFF, cardHigh = 0xFFFFFFFF,
    ink = 0xFF0F3440, inkSoft = 0xFF48636C, inkFaint = 0xFF8AA0A6, line = 0xFFF0E2C8,
    accent = 0xFF007A87, onAccent = 0xFFFFFFFF, secondary = 0xFF1B8A68, gold = 0xFFE9572F, sage = 0xFF6F9460,
    bar = 0xFF0C3742, barAccent = 0xFFFFC94D,
)
private val SeasideDark = palette(
    dark = true,
    background = 0xFF071C22, backgroundDeep = 0xFF04141A, card = 0xFF0E2A32, cardHigh = 0xFF153842,
    ink = 0xFFEAF7F8, inkSoft = 0xFFA9C5CA, inkFaint = 0xFF6F8E94, line = 0xFF1D3F49,
    accent = 0xFF4FD4DF, onAccent = 0xFF04262C, secondary = 0xFF4BD6A4, gold = 0xFFFF8B66, sage = 0xFFA6C79A,
    bar = 0xFF0A242B, barAccent = 0xFFFFC94D,
)
private val SeasideScene = scene(false, 0xFF6FD2E0, 0xFFFFE8BD, 0xFFFFC53D, 0xFF52C6D2, 0xFF1AA0B2, 0xFFF4D49A, 0xFF1C7F5B)
private val SeasideNightScene = scene(true, 0xFF0A2644, 0xFF1A4A63, 0xFFFFF1C9, 0xFF1A6A80, 0xFF11505F, 0xFF3A5450, 0xFF0C3B31)

// Mountain air: mist, pine, campfire ----------------------------------------------------------
private val SummitLight = palette(
    dark = false,
    background = 0xFFF1F6F1, backgroundDeep = 0xFFE3ECE3, card = 0xFFFFFFFF, cardHigh = 0xFFFFFFFF,
    ink = 0xFF1A2B23, inkSoft = 0xFF506359, inkFaint = 0xFF8C9C93, line = 0xFFDAE4DA,
    accent = 0xFF2B6A4D, onAccent = 0xFFFFFFFF, secondary = 0xFF2A77AE, gold = 0xFFD9791F, sage = 0xFF7B9070,
    bar = 0xFF15302A, barAccent = 0xFFF5B94A,
)
private val SummitDark = palette(
    dark = true,
    background = 0xFF0B1512, backgroundDeep = 0xFF070F0C, card = 0xFF13211C, cardHigh = 0xFF1B2D27,
    ink = 0xFFEDF5F0, inkSoft = 0xFFADC0B6, inkFaint = 0xFF75897F, line = 0xFF223730,
    accent = 0xFF7FD1A6, onAccent = 0xFF072117, secondary = 0xFF7DBFEA, gold = 0xFFF5A94E, sage = 0xFFA9C09C,
    bar = 0xFF0E1B17, barAccent = 0xFFF5B94A,
)
private val SummitScene = scene(false, 0xFF9AD1EE, 0xFFE8F5F1, 0xFFFFD66B, 0xFFA3BDD0, 0xFF5E8F7C, 0xFF2E6B4F, 0xFFFFFFFF)
private val SummitNightScene = scene(true, 0xFF0C1B33, 0xFF1D3A4A, 0xFFF4F1DE, 0xFF2C4760, 0xFF21433A, 0xFF132A22, 0xFFDCE8EC)

// Golden hour: peach, rose, plum --------------------------------------------------------------
private val SundownLight = palette(
    dark = false,
    background = 0xFFFFF3E8, backgroundDeep = 0xFFFBE4D2, card = 0xFFFFFFFF, cardHigh = 0xFFFFFFFF,
    ink = 0xFF3A1C3F, inkSoft = 0xFF6C4F68, inkFaint = 0xFFA48EA0, line = 0xFFF4DCCA,
    accent = 0xFFC62F4C, onAccent = 0xFFFFFFFF, secondary = 0xFF7A3E9D, gold = 0xFFE4701B, sage = 0xFFA9794C,
    bar = 0xFF36183C, barAccent = 0xFFFFB347,
)
private val SundownDark = palette(
    dark = true,
    background = 0xFF170C1C, backgroundDeep = 0xFF100714, card = 0xFF241429, cardHigh = 0xFF311D37,
    ink = 0xFFFBEFF3, inkSoft = 0xFFCDB2C6, inkFaint = 0xFF8E7389, line = 0xFF3A2440,
    accent = 0xFFFF7D8F, onAccent = 0xFF3A0A16, secondary = 0xFFC79BEA, gold = 0xFFFFA64D, sage = 0xFFD6AE86,
    bar = 0xFF1C0F22, barAccent = 0xFFFFB347,
)
private val SundownScene = scene(false, 0xFFF0577A, 0xFFFFC77A, 0xFFFFF0B0, 0xFFB5448B, 0xFF7B2F7A, 0xFF3F1B52, 0xFF2A1038)
private val SundownNightScene = scene(true, 0xFF24103F, 0xFF8C3565, 0xFFFFD9A0, 0xFF5A2468, 0xFF3A174C, 0xFF1E0C2C, 0xFF120618)

// Postcard: aged paper, airmail blue and red --------------------------------------------------
private val AirmailLight = palette(
    dark = false,
    background = 0xFFF6EFE0, backgroundDeep = 0xFFEDE2CC, card = 0xFFFFFCF5, cardHigh = 0xFFFFFFFF,
    ink = 0xFF1F2A44, inkSoft = 0xFF545E78, inkFaint = 0xFF9097A8, line = 0xFFE3D6BC,
    accent = 0xFF1F4E9C, onAccent = 0xFFFFFFFF, secondary = 0xFF2B7A58, gold = 0xFFCF3B31, sage = 0xFF85855A,
    bar = 0xFF1B2540, barAccent = 0xFFEF6A5E,
)
private val AirmailDark = palette(
    dark = true,
    background = 0xFF10141F, backgroundDeep = 0xFF0A0E17, card = 0xFF1A2030, cardHigh = 0xFF232B3F,
    ink = 0xFFF3EEDF, inkSoft = 0xFFB5B8C6, inkFaint = 0xFF7C8196, line = 0xFF2B3348,
    accent = 0xFF8FB4FF, onAccent = 0xFF0B1B3A, secondary = 0xFF6FCFA0, gold = 0xFFFF7C70, sage = 0xFFC2C28E,
    bar = 0xFF0C101A, barAccent = 0xFFFF7C70,
)
private val AirmailScene = scene(false, 0xFFBFE0EE, 0xFFF6EFE0, 0xFFD9443A, 0xFFE2D4B2, 0xFFD3C299, 0xFFC2AE80, 0xFF1F4E9C, mist = 0xFFFFFCF5)
private val AirmailNightScene = scene(true, 0xFF17203A, 0xFF1D2740, 0xFFFF7C70, 0xFF2B3653, 0xFF232D47, 0xFF1A2238, 0xFF8FB4FF, mist = 0xFFE9E4D4)

// Carnival: candy pink, turquoise, marigold ---------------------------------------------------
private val FiestaLight = palette(
    dark = false,
    background = 0xFFFFF6F1, backgroundDeep = 0xFFFFE8DF, card = 0xFFFFFFFF, cardHigh = 0xFFFFFFFF,
    ink = 0xFF2B1B4D, inkSoft = 0xFF5D4F7E, inkFaint = 0xFF9C92B5, line = 0xFFF6DDD6,
    accent = 0xFFD4186F, onAccent = 0xFFFFFFFF, secondary = 0xFF00897A, gold = 0xFFE87A00, sage = 0xFF6E962F,
    bar = 0xFF2B1B4D, barAccent = 0xFFFFD23F,
)
private val FiestaDark = palette(
    dark = true,
    background = 0xFF150E26, backgroundDeep = 0xFF0E081B, card = 0xFF211736, cardHigh = 0xFF2D2047,
    ink = 0xFFFBF3FF, inkSoft = 0xFFC5B8DE, inkFaint = 0xFF8A7CA6, line = 0xFF35284F,
    accent = 0xFFFF6FB5, onAccent = 0xFF3A0520, secondary = 0xFF4FE0C6, gold = 0xFFFFB23F, sage = 0xFFB6DB6B,
    bar = 0xFF190F2E, barAccent = 0xFFFFD23F,
)
private val FiestaScene = scene(false, 0xFF6FD8F2, 0xFFFFF0C9, 0xFFFFD23F, 0xFFFF8FB8, 0xFFB565E0, 0xFF3DC7A4, 0xFFFF5A5F)
private val FiestaNightScene = scene(true, 0xFF1B1445, 0xFF3F2670, 0xFFFFE27A, 0xFF8A4592, 0xFF55348F, 0xFF1F6470, 0xFFFF6FB5)
