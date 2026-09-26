package com.example.samsonic.ui.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// One critically damped spring for every settle, so a fling carries its speed
// into the motion instead of restarting on a fixed curve.
private val SettleSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

/**
 * A 0..1 position that a finger drags across [travelPx] pixels (dragging up moves
 * toward 1) and a spring settles to either end. Shared by the player sheet and the
 * queue panel that slides up over Now Playing.
 */
@Stable
internal class SpringTrack(private val scope: CoroutineScope) {
    // Written synchronously by drags (so consecutive drag deltas always build on
    // the latest value), and by the settle animation in between. An underdamped
    // settle spec can carry it briefly past 0 or 1.
    var position by mutableFloatStateOf(0f)
        private set
    private var settleJob: Job? = null
    // The settle's speed (per second, in 0..1 units) as of its last frame, so a new
    // settle taking over mid-way carries it on instead of starting from a standstill.
    private var velocity = 0f

    /** Pixels a full 0..1 trip covers; set by the layout. */
    var travelPx by mutableFloatStateOf(1f)

    /** Jumps to [target] without animating (a drag, or a predictive back gesture). */
    fun snapTo(target: Float) {
        stop()
        position = target.coerceIn(0f, 1f)
    }

    fun dragBy(deltaPx: Float) = snapTo(position - deltaPx / travelPx)

    fun stop() {
        settleJob?.cancel()
        settleJob = null
    }

    /** The end a release with [velocityPx] (px/s, positive = downward) heads for. */
    fun targetFor(velocityPx: Float, commitAt: Float = 0.5f, flingPx: Float? = null): Float {
        val flingThreshold = flingPx ?: (travelPx * 0.8f)
        return when {
            velocityPx < -flingThreshold -> 1f
            velocityPx > flingThreshold -> 0f
            else -> if (position > commitAt) 1f else 0f
        }
    }

    /**
     * Settles at [target], setting off at [velocityPx] (px/s, positive = downward: a
     * release's speed) or, left null, at the speed of any settle it takes over, so
     * turning round mid-way (collapsing as it expands) is one continuous motion. The
     * returned job completes when it gets there (or is cut short).
     */
    fun animateTo(target: Float, velocityPx: Float? = null, spec: AnimationSpec<Float> = SettleSpring): Job {
        val startVelocity = when {
            velocityPx != null -> -velocityPx / travelPx
            settleJob?.isActive == true -> velocity
            else -> 0f
        }
        stop()
        velocity = startVelocity
        return scope.launch {
            animate(position, target, startVelocity, spec) { current, speed ->
                position = current
                velocity = speed
            }
        }.also { settleJob = it }
    }
}
