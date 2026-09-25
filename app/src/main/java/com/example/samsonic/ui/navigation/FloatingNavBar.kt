package com.example.samsonic.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.components.IconLabelTab
import com.example.samsonic.ui.components.TabIndicatorSpring
import com.example.samsonic.ui.components.drawTabIndicator
import com.example.samsonic.ui.components.tabProximity
import com.example.samsonic.ui.components.tabWeights
import com.example.samsonic.ui.components.tabIndicatorColors
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState

private val BarPadding = 8.dp

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
        position.animateTo(targetIndex.toFloat(), TabIndicatorSpring)
    }
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selectedIndex >= 0) 1f else 0f,
        animationSpec = tween(220),
        label = "navIndicatorAlpha",
    )

    val count = destinations.size
    val p = position.value.coerceIn(0f, (count - 1).toFloat())
    val weights = tabWeights(count, p)
    val indicatorColors = tabIndicatorColors()

    Row(
        modifier = modifier
            .glassSurface(
                shape = RoundedCornerShape(OneUiRadius.Pill),
                hazeState = hazeState,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = GlassAlpha.Nav,
            )
            .drawBehind { drawTabIndicator(weights, p, BarPadding.toPx(), indicatorColors, indicatorAlpha) }
            .padding(horizontal = BarPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEachIndexed { index, destination ->
            IconLabelTab(
                icon = destination.icon,
                label = destination.label,
                selected = index == selectedIndex,
                emphasis = tabProximity(index, p) * indicatorAlpha,
                weight = weights[index],
                verticalPadding = BarPadding,
                onClick = { onSelect(destination.route) },
            )
        }
    }
}

