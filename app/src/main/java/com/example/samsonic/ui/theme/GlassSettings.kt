package com.example.samsonic.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import com.example.samsonic.data.ThemeManager

/**
 * User-tunable glass look, provided once at the app root so every
 * [glassSurface] and [BlurredArtBackdrop] picks it up without each call site
 * collecting the preference flows itself.
 */
@Immutable
data class GlassSettings(
    /** Multiplier over each surface's own base alpha (1f = design default). */
    val opacityScale: Float = ThemeManager.DefaultGlassOpacity,
    /** Frosted-glass blur of the floating chrome (nav bar, mini player). */
    val blurRadius: Dp = ThemeManager.DefaultGlassBlur,
    /** Blur of the album art behind the Now Playing screen. */
    val backdropBlur: Dp = ThemeManager.DefaultBackdropBlur,
)

val LocalGlassSettings = staticCompositionLocalOf { GlassSettings() }
