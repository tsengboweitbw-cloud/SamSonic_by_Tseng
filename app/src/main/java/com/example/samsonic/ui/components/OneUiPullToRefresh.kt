package com.example.samsonic.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.glassSurface
import com.example.samsonic.ui.theme.accentPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

private val IndicatorSize = 42.dp
private val Threshold = 64.dp
    //PullToRefreshDefaults.PositionalThreshold

/**
 * One UI style pull-to-refresh: the [content] itself follows the finger down
 * (with the drag resistance of [pullToRefresh]), opening a gap above it where
 * a small glass circle fills its arc as you pull. Past the threshold a haptic
 * tick confirms; on release the content springs to rest under the spinning
 * indicator, then springs back up when [isRefreshing] ends.
 *
 * [topInset] is empty space the content already leaves at its top, which the
 * indicator counts as part of the gap it centres in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    val haptics = LocalHapticFeedback.current
    val refreshing by rememberUpdatedState(isRefreshing)

    // A tick each time the pull crosses the threshold on its way down.
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction >= 1f }
            .distinctUntilChanged()
            .drop(1)
            .filter { it && !refreshing }
            .collect { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullToRefresh(isRefreshing = isRefreshing, state = state, threshold = Threshold, onRefresh = onRefresh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = state.distanceFraction * Threshold.toPx() },
        ) { content() }
        RefreshIndicator(
            state = state,
            isRefreshing = isRefreshing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    // Centred in the gap the content has pulled open (plus its own top inset).
                    val gap = state.distanceFraction * Threshold.toPx() + topInset.toPx()
                    translationY = ((gap - IndicatorSize.toPx()) / 2f).coerceAtLeast(0f)
                    val shown = state.distanceFraction.coerceIn(0f, 1f)
                    alpha = shown
                    scaleX = 0.6f + 0.4f * shown
                    scaleY = scaleX
                },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshIndicator(state: PullToRefreshState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(IndicatorSize)
            .glassSurface(
                shape = CircleShape,
                hazeState = null,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = GlassAlpha.Nav,
            ),
    ) {
        val arcModifier = Modifier.fillMaxSize().padding(9.dp)
        if (isRefreshing) SpinningArc(arcModifier) else PullArc({ state.distanceFraction }, arcModifier)
    }
}

/** While pulling: the arc fills with the pull, turning a little as it grows. */
@Composable
private fun PullArc(fraction: () -> Float, modifier: Modifier) {
    val brush = refreshArcBrush()
    Canvas(modifier = modifier) {
        val progress = fraction().coerceIn(0f, 1f)
        rotate(progress * 120f) {
            drawArc(brush, startAngle = -90f, sweepAngle = progress * 300f, useCenter = false, style = arcStroke())
        }
    }
}

/** While refreshing: the arc spins and breathes. Composed only then, so idle pages run no animation. */
@Composable
private fun SpinningArc(modifier: Modifier) {
    val brush = refreshArcBrush()
    val spin = rememberInfiniteTransition(label = "refreshSpin")
    val rotation = spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "rotation",
    )
    val sweep = spin.animateFloat(
        initialValue = 60f,
        targetValue = 260f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "sweep",
    )
    Canvas(modifier = modifier) {
        rotate(rotation.value) {
            drawArc(brush, startAngle = -90f, sweepAngle = sweep.value, useCenter = false, style = arcStroke())
        }
    }
}

private fun DrawScope.arcStroke() = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)

/** The arc's stroke: the accent sweeping round into its neighbour and back, so it has no seam. */
@Composable
private fun refreshArcBrush(): Brush {
    val palette = MaterialTheme.accentPalette
    return Brush.sweepGradient(listOf(palette.primary, palette.secondary, palette.primary))
}
