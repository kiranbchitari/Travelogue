package com.travelogue.app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** 4dp grid. Use these instead of literal dp values for layout spacing. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp
    /** Horizontal page gutter. */
    val gutter = 20.dp
    /** Bottom padding so content clears the docked bottom bar. */
    val bottomBarClearance = 96.dp
}

/** Corner radii of one theme. [button] is the main call-to-action shape. */
@Immutable
data class TravelShapes(
    val xs: RoundedCornerShape,
    val sm: RoundedCornerShape,
    val md: RoundedCornerShape,
    val lg: RoundedCornerShape,
    val xl: RoundedCornerShape,
    val button: RoundedCornerShape = sm,
)

fun travelShapes(xs: Int, sm: Int, md: Int, lg: Int, xl: Int, button: Int = sm) = TravelShapes(
    RoundedCornerShape(xs.dp), RoundedCornerShape(sm.dp), RoundedCornerShape(md.dp),
    RoundedCornerShape(lg.dp), RoundedCornerShape(xl.dp), RoundedCornerShape(button.dp),
)

/** Tight, crisp corners: cards and buttons read as cut paper rather than soft blobs. */
val CrispShapes = travelShapes(xs = 4, sm = 8, md = 10, lg = 14, xl = 22)

val LocalTravelShapes = staticCompositionLocalOf { CrispShapes }

/** Corner shapes of the current theme. Use these instead of literal radii. */
object Shapes {
    val xs: RoundedCornerShape @Composable @ReadOnlyComposable get() = LocalTravelShapes.current.xs
    val sm: RoundedCornerShape @Composable @ReadOnlyComposable get() = LocalTravelShapes.current.sm
    val md: RoundedCornerShape @Composable @ReadOnlyComposable get() = LocalTravelShapes.current.md
    val lg: RoundedCornerShape @Composable @ReadOnlyComposable get() = LocalTravelShapes.current.lg
    val xl: RoundedCornerShape @Composable @ReadOnlyComposable get() = LocalTravelShapes.current.xl
    val button: RoundedCornerShape @Composable @ReadOnlyComposable get() = LocalTravelShapes.current.button
    val pill = RoundedCornerShape(50)
}
