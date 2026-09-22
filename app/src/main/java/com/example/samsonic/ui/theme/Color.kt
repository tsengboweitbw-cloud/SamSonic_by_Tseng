package com.example.samsonic.ui.theme

import androidx.compose.ui.graphics.Color

// Obsidian-inspired dark palette: near-black surfaces with a violet accent.
val ObsidianBackground = Color(0xFF0E0E12)
val ObsidianSurface = Color(0xFF17171D)
val ObsidianSurfaceElevated = Color(0xFF1F1F27)
val ObsidianSurfaceHigh = Color(0xFF28282F)
val ObsidianOutline = Color(0xFF34333D)

val ObsidianAccent = Color(0xFF8875FF)
val ObsidianAccentDim = Color(0xFF5E52A8)
val ObsidianAccentContainer = Color(0xFF322A5E)

val ObsidianTextPrimary = Color(0xFFF2F1F7)
val ObsidianTextSecondary = Color(0xFFA5A3B3)
val ObsidianTextTertiary = Color(0xFF6E6C7C)

val ObsidianError = Color(0xFFFF6B6B)

// One UI 9.0 glassmorphism: named translucency levels (0.7-0.85 alpha per
// Rule.md) plus the thin light rim-stroke drawn along a glass surface's edge.
object GlassAlpha {
    const val Nav = 0.7f
    const val MiniPlayer = 0.7f
    const val Card = 0.74f
    const val Sheet = 0.85f
}

val GlassRimLight = Color(0xFFFFFFFF).copy(alpha = 0.16f)

// Deterministic gradient pairs used for generated "album art" placeholders.
val ArtGradients = listOf(
    Color(0xFF8875FF) to Color(0xFF3D2C7A),
    Color(0xFFFF6FA5) to Color(0xFF5C1E4A),
    Color(0xFF3EC6E0) to Color(0xFF13415B),
    Color(0xFFFFB86B) to Color(0xFF6B3A12),
    Color(0xFF6DE38F) to Color(0xFF15542E),
    Color(0xFFB07CFF) to Color(0xFF33165C),
    Color(0xFFFF8A65) to Color(0xFF5C2410),
    Color(0xFF5B8DEF) to Color(0xFF16265C),
)
