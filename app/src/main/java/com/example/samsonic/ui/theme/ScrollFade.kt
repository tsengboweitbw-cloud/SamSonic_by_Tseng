package com.example.samsonic.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * The bottom-edge partner of [scrollTopFade], for lists in a bounded panel:
 * rows melt away at the bottom edge while more lie below it, and the fade
 * clears once the list reaches its end, so the last row is never dimmed.
 */
@Composable
fun Modifier.scrollBottomFade(state: ScrollableState, height: Dp = 32.dp): Modifier {
    val fade by animateDpAsState(
        targetValue = if (state.canScrollForward) height else 0.dp,
        animationSpec = tween(200),
        label = "scrollBottomFade",
    )
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fadePx = fade.toPx()
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
}

/**
 * The sideways partner of [scrollTopFade], for horizontal rows: cards melt away
 * at the left edge once the row is scrolled, and at the right edge while more
 * cards lie beyond it. Each edge only shows while there is something past it,
 * so at rest the first card is never dimmed.
 */
@Composable
fun Modifier.horizontalScrollFade(state: ScrollableState, width: Dp = 8.dp): Modifier {
    val start by animateDpAsState(
        targetValue = if (state.canScrollBackward) width else 0.dp,
        animationSpec = tween(200),
        label = "scrollStartFade",
    )
    val end by animateDpAsState(
        targetValue = if (state.canScrollForward) width else 0.dp,
        animationSpec = tween(200),
        label = "scrollEndFade",
    )
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val startPx = start.toPx()
            if (startPx > 0f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black,
                        startX = 0f,
                        endX = startPx,
                    ),
                    size = Size(startPx, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
            val endPx = end.toPx()
            if (endPx > 0f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Black,
                        1f to Color.Transparent,
                        startX = size.width - endPx,
                        endX = size.width,
                    ),
                    topLeft = Offset(size.width - endPx, 0f),
                    size = Size(endPx, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}
