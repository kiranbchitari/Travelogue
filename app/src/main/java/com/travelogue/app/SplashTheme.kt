package com.travelogue.app

import android.content.res.Resources
import androidx.annotation.StyleRes
import com.travelogue.app.core.designsystem.theme.AppTheme
import com.travelogue.app.core.designsystem.theme.ThemeMode

/**
 * The start-up screen style (res/values/splash_themes.xml) for a theme and brightness, or
 * [Resources.ID_NULL] for the one in the manifest. With brightness on System the style picks its
 * own light or dark colours; a brightness set by hand needs the fixed variant, because the start-up
 * screen is drawn by Android before the app is running and knows only the system setting.
 */
@StyleRes
fun splashStyle(theme: AppTheme, mode: ThemeMode): Int {
    fun pick(@StyleRes system: Int, @StyleRes light: Int, @StyleRes dark: Int) = when (mode) {
        ThemeMode.SYSTEM -> system
        ThemeMode.LIGHT -> light
        ThemeMode.DARK -> dark
    }
    return when (theme) {
        AppTheme.CLASSIC -> pick(Resources.ID_NULL, R.style.Theme_Travelogue_Splash_Classic_Light, R.style.Theme_Travelogue_Splash_Classic_Dark)
        AppTheme.SEASIDE -> pick(R.style.Theme_Travelogue_Splash_Seaside, R.style.Theme_Travelogue_Splash_Seaside_Light, R.style.Theme_Travelogue_Splash_Seaside_Dark)
        AppTheme.SUMMIT -> pick(R.style.Theme_Travelogue_Splash_Summit, R.style.Theme_Travelogue_Splash_Summit_Light, R.style.Theme_Travelogue_Splash_Summit_Dark)
        AppTheme.SUNDOWN -> pick(R.style.Theme_Travelogue_Splash_Sundown, R.style.Theme_Travelogue_Splash_Sundown_Light, R.style.Theme_Travelogue_Splash_Sundown_Dark)
        AppTheme.AIRMAIL -> pick(R.style.Theme_Travelogue_Splash_Airmail, R.style.Theme_Travelogue_Splash_Airmail_Light, R.style.Theme_Travelogue_Splash_Airmail_Dark)
        AppTheme.FIESTA -> pick(R.style.Theme_Travelogue_Splash_Fiesta, R.style.Theme_Travelogue_Splash_Fiesta_Light, R.style.Theme_Travelogue_Splash_Fiesta_Dark)
    }
}
