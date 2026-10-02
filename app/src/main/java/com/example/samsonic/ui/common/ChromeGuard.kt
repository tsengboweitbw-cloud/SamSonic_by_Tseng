package com.example.samsonic.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Whether a menu is open over a page, under the floating chrome (the mini player
 * and nav bar): while one is, the chrome can't be used, and a tap on it closes the
 * menu like a tap anywhere else outside it. The nav host shows [ChromeGuardLayer]
 * over the chrome; a menu claims it with [GuardChrome].
 */
@Stable
class ChromeGuard {
    internal var claim by mutableStateOf<Claim?>(null)

    /** How far up from the screen's bottom the chrome reaches: set by the nav host. */
    var height by mutableStateOf(0.dp)
        internal set

    /**
     * Where the top of the highest bar is right now, in px up from the screen's bottom:
     * the nav bar's, or the mini player's above it (apart or piled), following them as
     * the mini player comes, goes or is swiped away. Unlike [height], which only
     * reserves room, it's live: read it in layout or draw. Set by the nav host.
     */
    var top: () -> Float = { 0f }
        internal set

    /**
     * The window x-range of the page the open menu is dimming, set as its scrim draws (see
     * pageScrim): beside another pane, the chrome under that one stays undimmed.
     */
    var dimSpan: ClosedFloatingPointRange<Float>? = null
        internal set

    /** The open menu's dim over the page (read at draw time) and how to close it. */
    internal class Claim(val dim: () -> Float, val dismiss: () -> Unit)
}

val LocalChromeGuard = staticCompositionLocalOf<ChromeGuard?> { null }

/**
 * While [active], keeps the floating chrome from being used, dims it by [dim] as the
 * page under the menu is, and has a tap on it call [onDismiss].
 */
@Composable
fun GuardChrome(active: Boolean, dim: () -> Float, onDismiss: () -> Unit) {
    val guard = LocalChromeGuard.current ?: return
    val currentDim by rememberUpdatedState(dim)
    val currentDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(guard, active) {
        if (!active) return@DisposableEffect onDispose {}
        val claim = ChromeGuard.Claim({ currentDim() }, { currentDismiss() })
        guard.claim = claim
        onDispose { if (guard.claim === claim) guard.claim = null }
    }
}

/**
 * Laid over the floating chrome, [height] up from the bottom of the screen (with
 * [modifier] placing it there): while a menu has claimed [guard], dims the chrome and
 * the page under it and takes every touch, a tap closing the menu.
 */
@Composable
fun ChromeGuardLayer(guard: ChromeGuard, height: Dp, modifier: Modifier = Modifier, alwaysDimWidth: Dp = 0.dp) {
    SideEffect { guard.height = height }
    ChromeGuardArea(guard, modifier.fillMaxWidth().height(height), followPage = true, alwaysDimWidth = alwaysDimWidth)
}

/**
 * Laid over floating chrome elsewhere than the foot of the screen (the nav rail at its
 * side), [modifier] placing and sizing it: while a menu has claimed [guard], dims what's
 * under it as the page is dimmed and takes every touch, a tap closing the menu.
 */
@Composable
fun ChromeGuardArea(
    guard: ChromeGuard,
    modifier: Modifier = Modifier,
    // Dims only under the page the menu dims, plus [alwaysDimWidth] at the start (the rail's).
    followPage: Boolean = false,
    alwaysDimWidth: Dp = 0.dp,
) {
    val claim = guard.claim ?: return
    var left by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .onGloballyPositioned { left = it.positionInWindow().x }
            .drawBehind {
                val color = Color.Black.copy(alpha = claim.dim().coerceIn(0f, 1f))
                val span = guard.dimSpan
                if (!followPage || span == null) {
                    drawRect(color)
                } else {
                    val start = (span.start - left).coerceIn(0f, size.width)
                    val end = (span.endInclusive - left).coerceIn(start, size.width)
                    drawRect(color, topLeft = Offset(start, 0f), size = Size(end - start, size.height))
                    drawRect(color, size = Size(minOf(alwaysDimWidth.toPx(), start), size.height))
                }
            }
            .dismissOnTap(claim),
    )
}

/**
 * Over a part of the page the open menu leaves uncovered but dims, such as its
 * title ([modifier] sizing it): while a menu has claimed the guard, takes every touch
 * there, a tap closing the menu.
 */
@Composable
fun GuardedArea(modifier: Modifier = Modifier) {
    val claim = LocalChromeGuard.current?.claim ?: return
    Box(modifier.dismissOnTap(claim))
}

/** Takes every touch, a tap closing [claim]'s menu. */
private fun Modifier.dismissOnTap(claim: ChromeGuard.Claim): Modifier = pointerInput(claim) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        if (waitForUpOrCancellation() != null) claim.dismiss()
    }
}
