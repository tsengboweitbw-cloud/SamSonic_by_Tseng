package com.example.samsonic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.AccentPalette
import com.example.samsonic.ui.theme.GlassRimWidth
import com.example.samsonic.ui.theme.glassRimBrush
import com.example.samsonic.ui.theme.glassSurface
import kotlinx.coroutines.launch

/**
 * Play's surface: the same frosted glass as the buttons beside it, tinted with the
 * accent instead of the surface color, sheened into its neighbour color toward the
 * bottom-right, and lifted by the same soft shadow ([listActionShadow]).
 */
@Composable
internal fun Modifier.accentGlass(palette: AccentPalette): Modifier = this
    .listActionShadow()
    .glassSurface(
        shape = CircleShape,
        hazeState = null,
        tint = palette.primary,
        alpha = AccentGlassAlpha,
        rim = false,
    )
    // Already clipped to the circle by the glass.
    .background(Brush.linearGradient(listOf(Color.Transparent, palette.secondary.copy(alpha = 0.6f))))
    .border(GlassRimWidth, glassRimBrush(), CircleShape)

/**
 * The soft black shadow under every button of the row, Play and the neutral ones alike.
 * Not Modifier.shadow, whose polygon core shows through the see-through glass (see circleGlow).
 */
internal fun Modifier.listActionShadow(): Modifier = circleGlow(Color.Black.copy(alpha = 0.25f), width = 8.dp, offsetY = 2.dp)

// Thinner than the neutral buttons' glass: enough accent to mark Play out, still see-through.
internal const val AccentGlassAlpha = 0.72f

// Pressed, a button sinks this far, and springs back past its size on release.
internal const val PressedScale = 0.8f
// Loose springs, so both the button and its icon wobble a few times before they settle.
internal val PressSpring = spring<Float>(dampingRatio = 0.3f, stiffness = 450f)
internal val BounceSpring = spring<Float>(dampingRatio = 0.25f, stiffness = 380f)
// How hard a tap kicks the button in (scale per second) and its icon out: even a quick
// tap, which barely presses the button before it's let go, gets a full bounce.
internal const val TapKick = 4f
internal const val IconKick = 6f

/**
 * A round button that answers a tap with some life: it sinks deep while held, a tap
 * kicks it into a springy bounce that overshoots and wobbles back, a haptic tick
 * confirms it, and its icon bounces the other way ([content] gets the icon's extra
 * scale, around 0, to apply).
 */
@Composable
internal fun RoundButton(
    onClick: () -> Unit,
    contentColor: Color,
    surface: Modifier,
    modifier: Modifier = Modifier,
    // Given, the surface is drawn on its own layer at this opacity (read in draw), the
    // icon staying put: for a neutral button melting into the merged capsule.
    surfaceAlpha: (() -> Float)? = null,
    content: @Composable (color: Color, pop: () -> Float) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressedScale else 1f,
        animationSpec = PressSpring,
        label = "roundButtonPress",
    )
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // The tap's bounce, on top of the press: 1 at rest, swinging either side after a tap.
    val bounce = remember { Animatable(1f) }
    // The icon's, 0 at rest: it swells as the button squeezes in.
    val iconBounce = remember { Animatable(0f) }
    Box(
        modifier = modifier
            .size(ListActionButtonSize)
            .graphicsLayer {
                val s = scale * bounce.value
                scaleX = s
                scaleY = s
            }
            .then(if (surfaceAlpha == null) surface else Modifier)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch { bounce.animateTo(1f, BounceSpring, initialVelocity = -TapKick) }
                scope.launch { iconBounce.animateTo(0f, BounceSpring, initialVelocity = IconKick) }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (surfaceAlpha != null) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = surfaceAlpha() }.then(surface))
        }
        content(contentColor) { iconBounce.value }
    }
}

@Composable
internal fun ButtonIcon(
    icon: ImageVector,
    loading: Boolean,
    color: Color,
    contentDescription: String,
    pop: () -> Float,
    size: Dp = 24.dp,
) {
    if (loading) {
        CircularProgressIndicator(color = color, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
    } else {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val s = (1f + 0.3f * pop()).coerceAtLeast(0.6f)
                    scaleX = s
                    scaleY = s
                },
        )
    }
}
