package com.example.samsonic.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

/**
 * The accent and three companions picked to go with it, all at the accent's own
 * brightness so none shouts over the others:
 * - [primary]: the user's accent itself;
 * - [secondary], [tertiary]: its neighbours on the color wheel (one each side), so
 *   pairs of them blend into gradients that stay in the accent's family;
 * - [highlight]: a softened complement, the one contrasting spark, used sparingly.
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

// How far round the color wheel the neighbours sit: close enough to read as one family.
private const val NeighbourShift = 38f

/**
 * Derives the [AccentPalette] for [accent] (already made readable for the theme).
 * The companions share its lightness, clamped to a band that reads over the theme's
 * background, and a saturation kept off the extremes, so a grey-ish accent still gets
 * some color and a neon one doesn't turn the app garish.
 */
fun accentPaletteOf(accent: Color, darkTheme: Boolean): AccentPalette {
    val (hue, saturation, lightness) = accent.toHsl()
    val companionSaturation = saturation.coerceIn(0.45f, 0.8f)
    val companionLightness = if (darkTheme) lightness.coerceIn(0.6f, 0.74f) else lightness.coerceIn(0.4f, 0.52f)
    fun companion(shift: Float, saturationScale: Float = 1f) =
        hslColor(hue + shift, companionSaturation * saturationScale, companionLightness)
    return AccentPalette(
        primary = accent,
        secondary = companion(NeighbourShift),
        tertiary = companion(-NeighbourShift),
        highlight = companion(180f, saturationScale = 0.85f),
    )
}

private fun Color.toHsl(): Triple<Float, Float, Float> {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    return Triple(hsl[0], hsl[1], hsl[2])
}

private fun hslColor(hue: Float, saturation: Float, lightness: Float): Color = Color.hsl(
    hue = ((hue % 360f) + 360f) % 360f,
    saturation = saturation.coerceIn(0f, 1f),
    lightness = lightness.coerceIn(0f, 1f),
)
