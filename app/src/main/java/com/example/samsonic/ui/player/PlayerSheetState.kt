package com.example.samsonic.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the expanded player sheet is showing. */
enum class PlayerPage { NowPlaying, Queue, Lyrics }

// One critically damped spring for every settle, so a fling carries its speed
// into the motion instead of restarting on a fixed curve.
private val SettleSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

/**
 * The player sheet: 0 = collapsed into the mini player pill, 1 = full-screen Now
 * Playing. Drags move [progress] with the finger; a release settles it to the
 * nearer end, or the end a fling heads for.
 */
@Stable
class PlayerSheetState internal constructor(private val scope: CoroutineScope) {
    // Written synchronously by drags (so consecutive drag deltas always build on
    // the latest value), and by the settle animation in between.
    private var value by mutableFloatStateOf(0f)
    private var settleJob: Job? = null

    /** 0 collapsed .. 1 expanded. */
    val progress: Float get() = value

    /** Whether the sheet is expanded or heading there (drives back handling). */
    var isExpanded by mutableStateOf(false)
        private set

    var page by mutableStateOf(PlayerPage.NowPlaying)

    /** Pixels the sheet's top travels between collapsed and expanded; set by the layout. */
    internal var travelPx by mutableFloatStateOf(1f)

    /** True between the two ends: while dragging or settling. */
    val isMoving by derivedStateOf { value > 0.001f && value < 0.999f }

    fun expand() = settleTo(1f)

    fun collapse() = settleTo(0f)

    /** Jumps to [target] without animating (a drag, or a predictive back gesture). */
    internal fun snapTo(target: Float) {
        stop()
        value = target.coerceIn(0f, 1f)
    }

    internal fun dragBy(deltaPx: Float) {
        // Dragging up (negative delta) expands; the sheet's top follows the finger.
        snapTo(value - deltaPx / travelPx)
    }

    internal fun stop() {
        settleJob?.cancel()
        settleJob = null
    }

    /** Settles after a drag released with [velocityPx] (px/s, positive = downward). */
    internal fun settle(velocityPx: Float) {
        val flingThreshold = travelPx * 0.8f
        val target = when {
            velocityPx < -flingThreshold -> 1f
            velocityPx > flingThreshold -> 0f
            else -> if (value > 0.5f) 1f else 0f
        }
        settleTo(target, initialVelocity = -velocityPx / travelPx)
    }

    private fun settleTo(target: Float, initialVelocity: Float = 0f) {
        stop()
        isExpanded = target == 1f
        if (target == 0f) page = PlayerPage.NowPlaying
        settleJob = scope.launch {
            animate(value, target, initialVelocity, SettleSpring) { current, _ -> value = current }
        }
    }
}

@Composable
fun rememberPlayerSheetState(): PlayerSheetState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PlayerSheetState(scope) }
}
