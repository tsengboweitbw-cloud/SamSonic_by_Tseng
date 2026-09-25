package com.example.samsonic.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The accent and three companions derived from it (see [accentPaletteOf]), all in
 * its family so they read as one set:
 * - [primary]: the user's accent itself;
 * - [secondary], [tertiary]: its neighbours on the color wheel (one each side), so
 *   pairs of them blend into gradients that stay in the accent's family;
 * - [highlight]: a pale, soft tint of the accent itself, for the quietest touches.
 */
@Immutable
data class AccentPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val highlight: Color,
) {
    /** All four, in order: handy for cycling through them. */
    val all: List<Color> get() = listOf(primary, secondary, tertiary, highlight)

    /** The [index]th color, wrapping round. */
    operator fun get(index: Int): Color = all[Math.floorMod(index, 4)]

    /**
     * The gradient standing in for missing art, picked by [seed]: one palette color
     * fading into a deep shade of a neighbour (either way round), so placeholders
     * vary from one another but all sit in the app's colors.
     */
    fun artGradient(seed: Int): Pair<Color, Color> {
        val pick = Math.floorMod(seed, 8)
        val start = get(pick)
        val end = get(if (pick < 4) pick + 1 else pick - 1)
        return start to lerp(end, Color.Black, 0.6f)
    }
}

val LocalAccentPalette = staticCompositionLocalOf {
    accentPaletteOf(Color(0xFF8875FF), darkTheme = true)
}

/**
 * A progress line's fill: the accent easing into its neighbour across the line's
 * whole length ([startX] to [endX]), so a bar only part way shows only the start of it.
 */
fun progressBrush(palette: AccentPalette, startX: Float, endX: Float): Brush =
    Brush.horizontalGradient(listOf(palette.primary, palette.secondary), startX = startX, endX = endX)

// How far round the (perceptual) color wheel the neighbours sit: near enough that
// all three read as one family, far enough that a gradient between them shows.
private const val NeighbourShift = 24f

/**
 * Derives the [AccentPalette] for [accent] (already made readable for the theme),
 * in OKLCH so every companion looks exactly as bright as the accent:
 * - the neighbours keep its lightness and colorfulness, the cooler-leaning one a
 *   little quieter, so the accent always leads;
 * - the highlight is the accent's own hue, lifted and softened into a pale tint,
 *   rather than a clashing complement.
 * Colorfulness is kept off the extremes, so a grey-ish accent still gets a gentle
 * palette and a neon one doesn't turn the app garish.
 */
fun accentPaletteOf(accent: Color, darkTheme: Boolean): AccentPalette {
    val base = OkLch.of(accent)
    val chroma = base.chroma.coerceIn(0.05f, 0.17f)
    fun companion(shift: Float, chromaScale: Float) =
        OkLch(base.lightness, chroma * chromaScale, (base.hue + shift + 360f) % 360f).toColor()
    val tintLightness = (base.lightness + if (darkTheme) 0.12f else 0.1f).coerceAtMost(0.92f)
    return AccentPalette(
        primary = accent,
        secondary = companion(NeighbourShift, chromaScale = 1f),
        tertiary = companion(-NeighbourShift, chromaScale = 0.85f),
        highlight = OkLch(tintLightness, chroma * 0.55f, base.hue).toColor(),
    )
}
