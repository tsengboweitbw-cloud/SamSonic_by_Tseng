package com.example.samsonic.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Persists the user's theme mode and accent color choice via plain SharedPreferences - unlike
 * [SessionManager], this is non-sensitive UI preference data, so no encryption is needed.
 */
class ThemeManager(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("samsonic_theme", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow(loadAccentColor())
    val accentColor: StateFlow<Color> = _accentColor.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setAccentColor(color: Color) {
        prefs.edit().putInt(KEY_ACCENT, color.toArgb()).apply()
        _accentColor.value = color
    }

    fun resetAccentColor() = setAccentColor(DefaultAccent)

    private fun loadThemeMode(): ThemeMode =
        ThemeMode.entries.find { it.name == prefs.getString(KEY_MODE, null) } ?: ThemeMode.SYSTEM

    private fun loadAccentColor(): Color =
        if (prefs.contains(KEY_ACCENT)) Color(prefs.getInt(KEY_ACCENT, 0)) else DefaultAccent

    companion object {
        private const val KEY_MODE = "theme_mode"
        private const val KEY_ACCENT = "accent_color"
        val DefaultAccent = Color(0xFF8875FF)
    }
}
