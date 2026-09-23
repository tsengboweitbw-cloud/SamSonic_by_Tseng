package com.example.samsonic.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.floor

private val BarPadding = 8.dp
private const val SelectedExtraWeight = 0.8f

// Slightly underdamped so the indicator glides and settles with a soft
// overshoot instead of stopping dead, like One UI's tab pill.
private val IndicatorSpring = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)

/**
 * One UI 9.0 floating pill bottom navigation: a frosted-glass bar over the
 * scrolling content, in place of Material's edge-to-edge opaque NavigationBar.
 * One shared indicator pill glides between tabs, the selected tab widens to
 * show its label, and taps give a soft press-scale instead of a ripple.
 */
@Composable
fun FloatingNavBar(
    destinations: List<BottomDestination>,
    selectedRoutes: Set<String>,
    onSelect: (String) -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = destinations.indexOfFirst { it.route in selectedRoutes }
    // Detail pages (album, artist...) have no tab: keep the pill where it was and fade it out.
    val lastIndex = remember { IntArray(1) { selectedIndex.coerceAtLeast(0) } }
    if (selectedIndex >= 0) lastIndex[0] = selectedIndex
    val targetIndex = lastIndex[0]

    // One continuous position drives both tab widths and the indicator, so the
    // two can never drift apart mid-animation.
    val position = remember { Animatable(targetIndex.toFloat()) }
    LaunchedEffect(targetIndex) {
        position.animateTo(targetIndex.toFloat(), IndicatorSpring)
    }
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selectedIndex >= 0) 1f else 0f,
        animationSpec = tween(220),
        label = "navIndicatorAlpha",
    )

    val count = destinations.size
    val p = position.value.coerceIn(0f, (count - 1).toFloat())
    val weights = List(count) { i -> 1f + SelectedExtraWeight * proximity(i, p) }
    val indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Row(
        modifier = modifier
            .glassSurface(
                shape = RoundedCornerShape(OneUiRadius.Pill),
                hazeState = hazeState,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = GlassAlpha.Nav,
            )
            .drawBehind {
                if (indicatorAlpha <= 0f || count == 0) return@drawBehind
                val inset = BarPadding.toPx()
                val unit = (size.width - inset * 2) / weights.sum()
                val k = floor(p).toInt().coerceAtMost(count - 1)
                val t = p - k
                val next = weights.getOrElse(k + 1) { weights[k] }
                val left = inset + (weights.take(k).sum() + t * weights[k]) * unit
                val width = (weights[k] * (1 - t) + next * t) * unit
                val height = size.height - inset * 2
                drawRoundRect(
                    color = indicatorColor,
                    topLeft = Offset(left, inset),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(height / 2),
                    alpha = indicatorAlpha,
                )
            }
            .padding(horizontal = BarPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEachIndexed { index, destination ->
            NavTab(
                destination = destination,
                selected = index == selectedIndex,
                emphasis = proximity(index, p) * indicatorAlpha,
                weight = weights[index],
                onClick = { onSelect(destination.route) },
            )
        }
    }
}

/** 1 at the indicator's center, falling to 0 one tab away. */
private fun proximity(index: Int, position: Float): Float =
    (1f - abs(index - position)).coerceIn(0f, 1f)

@Composable
private fun RowScope.NavTab(
    destination: BottomDestination,
    selected: Boolean,
    emphasis: Float,
    weight: Float,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "navPressScale",
    )
    val pressGlow by animateFloatAsState(
        targetValue = if (pressed) 0.08f else 0f,
        animationSpec = tween(if (pressed) 90 else 260),
        label = "navPressGlow",
    )
    val glowColor = MaterialTheme.colorScheme.onSurface
    val tint = lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.primary, emphasis)

    Row(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(vertical = BarPadding)
            .clip(RoundedCornerShape(OneUiRadius.Pill))
            .drawBehind { if (pressGlow > 0f) drawRect(glowColor, alpha = pressGlow) }
            // No ripple: One UI answers a tap with a soft shrink-and-glow.
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            tint = tint,
        )
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) +
                fadeIn(tween(durationMillis = 220, delayMillis = 70)),
            exit = shrinkHorizontally(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) +
                fadeOut(tween(durationMillis = 120)),
        ) {
            Text(
                text = destination.label,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
