package com.example.samsonic.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect

// Shared: saveLayer only reads it, never keeps it.
private val StripPaint = Paint()

/**
 * Draws the content with its top [top] px fading in from transparent and its bottom
 * [bottom] px fading out to transparent. Only those strips go through an offscreen
 * layer, each just its own size, rather than the whole content every frame, as a
 * full-size offscreen graphicsLayer would. The content is clipped to its bounds, as
 * such a layer would clip it.
 */
internal fun ContentDrawScope.drawWithVerticalFades(top: Float, bottom: Float) {
    val w = size.width
    val h = size.height
    val t = top.coerceIn(0f, h)
    val b = bottom.coerceIn(0f, h - t)
    if (t <= 0f && b <= 0f) {
        drawContent()
        return
    }
    clipRect(top = t, bottom = h - b) { this@drawWithVerticalFades.drawContent() }
    if (t > 0f) {
        fadedStrip(Rect(0f, 0f, w, t), Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = t))
    }
    if (b > 0f) {
        fadedStrip(Rect(0f, h - b, w, h), Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = h - b, endY = h))
    }
}

/** The sideways [drawWithVerticalFades]: [start] px fading in at the left edge, [end] px out at the right. */
internal fun ContentDrawScope.drawWithHorizontalFades(start: Float, end: Float) {
    val w = size.width
    val h = size.height
    val s = start.coerceIn(0f, w)
    val e = end.coerceIn(0f, w - s)
    if (s <= 0f && e <= 0f) {
        drawContent()
        return
    }
    clipRect(left = s, right = w - e) { this@drawWithHorizontalFades.drawContent() }
    if (s > 0f) {
        fadedStrip(Rect(0f, 0f, s, h), Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = s))
    }
    if (e > 0f) {
        fadedStrip(Rect(w - e, 0f, w, h), Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = w - e, endX = w))
    }
}

/** The content within [rect], masked by [brush] in a layer of its own the size of [rect]. */
private fun ContentDrawScope.fadedStrip(rect: Rect, brush: Brush) {
    val canvas = drawContext.canvas
    canvas.saveLayer(rect, StripPaint)
    clipRect(rect.left, rect.top, rect.right, rect.bottom) { this@fadedStrip.drawContent() }
    drawRect(brush, topLeft = rect.topLeft, size = rect.size, blendMode = BlendMode.DstIn)
    canvas.restore()
}
