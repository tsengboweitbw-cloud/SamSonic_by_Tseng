package com.example.samsonic.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Fades the bottom [height] of the content out into [background], so lists melt
 * into the page behind the floating chrome instead of being cut off. It draws that
 * colour over the content rather than masking the content to transparent, which
 * needs no offscreen layer: the same look where [background] is what lies behind.
 */
fun Modifier.bottomFade(height: Dp, background: Color): Modifier = drawWithContent {
    drawContent()
    val fadePx = height.toPx()
    if (fadePx <= 0f) return@drawWithContent
    val top = size.height - fadePx
    drawRect(
        brush = Brush.verticalGradient(0f to background.copy(alpha = 0f), 1f to background, startY = top, endY = size.height),
        topLeft = Offset(0f, top),
        size = Size(size.width, fadePx),
    )
}