package com.example.samsonic.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize

/** Pieces that morph between the mini player and Now Playing. */
enum class PlayerElement { Art, Progress }

/** Which player surface an anchor sits on. */
enum class PlayerSurface { Mini, Full }

/** One curve and length for the whole mini player <-> Now Playing morph. */
const val PlayerMorphMs = 420
val PlayerMorphEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Drives the mini player <-> Now Playing morph by hand instead of with shared
 * element transitions: those need both sides' animations to line up in the same
 * frame, which the mini player (outside the NavHost) never reliably does, so on
 * close the elements snapped straight to the mini player.
 *
 * Both surfaces report where their cover and progress line are on screen; while
 * the mini player animates in or out, [PlayerMorphOverlay] draws the element at
 * a point between the two and the real ones are hidden.
 */
@Stable
class PlayerMorphState {
    private class Anchor(val owner: Any, val bounds: Rect)

    private val anchors = mutableStateMapOf<Pair<PlayerElement, PlayerSurface>, Anchor>()
    private var fractionState: State<Float>? by mutableStateOf(null)
    private var activeState: State<Boolean>? by mutableStateOf(null)

    /** 0 at the mini player, 1 at Now Playing. */
    val fraction: Float get() = fractionState?.value ?: 0f

    /** True while the mini player is animating in or out. */
    val isActive: Boolean get() = activeState?.value == true

    /** Whether the overlay is currently drawing [element] (so the real ones should hide). */
    fun covers(element: PlayerElement): Boolean =
        isActive && PlayerSurface.entries.all { anchors[element to it] != null }

    /** Where [element] is drawn right now, in window coordinates; null when not morphing. */
    fun boundsOf(element: PlayerElement): Rect? {
        if (!isActive) return null
        val mini = anchors[element to PlayerSurface.Mini]?.bounds ?: return null
        val full = anchors[element to PlayerSurface.Full]?.bounds ?: return null
        return lerp(mini, full, fraction)
    }

    internal fun report(element: PlayerElement, surface: PlayerSurface, owner: Any, bounds: Rect) {
        anchors[element to surface] = Anchor(owner, bounds)
    }

    // Only the current owner may clear a slot: when the carousel moves the anchor to
    // another page, the new page can report before the old one is disposed.
    internal fun release(element: PlayerElement, surface: PlayerSurface, owner: Any) {
        if (anchors[element to surface]?.owner === owner) anchors.remove(element to surface)
    }

    internal fun bind(fraction: State<Float>, active: State<Boolean>) {
        fractionState = fraction
        activeState = active
    }

    internal fun unbind(fraction: State<Float>) {
        if (fractionState === fraction) {
            fractionState = null
            activeState = null
        }
    }
}

val LocalPlayerMorph = staticCompositionLocalOf<PlayerMorphState?> { null }

/**
 * Call inside the mini player's AnimatedVisibility: its enter/exit becomes the
 * morph's clock, so opening and closing Now Playing run the same animation in reverse.
 */
@Composable
fun AnimatedVisibilityScope.DrivePlayerMorph(morph: PlayerMorphState) {
    val transition = transition
    val fraction = transition.animateFloat(
        transitionSpec = { tween(PlayerMorphMs, easing = PlayerMorphEasing) },
        label = "playerMorph",
    ) { if (it == EnterExitState.Visible) 0f else 1f }
    val active = remember(transition) {
        derivedStateOf { transition.currentState != transition.targetState }
    }
    DisposableEffect(morph, fraction) {
        morph.bind(fraction, active)
        onDispose { morph.unbind(fraction) }
    }
}

/**
 * Marks this as [element] on [surface]: reports its window bounds to the morph and
 * hides it while the overlay draws it in flight. A no-op without a [LocalPlayerMorph].
 */
@Composable
fun Modifier.playerMorphAnchor(element: PlayerElement, surface: PlayerSurface): Modifier {
    val morph = LocalPlayerMorph.current ?: return this
    val owner = remember { Any() }
    DisposableEffect(morph, element, surface) {
        onDispose { morph.release(element, surface, owner) }
    }
    return this
        .onGloballyPositioned {
            morph.report(element, surface, owner, Rect(it.positionInWindow(), it.size.toSize()))
        }
        .graphicsLayer { alpha = if (morph.covers(element)) 0f else 1f }
}
