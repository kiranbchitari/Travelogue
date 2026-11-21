package com.travelogue.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun TravelogueTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = if (dark) DarkTravelColors else LightTravelColors
    val t = DefaultTravelTypography

    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.ink,
            secondary = c.secondary, onSecondary = c.background, secondaryContainer = c.secondarySoft, onSecondaryContainer = c.ink,
            tertiary = c.gold, background = c.background, onBackground = c.ink,
            surface = c.card, onSurface = c.ink, surfaceVariant = c.cardHigh, onSurfaceVariant = c.inkSoft,
            surfaceContainer = c.card, surfaceContainerHigh = c.cardHigh, surfaceContainerHighest = c.cardHigh,
            surfaceContainerLow = c.card, surfaceContainerLowest = c.background,
            outline = c.inkFaint, outlineVariant = c.line, error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.ink,
            secondary = c.secondary, onSecondary = c.cardHigh, secondaryContainer = c.secondarySoft, onSecondaryContainer = c.ink,
            tertiary = c.gold, background = c.background, onBackground = c.ink,
            surface = c.card, onSurface = c.ink, surfaceVariant = c.backgroundDeep, onSurfaceVariant = c.inkSoft,
            surfaceContainer = c.card, surfaceContainerHigh = c.cardHigh, surfaceContainerHighest = c.cardHigh,
            surfaceContainerLow = c.card, surfaceContainerLowest = c.cardHigh,
            outline = c.inkFaint, outlineVariant = c.line, error = c.danger,
        )
    }
    val m3Type = Typography(
        displaySmall = t.display, headlineMedium = t.headline, headlineSmall = t.headline,
        titleLarge = t.title, titleMedium = t.titleSmall, titleSmall = t.label,
        bodyLarge = t.bodyLarge, bodyMedium = t.body, bodySmall = t.bodySmall,
        labelLarge = t.label, labelMedium = t.label, labelSmall = t.overline,
    )
    CompositionLocalProvider(LocalTravelColors provides c, LocalTravelTypography provides t) {
        MaterialTheme(colorScheme = scheme, typography = m3Type, content = content)
    }
}

/** Shorthand accessors: `Travel.colors.ink`, `Travel.type.title`. */
object Travel {
    val colors: TravelColors
        @Composable @ReadOnlyComposable get() = LocalTravelColors.current
    val type: TravelTypography
        @Composable @ReadOnlyComposable get() = LocalTravelTypography.current
}
