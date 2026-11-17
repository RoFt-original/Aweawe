package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "chrononote_theme_prefs")

data class LocalThemeState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val palette: ThemePalette = ThemePalette.INDIGO,
    val notificationsEnabled: Boolean = true
)

class LocalThemePreferences(private val context: Context) {
    private val keyThemeMode = stringPreferencesKey("theme_mode")
    private val keyPalette = stringPreferencesKey("theme_palette")
    private val keyNotifications = booleanPreferencesKey("notifications_enabled")

    val themeStateFlow: Flow<LocalThemeState> = context.dataStore.data.map { prefs ->
        LocalThemeState(
            themeMode = ThemeMode.fromRaw(prefs[keyThemeMode]),
            palette = ThemePalette.fromRaw(prefs[keyPalette]),
            notificationsEnabled = prefs[keyNotifications] ?: true
        )
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[keyThemeMode] = mode.name
        }
    }

    suspend fun savePalette(palette: ThemePalette) {
        context.dataStore.edit { prefs ->
            prefs[keyPalette] = palette.name
        }
    }

    suspend fun saveNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[keyNotifications] = enabled
        }
    }

    suspend fun updateFromCloud(mode: ThemeMode, palette: ThemePalette, notificationsEnabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[keyThemeMode] = mode.name
            prefs[keyPalette] = palette.name
            prefs[keyNotifications] = notificationsEnabled
        }
    }
}
