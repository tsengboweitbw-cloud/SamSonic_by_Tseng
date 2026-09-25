package com.example.samsonic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.accentPalette
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * The gliding tab indicator shared by the floating nav bar and the chrome-sized
 * GlassTabBar, so the two pills move identically. One continuous position (in
 * tabs) drives both the tab widths and the indicator, so they never drift
 * apart mid-animation.
 */

/** How much wider the tab under the indicator grows than the others. */
const val SelectedTabExtraWeight = 0.8f

// Slightly underdamped so the indicator glides and settles with a soft
// overshoot instead of stopping dead, like One UI's tab pill.
val TabIndicatorSpring = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)

// How long (time constant, seconds) the indicator takes to close the gap to a
// finger that starts a swipe away from it.
private const val SwipeCatchUpSeconds = 0.04f

// The indicator's speed is averaged over about this long, so one quick frame doesn't read as a fast swipe.
private const val SwipeSpeedSmoothingSeconds = 0.08f

/**
 * Keeps this indicator position with a swiping finger ([target], in tabs) every
 * frame until cancelled: it moves exactly as the finger does, however fast, and
 * any gap it started with (the finger landing away from it) closes smoothly within
 * a few frames, so it neither jumps to the finger nor trails it. [onFrame] runs
 * after each move with the indicator's speed, in tabs a second.
 */
suspend fun Animatable<Float, AnimationVector1D>.followSwipe(target: () -> Float, onFrame: (speed: Float) -> Unit = {}) {
    var gap = target() - value
    var speed = 0f
    var lastNanos = withFrameNanos { it }
    while (true) {
        val now = withFrameNanos { it }
        val seconds = ((now - lastNanos) / 1e9f).coerceAtLeast(1e-4f)
        lastNanos = now
        gap *= exp(-seconds / SwipeCatchUpSeconds)
        val next = target() - gap
        speed += (abs(next - value) / seconds - speed) * (1f - exp(-seconds / SwipeSpeedSmoothingSeconds))
        snapTo(next)
        onFrame(speed)
    }
}

// Letting go of a swipe this fast carries on to the next tab the way it was going.
private val SwipeFlingVelocity = 500.dp

/** 1 at the indicator's center, falling to 0 one tab away. */
fun tabProximity(index: Int, position: Float): Float =
    (1f - abs(index - position)).coerceIn(0f, 1f)

/** Each tab's width weight at [position]: the tab under the indicator widens by [extraWeight]. */
fun tabWeights(count: Int, position: Float, extraWeight: Float = SelectedTabExtraWeight): List<Float> =
    List(count) { i -> 1f + extraWeight * tabProximity(i, position) }

/** Where the indicator sits across a bar [barWidth] px wide: [block] gets its left edge and width. */
private inline fun <T> indicatorSpan(
    weights: List<Float>,
    position: Float,
    inset: Float,
    barWidth: Float,
    block: (left: Float, width: Float) -> T,
): T {
    val count = weights.size
    val unit = (barWidth - inset * 2) / weights.sum()
    val k = floor(position).toInt().coerceIn(0, count - 1)
    val t = position - k
    val next = weights.getOrElse(k + 1) { weights[k] }
    val left = inset + (weights.take(k).sum() + t * weights[k]) * unit
    return block(left, (weights[k] * (1 - t) + next * t) * unit)
}

/**
 * Draws the indicator pill at [position] over tabs laid out by [weights],
 * inset by [inset] px from the bar's edges, growing and shrinking with the
 * tab it passes over. Filled with [colors] as a gradient across the whole bar, so
 * the pill shifts shade as it glides from one end of the bar to the other.
 */
fun DrawScope.drawTabIndicator(weights: List<Float>, position: Float, inset: Float, colors: List<Color>, alpha: Float = 1f) {
    if (weights.isEmpty() || alpha <= 0f) return
    val height = size.height - inset * 2
    indicatorSpan(weights, position, inset, size.width) { left, width ->
        drawRoundRect(
            brush = Brush.horizontalGradient(colors, startX = inset, endX = size.width - inset),
            topLeft = Offset(left, inset),
            size = Size(width, height),
            cornerRadius = CornerRadius(height / 2),
            alpha = alpha,
        )
    }
}

/**
 * The indicator position (in tabs) that centres the indicator on [x] px across the
 * bar, the ends holding it at the first and last tab. Tabs widen as the indicator
 * passes, so this searches for it rather than dividing the width evenly.
 */
private fun tabPositionAt(x: Float, barWidth: Float, inset: Float, count: Int, extraWeight: Float): Float {
    var low = 0f
    var high = (count - 1).toFloat()
    repeat(24) {
        val mid = (low + high) / 2
        val centre = indicatorSpan(tabWeights(count, mid, extraWeight), mid, inset, barWidth) { left, width -> left + width / 2 }
        if (centre < x) low = mid else high = mid
    }
    return (low + high) / 2
}

/**
 * Lets a tab bar of [count] tabs be swiped: as a finger slides along it, [onSwipe]
 * gets the indicator position (in tabs) that keeps the indicator under the finger,
 * and on letting go [onSwipeEnd] gets the tab to settle on: the nearest, or after a
 * flick, the next one the way it was going. Goes before the bar's own padding, so it
 * measures the width the indicator is drawn across; [inset] and [extraWeight] are
 * the ones it is drawn with. Taps still reach the tabs: a swipe only starts past
 * the touch slop.
 */
@Composable
fun Modifier.tabBarSwipe(
    count: Int,
    inset: Dp,
    extraWeight: Float,
    enabled: Boolean,
    onSwipe: (Float) -> Unit,
    onSwipeEnd: (Int) -> Unit,
): Modifier {
    val swipe by rememberUpdatedState(onSwipe)
    val swipeEnd by rememberUpdatedState(onSwipeEnd)
    if (!enabled || count < 2) return this
    return pointerInput(count, inset, extraWeight) {
        val flingVelocity = SwipeFlingVelocity.toPx()
        val tracker = VelocityTracker()
        var last = 0f
        fun settle(velocity: Float) {
            val target = when {
                velocity > flingVelocity -> ceil(last)
                velocity < -flingVelocity -> floor(last)
                else -> last
            }
            swipeEnd(target.roundToInt().coerceIn(0, count - 1))
        }
        detectHorizontalDragGestures(
            onDragStart = { tracker.resetTracking() },
            onDragEnd = { settle(tracker.calculateVelocity().x) },
            onDragCancel = { settle(0f) },
        ) { change, _ ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            last = tabPositionAt(change.position.x, size.width.toFloat(), inset.toPx(), count, extraWeight)
            swipe(last)
        }
    }
}

/** The indicator's fill: a faint wash of the accent easing into its neighbour. */
@Composable
fun tabIndicatorColors(): List<Color> {
    val palette = MaterialTheme.accentPalette
    return listOf(palette.primary, palette.secondary).map { it.copy(alpha = 0.2f) }
}
