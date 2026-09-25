package com.example.samsonic.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.example.samsonic.ui.theme.accentPalette
import kotlin.math.abs
import kotlin.math.floor

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

/** 1 at the indicator's center, falling to 0 one tab away. */
fun tabProximity(index: Int, position: Float): Float =
    (1f - abs(index - position)).coerceIn(0f, 1f)

/** Each tab's width weight at [position]: the tab under the indicator widens by [extraWeight]. */
fun tabWeights(count: Int, position: Float, extraWeight: Float = SelectedTabExtraWeight): List<Float> =
    List(count) { i -> 1f + extraWeight * tabProximity(i, position) }

/**
 * Draws the indicator pill at [position] over tabs laid out by [weights],
 * inset by [inset] px from the bar's edges, growing and shrinking with the
 * tab it passes over. Filled with [colors] as a gradient across the whole bar, so
 * the pill shifts shade as it glides from one end of the bar to the other.
 */
fun DrawScope.drawTabIndicator(weights: List<Float>, position: Float, inset: Float, colors: List<Color>, alpha: Float = 1f) {
    val count = weights.size
    if (count == 0 || alpha <= 0f) return
    val unit = (size.width - inset * 2) / weights.sum()
    val k = floor(position).toInt().coerceIn(0, count - 1)
    val t = position - k
    val next = weights.getOrElse(k + 1) { weights[k] }
    val left = inset + (weights.take(k).sum() + t * weights[k]) * unit
    val width = (weights[k] * (1 - t) + next * t) * unit
    val height = size.height - inset * 2
    drawRoundRect(
        brush = Brush.horizontalGradient(colors, startX = inset, endX = size.width - inset),
        topLeft = Offset(left, inset),
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2),
        alpha = alpha,
    )
}

/** The indicator's fill: a faint wash of the accent easing into its neighbour. */
@Composable
fun tabIndicatorColors(): List<Color> {
    val palette = MaterialTheme.accentPalette
    return listOf(palette.primary, palette.secondary).map { it.copy(alpha = 0.2f) }
}
