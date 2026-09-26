package com.example.samsonic.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.OneUiRadius
import kotlin.math.roundToInt

/**
 * One tab of the floating nav bar, and of a chrome-sized [GlassTabBar] with
 * icons: just the icon, until the indicator comes over it, when its label slides
 * out beside it (and folds back as the indicator leaves), fading in and out with
 * a soft edge. The label and the icon's tint follow [emphasis] (1 under the
 * indicator, 0 a tab away), and taps give a soft shrink-and-glow instead of a ripple.
 * [emphasis] is read as the tab is laid out and drawn, so the indicator sliding
 * past only recomposes it as its label comes and goes.
 */
@Composable
fun IconLabelTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    emphasis: () -> Float,
    verticalPadding: Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
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
    val restTint = MaterialTheme.colorScheme.onSurfaceVariant
    val onTint = MaterialTheme.colorScheme.primary
    val painter = rememberVectorPainter(icon)
    val showLabel by remember(emphasis) { derivedStateOf { emphasis() > 0f } }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = verticalPadding)
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
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tinted as it's drawn, following the indicator without recomposing.
        Box(
            Modifier
                .size(24.dp)
                .semantics { contentDescription = label }
                .drawBehind {
                    with(painter) { draw(size, colorFilter = ColorFilter.tint(lerp(restTint, onTint, emphasis()))) }
                },
        )
        // The label opens with how near the indicator is ([emphasis]), as the tab
        // widens with it, so it keeps pace with the indicator however fast it
        // moves (a swipe racing along the bar shows each label as it passes) and
        // folds away as the tab narrows rather than being cut short to "…".
        if (showLabel) {
            Text(
                text = label,
                modifier = Modifier
                    .revealWidth(emphasis, edgeFade = 20.dp)
                    // Fades in and out as it opens and closes, easing at both ends.
                    .graphicsLayer {
                        val t = ((emphasis() - 0.1f) / 0.9f).coerceIn(0f, 1f)
                        alpha = t * t * (3 - 2 * t)
                    }
                    .padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/**
 * Lays the content out at its full width but takes up only [fraction] of it (and
 * never more than there is room for), so it opens and closes like a sliding reveal
 * without its content ever reflowing. Where it's cut short, the last [edgeFade] of
 * what shows fades out, so letters dissolve at the edge instead of being sliced.
 */
private fun Modifier.revealWidth(fraction: () -> Float, edgeFade: Dp): Modifier {
    // Whether the last layout cut the content short; drawing (after it) fades the edge then.
    val cut = BooleanArray(1)
    return clipToBounds()
        // Offscreen, so the fade masks the text alone, not what's behind it.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (cut[0]) {
                val fade = edgeFade.toPx().coerceAtMost(size.width)
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
            val width = (placeable.width * fraction()).roundToInt().coerceAtMost(constraints.maxWidth)
            cut[0] = width < placeable.width
            layout(width, placeable.height) { placeable.placeRelative(0, 0) }
        }
}
