package com.example.samsonic.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The top-edge partner of [bottomFade]: fades the top [height] of a scrolling
 * list so rows melt away under the fixed header (or status bar) instead of
 * being sliced off. Only shows once the list is scrolled, so at rest the first
 * row is never dimmed.
 *
 * Offscreen compositing is required: DstIn has to mask this layer's own
 * pixels, not whatever was already drawn underneath it.
 */
@Composable
fun Modifier.scrollTopFade(state: ScrollableState, height: Dp = 32.dp): Modifier {
    val fade by animateDpAsState(
        targetValue = if (state.canScrollBackward) height else 0.dp,
        animationSpec = tween(200),
        label = "scrollTopFade",
    )
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fadePx = fade.toPx()
            if (fadePx <= 0f) return@drawWithContent
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                    startY = 0f,
                    endY = fadePx,
                ),
                size = Size(size.width, fadePx),
                blendMode = BlendMode.DstIn,
            )
        }
}
