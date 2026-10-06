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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.samsonic.ui.common.LocalPageHeaderOpen
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
import com.example.samsonic.ui.theme.AccentSheen
import com.example.samsonic.ui.theme.LocalChromeBlurScale
import com.example.samsonic.ui.theme.GlassAlpha
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.example.samsonic.ui.theme.glassSurface
import com.example.samsonic.ui.theme.accentPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

private val IndicatorSize = 36.dp
private val RestGap = 4.dp
private val Threshold = 64.dp
    //PullToRefreshDefaults.PositionalThreshold

/**
 * One UI style pull-to-refresh: the [content] stays put and a small glass circle
 * slides down over it, just under the header, filling its arc as you pull. Past
 * the threshold a haptic tick confirms; on release it spins until [isRefreshing]
 * ends, then slides back up.
 *
 * [topInset] is empty space the content already leaves at its top, which the
 * indicator rests below.
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
    val haze = rememberHazeState()
    val haptics = LocalHapticFeedback.current
    val refreshing by rememberUpdatedState(isRefreshing)
    // Under a lowered header a pull first opens it; only with it open does a pull refresh.
    val headerOpen = LocalPageHeaderOpen.current
    val enabled by remember(headerOpen) { derivedStateOf { headerOpen() } }

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
            .pullToRefresh(isRefreshing = isRefreshing, state = state, enabled = enabled, threshold = Threshold, onRefresh = onRefresh),
    ) {
        Box(modifier = Modifier.fillMaxSize().hazeSource(haze)) { content() }
        RefreshIndicator(
            state = state,
            haze = haze,
            isRefreshing = isRefreshing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    // Floats over the content, below the header: it slides down from the top
                    // edge to its resting place just under the content's own top inset.
                    val shown = state.distanceFraction.coerceIn(0f, 1f)
                    val rest = topInset.toPx() + RestGap.toPx()
                    translationY = -IndicatorSize.toPx() + shown * (IndicatorSize.toPx() + rest)
                    alpha = shown
                    scaleX = 0.6f + 0.4f * shown
                    scaleY = scaleX
                },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshIndicator(state: PullToRefreshState, haze: HazeState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(IndicatorSize)
            .glassSurface(
                shape = CircleShape,
                hazeState = haze,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = GlassAlpha.Nav,
                sheen = AccentSheen.Chrome,
                inputScale = LocalChromeBlurScale.current,
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
