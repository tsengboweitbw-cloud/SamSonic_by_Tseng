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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs

private val BarPadding = 6.dp
/** Height of a [GlassTabBar], for callers reserving its space. */
val GlassTabBarHeight = 56.dp

// Same slightly underdamped glide as the floating nav bar's indicator.
private val IndicatorSpring = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)

/**
 * A frosted-glass pill of text tabs, styled after the floating nav bar: one
 * shared indicator pill glides between equal-width tabs, the selected label
 * takes the accent color, and taps give a soft press-scale instead of a ripple.
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
) {
    val animated = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        animated.animateTo(selectedIndex.toFloat(), IndicatorSpring)
    }
    val count = labels.size
    val p = (position ?: animated.value).coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat())
    val indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GlassTabBarHeight)
            .then(if (glass) Modifier.glassTabPill(hazeState) else Modifier)
            .drawBehind {
                if (count == 0) return@drawBehind
                val inset = BarPadding.toPx()
                val unit = (size.width - inset * 2) / count
                val height = size.height - inset * 2
                drawRoundRect(
                    color = indicatorColor,
                    topLeft = Offset(inset + p * unit, inset),
                    size = Size(unit, height),
                    cornerRadius = CornerRadius(height / 2),
                )
            }
            .padding(horizontal = BarPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            GlassTab(
                label = label,
                selected = index == selectedIndex,
                emphasis = (1f - abs(index - p)).coerceIn(0f, 1f),
                enabled = enabled,
                onClick = { onSelect(index) },
            )
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
)

@Composable
private fun RowScope.GlassTab(
    label: String,
    selected: Boolean,
    emphasis: Float,
    enabled: Boolean,
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
            .weight(1f)
            .fillMaxHeight()
            .padding(vertical = BarPadding)
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
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
