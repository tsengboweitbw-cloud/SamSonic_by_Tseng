package com.example.samsonic.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

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

    // Simple luminance check picks readable text for whatever accent the
    // user lands on, rather than assuming it's always dark-background-safe.
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF14131A) else Color(0xFFF2F1F7)
    val accentContainer = lerp(background, accent, 0.32f)
    val accentDim = lerp(accent, outline, 0.5f)

    return if (darkTheme) {
        darkColorScheme(
            primary = accent,
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
            primary = accent,
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
    MaterialTheme(
        colorScheme = obsidianColorScheme(darkTheme, accentColor),
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
