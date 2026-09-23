package com.example.samsonic.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
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
 * content) shrinks; what comes before it, like a glass surface, stays put.
 */
@Composable
fun Modifier.pressClickable(onClick: () -> Unit, pressedScale: Float = 0.85f, enabled: Boolean = true): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}
