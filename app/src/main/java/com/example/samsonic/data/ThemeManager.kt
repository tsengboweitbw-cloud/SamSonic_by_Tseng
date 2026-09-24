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

/** How song rows show a song's audio format (see SongFormat in the UI). */
enum class AudioFormatDisplay {
    OFF,
    /** "Artist · FLAC 96/24" as the row's second line. */
    UNDER_ARTIST,
    /** The format over the duration; the heart button goes, a small heart marks liked songs. */
    QUIET_HEART,
    /** A small outlined codec badge ("FLAC") by the duration. */
    CODEC_BADGE,
    /** A "Hi-Res" badge on hi-res songs only. */
    HI_RES_BADGE,
}

/**
 * Persists the user's theme mode, accent color and other UI preferences via plain SharedPreferences - unlike
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

    private val _glassOpacity = MutableStateFlow(prefs.getFloat(KEY_GLASS_OPACITY, DefaultGlassOpacity))
    // Multiplier over each glass surface's own base alpha, so nav, cards and
    // sheets keep their relative density while the user scales them together.
    val glassOpacity: StateFlow<Float> = _glassOpacity.asStateFlow()

    private val _glassBlur = MutableStateFlow(prefs.getFloat(KEY_GLASS_BLUR, DefaultGlassBlur.value).dp)
    val glassBlur: StateFlow<Dp> = _glassBlur.asStateFlow()

    private val _backdropBlur = MutableStateFlow(prefs.getFloat(KEY_BACKDROP_BLUR, DefaultBackdropBlur.value).dp)
    val backdropBlur: StateFlow<Dp> = _backdropBlur.asStateFlow()

    // The secondary menus' own pair (the Library view options, the Now Playing
    // panels): they float over dimmed content, so they get their own look.
    private val _panelOpacity = MutableStateFlow(prefs.getFloat(KEY_PANEL_OPACITY, DefaultPanelOpacity))
    val panelOpacity: StateFlow<Float> = _panelOpacity.asStateFlow()

    private val _panelBlur = MutableStateFlow(prefs.getFloat(KEY_PANEL_BLUR, DefaultPanelBlur.value).dp)
    val panelBlur: StateFlow<Dp> = _panelBlur.asStateFlow()

    // Whether the heart (like/unlike) buttons are shown at all.
    private val _likesEnabled = MutableStateFlow(prefs.getBoolean(KEY_LIKES_ENABLED, true))
    val likesEnabled: StateFlow<Boolean> = _likesEnabled.asStateFlow()

    // How song rows show each song's audio format, if at all. Off until chosen.
    private val _audioFormatDisplay = MutableStateFlow(loadAudioFormatDisplay())
    val audioFormatDisplay: StateFlow<AudioFormatDisplay> = _audioFormatDisplay.asStateFlow()

    fun setAudioFormatDisplay(display: AudioFormatDisplay) {
        prefs.edit().putString(KEY_AUDIO_FORMAT_DISPLAY, display.name).apply()
        _audioFormatDisplay.value = display
    }

    private fun loadAudioFormatDisplay(): AudioFormatDisplay {
        prefs.getString(KEY_AUDIO_FORMAT_DISPLAY, null)?.let { saved ->
            return AudioFormatDisplay.entries.find { it.name == saved } ?: AudioFormatDisplay.OFF
        }
        // The earlier on/off switch: on becomes the nearest layout.
        return if (prefs.getBoolean(KEY_SHOW_AUDIO_FORMAT, false)) AudioFormatDisplay.UNDER_ARTIST else AudioFormatDisplay.OFF
    }

    // Which Now Playing swipes open a panel: right for lyrics, left for song info, up for the queue.
    // Only the queue's is on until the user turns the others on.
    // Each panel still opens from its button either way.
    private val _swipeForLyrics = MutableStateFlow(prefs.getBoolean(KEY_SWIPE_LYRICS, false))
    val swipeForLyrics: StateFlow<Boolean> = _swipeForLyrics.asStateFlow()

    private val _swipeForInfo = MutableStateFlow(prefs.getBoolean(KEY_SWIPE_INFO, false))
    val swipeForInfo: StateFlow<Boolean> = _swipeForInfo.asStateFlow()

    private val _swipeForQueue = MutableStateFlow(prefs.getBoolean(KEY_SWIPE_QUEUE, true))
    val swipeForQueue: StateFlow<Boolean> = _swipeForQueue.asStateFlow()

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

    fun setGlassOpacity(scale: Float) {
        prefs.edit().putFloat(KEY_GLASS_OPACITY, scale).apply()
        _glassOpacity.value = scale
    }

    fun setGlassBlur(radius: Dp) {
        prefs.edit().putFloat(KEY_GLASS_BLUR, radius.value).apply()
        _glassBlur.value = radius
    }

    fun setBackdropBlur(radius: Dp) {
        prefs.edit().putFloat(KEY_BACKDROP_BLUR, radius.value).apply()
        _backdropBlur.value = radius
    }

    fun setPanelOpacity(scale: Float) {
        prefs.edit().putFloat(KEY_PANEL_OPACITY, scale).apply()
        _panelOpacity.value = scale
    }

    fun setPanelBlur(radius: Dp) {
        prefs.edit().putFloat(KEY_PANEL_BLUR, radius.value).apply()
        _panelBlur.value = radius
    }

    fun setLikesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIKES_ENABLED, enabled).apply()
        _likesEnabled.value = enabled
    }

    fun setSwipeForLyrics(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SWIPE_LYRICS, enabled).apply()
        _swipeForLyrics.value = enabled
    }

    fun setSwipeForInfo(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SWIPE_INFO, enabled).apply()
        _swipeForInfo.value = enabled
    }

    fun setSwipeForQueue(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SWIPE_QUEUE, enabled).apply()
        _swipeForQueue.value = enabled
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
        private const val KEY_GLASS_OPACITY = "glass_opacity"
        private const val KEY_GLASS_BLUR = "glass_blur"
        private const val KEY_BACKDROP_BLUR = "backdrop_blur"
        private const val KEY_PANEL_OPACITY = "panel_opacity"
        private const val KEY_PANEL_BLUR = "panel_blur"
        private const val KEY_LIKES_ENABLED = "likes_enabled"
        private const val KEY_SHOW_AUDIO_FORMAT = "show_audio_format"
        private const val KEY_AUDIO_FORMAT_DISPLAY = "audio_format_display"
        private const val KEY_SWIPE_LYRICS = "swipe_for_lyrics"
        private const val KEY_SWIPE_INFO = "swipe_for_info"
        private const val KEY_SWIPE_QUEUE = "swipe_for_queue"
        val DefaultAccent = Color(0xFF8875FF)
        val DefaultAlbumArtCornerRadius = 2.dp
        const val DefaultGlassOpacity = 1f
        val DefaultGlassBlur = 16.dp
        val DefaultBackdropBlur = 40.dp
        const val DefaultPanelOpacity = 1f
        val DefaultPanelBlur = 28.dp
    }
}
