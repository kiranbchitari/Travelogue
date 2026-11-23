package com.travelogue.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.travelogue.app.R

private fun fraunces(weight: Int, soft: Float, wonk: Float) = Font(
    R.font.fraunces,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("SOFT", soft), FontVariation.Setting("WONK", wonk)),
)

/** The headline serif. [soft] rounds its corners (0 to 100) and [wonk] (0 or 1) swaps in its livelier letterforms. */
private fun serif(soft: Float, wonk: Float) =
    FontFamily(fraunces(400, soft, wonk), fraunces(500, soft, wonk), fraunces(600, soft, wonk), fraunces(700, soft, wonk))

private fun jakarta(weight: Int) = Font(
    R.font.jakarta,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** High-contrast serif for headlines and numbers, set tight and heavy. */
val Serif = serif(soft = 0f, wonk = 0f)

/** Clean humanist sans for UI and body text. */
val Sans = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700))

@Immutable
data class TravelTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val overline: TextStyle,
    val number: TextStyle,
    val numberSmall: TextStyle,
    val journal: TextStyle,
)

private fun typography(serif: FontFamily, heavy: Int) = TravelTypography(
    display = TextStyle(fontFamily = serif, fontWeight = FontWeight(heavy), fontSize = 36.sp, lineHeight = 39.sp, letterSpacing = (-0.035).em),
    headline = TextStyle(fontFamily = serif, fontWeight = FontWeight(heavy), fontSize = 27.sp, lineHeight = 31.sp, letterSpacing = (-0.03).em),
    title = TextStyle(fontFamily = serif, fontWeight = FontWeight(heavy), fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.02).em),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight(600), fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.005).em),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    body = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 17.sp),
    label = TextStyle(fontFamily = Sans, fontWeight = FontWeight(600), fontSize = 13.sp, lineHeight = 18.sp),
    overline = TextStyle(fontFamily = Sans, fontWeight = FontWeight(700), fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.18.em),
    number = TextStyle(fontFamily = serif, fontWeight = FontWeight(600), fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.03).em),
    numberSmall = TextStyle(fontFamily = serif, fontWeight = FontWeight(600), fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = (-0.02).em),
    journal = TextStyle(fontFamily = serif, fontWeight = FontWeight(400), fontSize = 17.sp, lineHeight = 28.sp),
)

/** How the headline serif is cut: sharp and formal, rounded, or rounded with playful letterforms and more weight. */
enum class TypeFlavour { CRISP, SOFT, PLAYFUL }

val DefaultTravelTypography = typography(Serif, heavy = 600)
private val SoftTravelTypography by lazy { typography(serif(soft = 100f, wonk = 0f), heavy = 600) }
private val PlayfulTravelTypography by lazy { typography(serif(soft = 100f, wonk = 1f), heavy = 700) }

fun travelTypography(flavour: TypeFlavour): TravelTypography = when (flavour) {
    TypeFlavour.CRISP -> DefaultTravelTypography
    TypeFlavour.SOFT -> SoftTravelTypography
    TypeFlavour.PLAYFUL -> PlayfulTravelTypography
}

val LocalTravelTypography = staticCompositionLocalOf { DefaultTravelTypography }
