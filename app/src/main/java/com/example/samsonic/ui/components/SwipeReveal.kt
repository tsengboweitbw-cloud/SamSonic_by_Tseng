package com.example.samsonic.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRow
import com.example.samsonic.ui.theme.glassSurface

/**
 * The rounded glass panel behind the sliding card, spanning the whole row so the card's
 * blur has the action's color to frost. Until the swipe passes the threshold it's a
 * translucent wash of that color; past it the panel turns dense and the icon pops. The icon
 * alone (no label; TalkBack reads [SwipeAction.label]) sits at the outer edge, in the gap
 * the card opens.
 */
@Composable
internal fun SwipeReveal(
    action: SwipeAction,
    progress: Float,
    armed: Boolean,
    alignEnd: Boolean,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val actionColor = if (action.destructive) colors.error else colors.primary
    val onActionColor = if (action.destructive) colors.onError else colors.onPrimary
    val density by animateFloatAsState(if (armed) GlassAlpha.Panel else 0.3f, label = "swipeRevealDensity")
    val contentColor by animateColorAsState(
        targetValue = if (armed) onActionColor else actionColor,
        label = "swipeRevealContent",
    )
    val pop by animateFloatAsState(
        targetValue = if (armed) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 600f),
        label = "swipeRevealPop",
    )
    Box(
        modifier = modifier
            .padding(OneUiRow.Inset)
            .glassSurface(shape = OneUiRow.Shape, hazeState = null, tint = actionColor, alpha = density)
            .padding(horizontal = 20.dp),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    alpha = progress
                    scaleX = pop
                    scaleY = pop
                },
        )
    }
}
