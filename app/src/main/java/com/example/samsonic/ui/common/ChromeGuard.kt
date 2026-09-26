package com.example.samsonic.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
fun ChromeGuardLayer(guard: ChromeGuard, height: Dp, modifier: Modifier = Modifier) {
    SideEffect { guard.height = height }
    val claim = guard.claim ?: return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind { drawRect(Color.Black.copy(alpha = claim.dim().coerceIn(0f, 1f))) }
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
