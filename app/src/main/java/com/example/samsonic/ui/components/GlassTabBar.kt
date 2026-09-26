package com.example.samsonic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.AccentSheen
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiChrome
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How big a [GlassTabBar] is, and how it moves. [Chrome] matches the floating
 * nav bar exactly (height, indicator inset, label size, and the tab under the
 * indicator widening as it passes), for a tab pill that sits on the page as
 * chrome; [Compact] keeps equal-width tabs, for pickers inside panels.
 */
enum class GlassTabBarSize(val height: Dp, internal val padding: Dp, internal val selectedExtraWeight: Float) {
    Compact(56.dp, 6.dp, 0f),
    Chrome(OneUiChrome.BarHeight, 8.dp, SelectedTabExtraWeight),
}

/**
 * A frosted-glass pill of tabs, styled after the floating nav bar: one shared
 * indicator pill glides between the tabs (see [GlassTabBarSize]), and taps
 * give a soft press-scale instead of a ripple; a finger sliding along the bar
 * carries the indicator with it. With [icons], each tab is the
 * nav bar's own [IconLabelTab] (icon only, label sliding out when selected);
 * without, the tabs are text and the selected label takes the accent color.
 */
@Composable
fun GlassTabBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    // The indicator's live position in tabs (e.g. a pager's page plus its
    // offset fraction), so it tracks a swipe under the finger. Without it the
    // indicator springs to [selectedIndex] on its own.
    position: Float? = null,
    // Frozen: taps do nothing and give no press feedback (the caller dims it).
    enabled: Boolean = true,
    // False draws only the tabs and indicator, for a caller that supplies
    // its own glass pill (so the pill can stay put while its tabs swap).
    glass: Boolean = true,
    barSize: GlassTabBarSize = GlassTabBarSize.Compact,
    // One per label, in order.
    icons: List<ImageVector>? = null,
    // Gets the finger's position (in tabs) as it slides along the bar, for a
    // caller moving [position] with it (e.g. scrolling a pager along). Without
    // it the bar carries its own indicator under the finger.
    onSwipe: ((Float) -> Unit)? = null,
    // The tab a swipe let go on. Without it, a new tab goes to [onSelect].
    onSwipeEnd: ((Int) -> Unit)? = null,
) {
    val animated = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        animated.animateTo(selectedIndex.toFloat(), TabIndicatorSpring)
    }
    val scope = rememberCoroutineScope()
    // Carrying the indicator itself (no [onSwipe]), which then outranks [position]
    // until it settles, so handing back doesn't jump.
    var swiping by remember { mutableStateOf(false) }
    // Where the finger has the indicator, in tabs; read every frame while it follows.
    val finger = remember { FloatArray(1) }
    var follow by remember { mutableStateOf<Job?>(null) }
    val count = labels.size
    val p = (if (swiping) animated.value else position ?: animated.value)
        .coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat())
    val weights = tabWeights(count, p, barSize.selectedExtraWeight)
    val indicatorColors = tabIndicatorColors()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(barSize.height)
            .then(if (glass) Modifier.glassTabPill(hazeState) else Modifier)
            .drawBehind { drawTabIndicator(weights, p, barSize.padding.toPx(), indicatorColors) }
            .tabBarSwipe(
                count = count,
                inset = barSize.padding,
                extraWeight = barSize.selectedExtraWeight,
                enabled = enabled,
                onSwipe = { at ->
                    if (onSwipe != null) {
                        onSwipe(at)
                    } else {
                        finger[0] = at
                        if (!swiping) {
                            // Picks up from wherever [position] had it.
                            val from = if (position != null) p else null
                            swiping = true
                            follow = scope.launch {
                                if (from != null) animated.snapTo(from)
                                animated.followSwipe(target = { finger[0] })
                            }
                        }
                    }
                },
                onSwipeEnd = { index ->
                    follow?.cancel()
                    when {
                        onSwipeEnd != null -> onSwipeEnd(index)
                        index != selectedIndex -> onSelect(index)
                    }
                    if (swiping) {
                        scope.launch {
                            try {
                                animated.animateTo(index.toFloat(), TabIndicatorSpring)
                            } finally {
                                swiping = false
                            }
                        }
                    }
                },
            )
            .padding(horizontal = barSize.padding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val icon = icons?.getOrNull(index)
            if (icon != null) {
                IconLabelTab(
                    icon = icon,
                    label = label,
                    selected = index == selectedIndex,
                    emphasis = tabProximity(index, p),
                    weight = weights[index],
                    verticalPadding = barSize.padding,
                    enabled = enabled,
                    onClick = { onSelect(index) },
                )
            } else {
                GlassTab(
                    label = label,
                    selected = index == selectedIndex,
                    emphasis = tabProximity(index, p),
                    weight = weights[index],
                    enabled = enabled,
                    size = barSize,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

/** The frosted pill a [GlassTabBar] sits on, for callers drawing it with `glass = false`. */
@Composable
fun Modifier.glassTabPill(hazeState: HazeState?): Modifier = glassSurface(
    shape = RoundedCornerShape(OneUiRadius.Pill),
    hazeState = hazeState,
    tint = MaterialTheme.colorScheme.surfaceContainerHigh,
    alpha = GlassAlpha.Nav,
    sheen = AccentSheen.Chrome,
)

@Composable
private fun RowScope.GlassTab(
    label: String,
    selected: Boolean,
    emphasis: Float,
    weight: Float,
    enabled: Boolean,
    size: GlassTabBarSize,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "tabPressScale",
    )
    val pressGlow by animateFloatAsState(
        targetValue = if (pressed) 0.08f else 0f,
        animationSpec = tween(if (pressed) 90 else 260),
        label = "tabPressGlow",
    )
    val glowColor = MaterialTheme.colorScheme.onSurface
    val color = lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.primary, emphasis)

    Row(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(vertical = size.padding)
            .clip(RoundedCornerShape(OneUiRadius.Pill))
            .drawBehind { if (pressGlow > 0f) drawRect(glowColor, alpha = pressGlow) }
            // No ripple: One UI answers a tap with a soft shrink-and-glow.
            .selectable(
                selected = selected,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (size == GlassTabBarSize.Chrome) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
