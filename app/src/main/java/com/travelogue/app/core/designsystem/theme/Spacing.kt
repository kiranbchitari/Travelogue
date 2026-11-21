package com.travelogue.app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
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
    /** Bottom padding so content clears the floating bottom bar. */
    val bottomBarClearance = 112.dp
}

object Shapes {
    val xs = RoundedCornerShape(8.dp)
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(18.dp)
    val lg = RoundedCornerShape(24.dp)
    val xl = RoundedCornerShape(32.dp)
    val pill = RoundedCornerShape(50)
}
