package com.example.samsonic.ui.theme

import android.graphics.Bitmap
import androidx.core.graphics.scale
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** How wide the backdrop's art is blurred, in pixels: small, as the blur leaves no detail to keep. */
internal const val BackdropBlurWidth = 128

/**
 * Blurs the cover behind Now Playing once, as it loads, rather than live every frame:
 * a live full-screen blur of this radius cost the GPU heavily each frame the player
 * moved. The art is shrunk to [BackdropBlurWidth] and given a Gaussian-like blur (three
 * box blurs) matching [Modifier.blur][androidx.compose.ui.draw.blur] at the size it's
 * shown; scaled back up, it looks the same.
 *
 * [radiusFraction] is the blur radius as a share of the art's shown size.
 */
internal class BackdropBlur(private val radiusFraction: Float) : Transformation() {
    override val cacheKey: String = "backdropBlur(${(radiusFraction * 10_000).roundToInt()})"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = BackdropBlurWidth.coerceAtMost(input.width)
        val height = max(1, (input.height * width.toFloat() / input.width).roundToInt())
        val small = input.scale(width, height).copy(Bitmap.Config.ARGB_8888, true)
        // As RenderEffect's blur takes a radius: sigma = radius / sqrt(3) + 0.5.
        val sigma = radiusFraction * max(width, height) * 0.57735f + 0.5f
        // Three box blurs of this radius come close to a Gaussian of [sigma].
        val boxRadius = ((sqrt(4 * sigma * sigma + 1) - 1) / 2).roundToInt().coerceAtLeast(1)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        val scratch = IntArray(pixels.size)
        repeat(3) {
            boxBlur(pixels, scratch, width, height, boxRadius, horizontal = true)
            boxBlur(scratch, pixels, width, height, boxRadius, horizontal = false)
        }
        small.setPixels(pixels, 0, width, 0, 0, width, height)
        return small
    }
}

/** One pass of a box blur of [radius] from [src] into [dst], along rows or columns, edges clamped. */
private fun boxBlur(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val window = radius * 2 + 1
    for (line in 0 until lines) {
        fun at(i: Int): Int {
            val c = i.coerceIn(0, length - 1)
            return if (horizontal) line * width + c else c * width + line
        }
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val p = src[at(i)]
            a += p ushr 24; r += (p shr 16) and 0xff; g += (p shr 8) and 0xff; b += p and 0xff
        }
        for (i in 0 until length) {
            dst[at(i)] = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            val out = src[at(i - radius)]
            val next = src[at(i + radius + 1)]
            a += (next ushr 24) - (out ushr 24)
            r += ((next shr 16) and 0xff) - ((out shr 16) and 0xff)
            g += ((next shr 8) and 0xff) - ((out shr 8) and 0xff)
            b += (next and 0xff) - (out and 0xff)
        }
    }
}
