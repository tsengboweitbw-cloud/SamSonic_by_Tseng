package com.example.samsonic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.samsonic.ui.theme.OneUiRadius

/**
 * One tab of the floating nav bar, and of a chrome-sized [GlassTabBar] with
 * icons: just the icon, until selected, when its label slides out beside it.
 * The icon's tint follows [emphasis] (1 under the indicator, 0 a tab away),
 * and taps give a soft shrink-and-glow instead of a ripple.
 */
@Composable
fun RowScope.IconLabelTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    emphasis: Float,
    weight: Float,
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
    val tint = lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.primary, emphasis)

    Row(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
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
        Icon(
            imageVector = icon,
            contentDescription = label,
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
                text = label,
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
