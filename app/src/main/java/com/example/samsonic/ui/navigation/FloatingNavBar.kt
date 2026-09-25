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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.samsonic.R
import com.example.samsonic.ui.components.IconLabelTab
import com.example.samsonic.ui.components.SelectedTabExtraWeight
import com.example.samsonic.ui.components.TabFollowSpring
import com.example.samsonic.ui.components.TabIndicatorSpring
import com.example.samsonic.ui.components.drawTabIndicator
import com.example.samsonic.ui.components.tabBarSwipe
import com.example.samsonic.ui.components.tabProximity
import com.example.samsonic.ui.components.tabWeights
import com.example.samsonic.ui.components.tabIndicatorColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState

private val BarPadding = 8.dp

/**
 * One UI 9.0 floating pill bottom navigation: a frosted-glass bar over the
 * scrolling content, in place of Material's edge-to-edge opaque NavigationBar.
 * One shared indicator pill glides between tabs, the selected tab widens to
 * show its label, and taps give a soft press-scale instead of a ripple. A finger
 * sliding along the bar carries the pill with it, opening each tab it reaches.
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
    val scope = rememberCoroutineScope()
    // A finger sliding along the bar, which brings the pill back on a detail page.
    var swiping by remember { mutableStateOf(false) }
    // The tab the swipe last opened (or started on), so each tab opens once as the pill reaches it.
    var swipeTab by remember { mutableIntStateOf(0) }
    LaunchedEffect(targetIndex) {
        // Mid-swipe the pill stays with the finger, whatever the swipe has opened.
        if (!swiping) position.animateTo(targetIndex.toFloat(), TabIndicatorSpring)
    }
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selectedIndex >= 0 || swiping) 1f else 0f,
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
            // Swiping along the bar slides the pill under the finger, opening each
            // tab as the pill comes nearer it than its neighbours, so the page
            // changes with the finger; letting go opens where the pill settles.
            .tabBarSwipe(
                count = count,
                inset = BarPadding,
                extraWeight = SelectedTabExtraWeight,
                enabled = true,
                onSwipe = { at ->
                    if (!swiping) {
                        swiping = true
                        swipeTab = targetIndex
                    }
                    scope.launch { position.animateTo(at, TabFollowSpring) }
                    val nearest = at.roundToInt()
                    if (nearest != swipeTab) {
                        swipeTab = nearest
                        onSelect(destinations[nearest].route)
                    }
                },
                onSwipeEnd = { index ->
                    swiping = false
                    if (index != swipeTab) onSelect(destinations[index].route)
                    scope.launch { position.animateTo(index.toFloat(), TabIndicatorSpring) }
                },
            )
            .padding(horizontal = BarPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEachIndexed { index, destination ->
            IconLabelTab(
                icon = destination.icon,
                label = stringResource(destination.label),
                selected = index == selectedIndex,
                emphasis = tabProximity(index, p) * indicatorAlpha,
                weight = weights[index],
                verticalPadding = BarPadding,
                onClick = { onSelect(destination.route) },
            )
        }
    }
}

