package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.DawThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "spatial_daw_theme")

data class ThemeSettings(
    val preset: DawThemePreset = DawThemePreset.CYBER_NEON,
    val useDynamicColor: Boolean = false,
)

class ThemePreferences(private val context: Context) {

    private object Keys {
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    }

    val settingsFlow: Flow<ThemeSettings> = context.themeDataStore.data.map { prefs ->
        ThemeSettings(
            preset = DawThemePreset.fromName(prefs[Keys.THEME_PRESET]),
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
        )
    }

    suspend fun setThemePreset(preset: DawThemePreset) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.THEME_PRESET] = preset.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.USE_DYNAMIC_COLOR] = enabled
        }
    }
}
