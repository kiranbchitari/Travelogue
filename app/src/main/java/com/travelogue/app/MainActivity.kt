package com.travelogue.app

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelogue.app.core.designsystem.theme.ThemeMode
import com.travelogue.app.core.designsystem.theme.TravelogueTheme
import com.travelogue.app.data.settings.AppSettings
import com.travelogue.app.ui.navigation.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as TravelogueApp).container
        var ready = false
        installSplashScreen().setKeepOnScreenCondition { !ready }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null as AppSettings?)
            val s = settings ?: return@setContent
            ready = true
            val dark = when (s.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // System bar icons follow the in-app theme, not just the system setting.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            // So does the window behind the app, which shows for a moment when the screen resizes.
            val page = s.theme.colors(dark).background
            LaunchedEffect(page) { window.setBackgroundDrawable(ColorDrawable(page.toArgb())) }
            // And the next start-up screen. Android draws it before the app is running, so it has to
            // be told ahead of time; it remembers the choice. Android 11 and older cannot be told.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                LaunchedEffect(s.theme, s.themeMode) { splashScreen.setSplashScreenTheme(splashStyle(s.theme, s.themeMode)) }
            }
            TravelogueTheme(mode = s.themeMode, theme = s.theme) {
                AppRoot()
            }
        }
    }
}
