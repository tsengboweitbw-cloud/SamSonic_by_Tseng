package com.example.samsonic.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

    private val _albumArtCornerRadius = MutableStateFlow(loadAlbumArtCornerRadius())
    val albumArtCornerRadius: StateFlow<Dp> = _albumArtCornerRadius.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setAccentColor(color: Color) {
        prefs.edit().putInt(KEY_ACCENT, color.toArgb()).apply()
        _accentColor.value = color
    }

    fun resetAccentColor() = setAccentColor(DefaultAccent)

    fun setAlbumArtCornerRadius(radius: Dp) {
        prefs.edit().putFloat(KEY_ART_RADIUS, radius.value).apply()
        _albumArtCornerRadius.value = radius
    }

    private fun loadThemeMode(): ThemeMode =
        ThemeMode.entries.find { it.name == prefs.getString(KEY_MODE, null) } ?: ThemeMode.SYSTEM

    private fun loadAccentColor(): Color =
        if (prefs.contains(KEY_ACCENT)) Color(prefs.getInt(KEY_ACCENT, 0)) else DefaultAccent

    private fun loadAlbumArtCornerRadius(): Dp =
        prefs.getFloat(KEY_ART_RADIUS, DefaultAlbumArtCornerRadius.value).dp

    companion object {
        private const val KEY_MODE = "theme_mode"
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_ART_RADIUS = "album_art_corner_radius"
        val DefaultAccent = Color(0xFF8875FF)
        val DefaultAlbumArtCornerRadius = 24.dp
    }
}
