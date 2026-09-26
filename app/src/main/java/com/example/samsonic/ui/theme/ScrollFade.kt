package com.example.samsonic.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How far (0 to 1) an edge fade is shown: animated toward 1 while [edgeHasMore], else
 * 0. Followed in an effect, not composition, so a list starting or stopping being
 * scrollable past that edge doesn't recompose the screen it's on.
 */
@Composable
private fun rememberEdgeFade(state: ScrollableState, edgeHasMore: (ScrollableState) -> Boolean): Animatable<Float, *> {
    val fade = remember(state) { Animatable(if (edgeHasMore(state)) 1f else 0f) }
    LaunchedEffect(state) {
        snapshotFlow { edgeHasMore(state) }.collect { fade.animateTo(if (it) 1f else 0f, tween(200)) }
    }
    return fade
}

/**
 * The top-edge partner of [bottomFade]: fades the top [height] of a scrolling
 * list so rows melt away under the fixed header (or status bar) instead of
 * being sliced off. Only shows once the list is scrolled, so at rest the first
 * row is never dimmed. Only the faded strip is drawn offscreen (see [drawWithVerticalFades]).
 */
@Composable
fun Modifier.scrollTopFade(state: ScrollableState, height: Dp = 32.dp): Modifier {
    val fade = rememberEdgeFade(state) { it.canScrollBackward }
    return drawWithContent { drawWithVerticalFades(top = fade.value * height.toPx(), bottom = 0f) }
}

/**
 * The bottom-edge partner of [scrollTopFade], for lists in a bounded panel:
 * rows melt away at the bottom edge while more lie below it, and the fade
 * clears once the list reaches its end, so the last row is never dimmed.
 */
@Composable
fun Modifier.scrollBottomFade(state: ScrollableState, height: Dp = 32.dp): Modifier {
    val fade = rememberEdgeFade(state) { it.canScrollForward }
    return drawWithContent { drawWithVerticalFades(top = 0f, bottom = fade.value * height.toPx()) }
}

/** [scrollTopFade] and [scrollBottomFade] together, drawn in one pass. */
@Composable
fun Modifier.scrollEdgeFades(state: ScrollableState, height: Dp = 32.dp): Modifier {
    val top = rememberEdgeFade(state) { it.canScrollBackward }
    val bottom = rememberEdgeFade(state) { it.canScrollForward }
    return drawWithContent { drawWithVerticalFades(top = top.value * height.toPx(), bottom = bottom.value * height.toPx()) }
}

/**
 * The sideways partner of [scrollTopFade], for horizontal rows: cards melt away
 * at the left edge once the row is scrolled, and at the right edge while more
 * cards lie beyond it. Each edge only shows while there is something past it,
 * so at rest the first card is never dimmed.
 */
@Composable
fun Modifier.horizontalScrollFade(state: ScrollableState, width: Dp = 8.dp): Modifier {
    val start = rememberEdgeFade(state) { it.canScrollBackward }
    val end = rememberEdgeFade(state) { it.canScrollForward }
    return drawWithContent { drawWithHorizontalFades(start = start.value * width.toPx(), end = end.value * width.toPx()) }
}
