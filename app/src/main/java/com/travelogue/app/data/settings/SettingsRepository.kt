package com.travelogue.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.travelogue.app.core.designsystem.theme.ThemeMode
import com.travelogue.app.data.db.enumOr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultCurrency: String = "INR",
    val demoLoaded: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val currency = stringPreferencesKey("default_currency")
        val demo = booleanPreferencesKey("demo_loaded")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = enumOr(p[Keys.theme], ThemeMode.SYSTEM),
            defaultCurrency = p[Keys.currency] ?: "INR",
            demoLoaded = p[Keys.demo] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(mode: ThemeMode) { context.dataStore.edit { it[Keys.theme] = mode.name } }
    suspend fun setDefaultCurrency(code: String) { context.dataStore.edit { it[Keys.currency] = code.uppercase() } }
    suspend fun setDemoLoaded(v: Boolean) { context.dataStore.edit { it[Keys.demo] = v } }
    suspend fun clear() { context.dataStore.edit { it.clear() } }
}
