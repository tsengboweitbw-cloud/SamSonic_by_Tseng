package com.example.samsonic.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.drawBehind
import com.example.samsonic.ui.common.HoverGlowAlpha
import com.example.samsonic.ui.common.hovered
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An icon button without M3's translucent ripple circle: a tap just shrinks the icon
 * briefly and springs it back, like the floating nav bar's tabs (minus their glow).
 */
@Composable
fun PressIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .pressClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Makes this element a button that answers a tap by shrinking briefly and springing
 * back, with no ripple or pressed background. Everything chained after it (and the
 * content) shrinks; what comes before it, like a glass surface, stays put. A mouse over
 * it lights it faintly, within whatever shape it's clipped to before this.
 */
@Composable
fun Modifier.pressClickable(
    onClick: () -> Unit,
    pressedScale: Float = 0.85f,
    enabled: Boolean = true,
    // A long press; none by default.
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    val glow by animateFloatAsState(
        targetValue = if (enabled && hovered(interaction)) HoverGlowAlpha else 0f,
        animationSpec = tween(160),
        label = "hoverGlow",
    )
    val glowColor = MaterialTheme.colorScheme.onSurface
    return drawBehind { if (glow > 0f) drawRect(glowColor, alpha = glow) }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = onClick,
        )
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}
