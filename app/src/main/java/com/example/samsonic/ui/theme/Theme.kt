package com.example.samsonic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// SamSonic always runs in a single, deliberately-designed dark theme -
// Obsidian.md's signature near-black + violet look - rather than following
// system light/dark or dynamic (Material You) colors.
private val ObsidianColorScheme = darkColorScheme(
    primary = ObsidianAccent,
    onPrimary = ObsidianTextPrimary,
    primaryContainer = ObsidianAccentContainer,
    onPrimaryContainer = ObsidianTextPrimary,
    secondary = ObsidianAccentDim,
    onSecondary = ObsidianTextPrimary,
    background = ObsidianBackground,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = ObsidianTextSecondary,
    surfaceContainer = ObsidianSurface,
    surfaceContainerHigh = ObsidianSurfaceElevated,
    surfaceContainerHighest = ObsidianSurfaceHigh,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutline,
    error = ObsidianError,
    onError = ObsidianTextPrimary,
)

@Composable
fun SamSonicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ObsidianColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
