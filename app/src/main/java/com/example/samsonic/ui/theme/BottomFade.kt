package com.example.samsonic.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * Fades the bottom [height] of the content out to transparent, so lists melt
 * into the background behind the floating chrome instead of being cut off.
 *
 * Offscreen compositing is required: DstIn has to mask this layer's own
 * pixels, not whatever was already drawn underneath it.
 */
fun Modifier.bottomFade(height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fadePx = height.toPx()
        if (fadePx <= 0f) return@drawWithContent
        val top = size.height - fadePx
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black,
                1f to Color.Transparent,
                startY = top,
                endY = size.height,
            ),
            topLeft = Offset(0f, top),
            size = Size(size.width, fadePx),
            blendMode = BlendMode.DstIn,
        )
    }
