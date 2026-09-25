package com.example.samsonic.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Nudges [color] towards black (light theme) or white (dark theme) if its
 * luminance is too close to that theme's own background to read as a
 * foreground color - e.g. a white accent picked in light mode, or a
 * near-black one picked in dark mode. Leaves already-safe colors untouched,
 * so most accent choices keep their exact hue/brightness.
 */
private fun ensureReadableOverBackground(color: Color, darkTheme: Boolean): Color = when {
    darkTheme && color.luminance() < 0.35f -> lerp(color, Color.White, 0.5f)
    !darkTheme && color.luminance() > 0.55f -> lerp(color, Color.Black, 0.5f)
    else -> color
}

/**
 * One UI 9.0 dark/light schemes sharing the same Obsidian-inspired tonal
 * structure, with [accent] driving primary/secondary rather than a fixed
 * violet - see [ThemeManager][com.example.samsonic.data.ThemeManager] for
 * where the user's chosen accent and dark/light preference are persisted.
 */
private fun obsidianColorScheme(darkTheme: Boolean, accent: Color): ColorScheme {
    val background = if (darkTheme) ObsidianBackground else LightBackground
    val surface = if (darkTheme) ObsidianSurface else LightSurface
    val surfaceElevated = if (darkTheme) ObsidianSurfaceElevated else LightSurfaceElevated
    val surfaceHigh = if (darkTheme) ObsidianSurfaceHigh else LightSurfaceHigh
    val outline = if (darkTheme) ObsidianOutline else LightOutline
    val textPrimary = if (darkTheme) ObsidianTextPrimary else LightTextPrimary
    val textSecondary = if (darkTheme) ObsidianTextSecondary else LightTextSecondary
    val error = if (darkTheme) ObsidianError else LightError

    // primary isn't only used as a container fill (where onPrimary handles
    // contrast) - it's also used directly as a content color over the plain
    // background/surface (nav bar selected icon, slider active track, link
    // text). A user-picked accent close to the current theme's own
    // background luminance (e.g. white in light mode) would be invisible
    // there, so nudge it towards the far side before deriving anything else.
    val safeAccent = ensureReadableOverBackground(accent, darkTheme)

    // Simple luminance check picks readable text for whatever accent the
    // user lands on, rather than assuming it's always dark-background-safe.
    val onAccent = if (safeAccent.luminance() > 0.5f) Color(0xFF14131A) else Color(0xFFF2F1F7)
    val accentContainer = lerp(background, safeAccent, 0.32f)
    val accentDim = lerp(safeAccent, outline, 0.5f)

    return if (darkTheme) {
        darkColorScheme(
            primary = safeAccent,
            onPrimary = onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = textPrimary,
            secondary = accentDim,
            onSecondary = onAccent,
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceElevated,
            onSurfaceVariant = textSecondary,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceElevated,
            surfaceContainerHighest = surfaceHigh,
            outline = outline,
            outlineVariant = outline,
            error = error,
            onError = onAccent,
        )
    } else {
        lightColorScheme(
            primary = safeAccent,
            onPrimary = onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = textPrimary,
            secondary = accentDim,
            onSecondary = onAccent,
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceElevated,
            onSurfaceVariant = textSecondary,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceElevated,
            surfaceContainerHighest = surfaceHigh,
            outline = outline,
            outlineVariant = outline,
            error = error,
            onError = onAccent,
        )
    }
}

@Composable
fun SamSonicTheme(
    darkTheme: Boolean,
    accentColor: Color,
    content: @Composable () -> Unit,
) {
    val colorScheme = obsidianColorScheme(darkTheme, accentColor)
    // Built on the readable accent (the scheme's primary), so its companions match what's on screen.
    val palette = remember(colorScheme.primary, darkTheme) { accentPaletteOf(colorScheme.primary, darkTheme) }
    CompositionLocalProvider(LocalAccentPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}

/** The accent and its three companions (see [AccentPalette]). */
val MaterialTheme.accentPalette: AccentPalette
    @Composable @ReadOnlyComposable get() = LocalAccentPalette.current
