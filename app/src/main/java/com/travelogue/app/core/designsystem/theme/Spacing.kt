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
    /** Bottom padding so content clears the docked bottom bar. */
    val bottomBarClearance = 96.dp
}

/** Tight, crisp corners: cards and buttons read as cut paper rather than soft blobs. */
object Shapes {
    val xs = RoundedCornerShape(4.dp)
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(10.dp)
    val lg = RoundedCornerShape(14.dp)
    val xl = RoundedCornerShape(22.dp)
    val pill = RoundedCornerShape(50)
}
