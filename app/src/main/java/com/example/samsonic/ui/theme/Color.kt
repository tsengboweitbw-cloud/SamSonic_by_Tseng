package com.example.samsonic.ui.theme

import androidx.compose.ui.graphics.Color

// Obsidian-inspired dark palette: near-black surfaces with a violet accent.
val ObsidianBackground = Color(0xFF0E0E12)
val ObsidianSurface = Color(0xFF17171D)
val ObsidianSurfaceElevated = Color(0xFF1F1F27)
val ObsidianSurfaceHigh = Color(0xFF28282F)
val ObsidianOutline = Color(0xFF34333D)

val ObsidianTextPrimary = Color(0xFFF2F1F7)
val ObsidianTextSecondary = Color(0xFFA5A3B3)
val ObsidianTextTertiary = Color(0xFF6E6C7C)

val ObsidianError = Color(0xFFFF6B6B)

// Light counterpart palette - soft, slightly cool-toned surfaces mirroring the
// dark Obsidian set's tonal steps, so light mode reads as the same design,
// not a bolted-on afterthought.
val LightBackground = Color(0xFFF6F5F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFEDEBF3)
val LightSurfaceHigh = Color(0xFFE1DEEA)
val LightOutline = Color(0xFFD6D3DE)

val LightTextPrimary = Color(0xFF1B1A20)
val LightTextSecondary = Color(0xFF5F5D68)
val LightTextTertiary = Color(0xFF908E99)

val LightError = Color(0xFFBA1A1A)

// One UI 9.0 glassmorphism: named translucency levels (0.7-0.85 alpha per
// Rule.md) plus the thin light rim-stroke drawn along a glass surface's edge.
object GlassAlpha {
    const val Nav = 0.45f
    const val MiniPlayer = 0.45f
    const val Card = 0.74f
    const val Sheet = 0.28f
    // A panel floating over live content (the Library view options): its blur
    // samples the content undimmed, so it needs a dense tint to stay readable.
    const val Panel = 0.85f
    // Now Playing pills sit over already-blurred art, so a thin light veil
    // (not a dense surface tint) is what reads as frosted glass there.
    const val NowPlaying = 0.18f
    // A faint accent wash behind the playing row: the row's title is drawn in the
    // accent too, so a dense fill would swallow it (a light accent went white-on-white).
    const val Highlight = 0.16f
}
