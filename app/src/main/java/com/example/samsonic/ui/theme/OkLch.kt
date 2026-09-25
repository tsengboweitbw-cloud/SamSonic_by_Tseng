package com.example.samsonic.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * A color in OKLCH: perceptual [lightness] (0-1), [chroma] (colorfulness, about
 * 0-0.37) and [hue] in degrees. Unlike HSL, two colors with the same lightness
 * here really do look equally bright, whatever their hue - the property that
 * makes colors derived from each other read as one set.
 */
internal data class OkLch(val lightness: Float, val chroma: Float, val hue: Float) {

    /** Back to sRGB, pulling chroma in (hue and lightness kept) until it fits the gamut. */
    fun toColor(alpha: Float = 1f): Color {
        var c = chroma
        repeat(40) {
            val rgb = toLinearRgb(lightness, c, hue)
            if (rgb.all { it in -0.0001f..1.0001f }) {
                return Color(encode(rgb[0]), encode(rgb[1]), encode(rgb[2]), alpha)
            }
            c *= 0.93f
        }
        val rgb = toLinearRgb(lightness, 0f, hue)
        return Color(encode(rgb[0]), encode(rgb[1]), encode(rgb[2]), alpha)
    }

    companion object {
        fun of(color: Color): OkLch {
            val r = decode(color.red)
            val g = decode(color.green)
            val b = decode(color.blue)
            val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
            val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
            val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
            val okL = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
            val okA = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
            val okB = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
            val hue = Math.toDegrees(atan2(okB, okA).toDouble()).toFloat()
            return OkLch(okL, hypot(okA, okB), (hue + 360f) % 360f)
        }
    }
}

private fun toLinearRgb(lightness: Float, chroma: Float, hue: Float): FloatArray {
    val rad = Math.toRadians(hue.toDouble())
    val a = chroma * cos(rad).toFloat()
    val b = chroma * sin(rad).toFloat()
    val l = (lightness + 0.3963377774f * a + 0.2158037573f * b).let { it * it * it }
    val m = (lightness - 0.1055613458f * a - 0.0638541728f * b).let { it * it * it }
    val s = (lightness - 0.0894841775f * a - 1.2914855480f * b).let { it * it * it }
    return floatArrayOf(
        4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
        -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
        -0.0041960863f * l - 0.7034186147f * m + 1.7076926242f * s,
    )
}

// sRGB transfer curve, gamma-encoded <-> linear light.
private fun decode(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

private fun encode(c: Float): Float {
    val v = c.coerceIn(0f, 1f)
    return if (v <= 0.0031308f) 12.92f * v else 1.055f * v.pow(1f / 2.4f) - 0.055f
}
