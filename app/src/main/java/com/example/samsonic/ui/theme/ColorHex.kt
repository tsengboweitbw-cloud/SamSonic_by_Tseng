package com.example.samsonic.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/** "RRGGBB" (no leading '#'), uppercase. */
fun Color.toHexRgb(): String {
    val r = (red * 255).roundToInt()
    val g = (green * 255).roundToInt()
    val b = (blue * 255).roundToInt()
    return "%02X%02X%02X".format(r, g, b)
}

/** Parses a "RRGGBB" or "#RRGGBB" string; returns null for anything else (including partial input while typing). */
fun parseHexColor(hex: String): Color? {
    val clean = hex.removePrefix("#")
    if (clean.length != 6 || clean.any { it !in "0123456789abcdefABCDEF" }) return null
    val rgb = clean.toLongOrNull(16) ?: return null
    return Color((0xFF000000L or rgb).toInt())
}
