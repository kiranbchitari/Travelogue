package com.travelogue.app.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.travelogue.app.core.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every theme, in light and dark, must keep text readable on the surfaces it sits on. */
class AppThemeTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private fun check(what: String, fg: Color, bg: Color, atLeast: Float) {
        val ratio = contrast(fg, bg)
        assertTrue("$what contrast is $ratio, needs $atLeast", ratio >= atLeast)
    }

    @Test fun textStaysReadableInEveryTheme() {
        for (theme in AppTheme.entries) for (dark in listOf(false, true)) {
            val c = theme.colors(dark)
            val name = "${theme.name} ${if (dark) "dark" else "light"}:"
            check("$name ink on background", c.ink, c.background, 7f)
            check("$name ink on card", c.ink, c.card, 7f)
            check("$name soft ink on background", c.inkSoft, c.background, 4.5f)
            check("$name button label", c.onAccent, c.accent, 4.5f)
            check("$name accent on card", c.accent, c.card, 4.5f)
            check("$name bottom bar label", c.onBar, c.bar, 7f)
            check("$name bottom bar mark", c.barAccent, c.bar, 3f)
        }
    }

    @Test fun everyThemeHasTheSameNumberOfChartColours() {
        // Screens pick chart colours by position, so no theme may come up short.
        val expected = AppTheme.CLASSIC.colors(false).chart.size
        for (theme in AppTheme.entries) for (dark in listOf(false, true)) {
            assertEquals("${theme.name} dark=$dark", expected, theme.colors(dark).chart.size)
        }
    }

    @Test fun darkAndLightPalettesAreLabelledCorrectly() {
        for (theme in AppTheme.entries) {
            assertTrue(theme.colors(true).isDark)
            assertTrue(!theme.colors(false).isDark)
            assertEquals(true, theme.scene(true).night)
            assertEquals(false, theme.scene(false).night)
        }
    }
}
