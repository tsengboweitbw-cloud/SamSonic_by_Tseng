package com.example.samsonic.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private val TrackWidth = 46.dp
private val TrackHeight = 26.dp
private val ThumbSize = 20.dp
private val ThumbInset = 3.dp
// How much wider the thumb gets at the middle of its travel.
private val ThumbStretch = 8.dp
private const val PressedThumbScale = 0.85f

/**
 * One UI 9.0 switch: a slim pill track that fills with the accent color when
 * on, and a white thumb with a soft shadow - rather than Material3's outlined
 * track and growing thumb. All the motion lives here, so every switch in the
 * app gets it for free:
 * - the thumb springs across with a little overshoot, stretching into a pill
 *   mid-travel and squashing against the end as it lands;
 * - the track color follows the thumb rather than fading on its own clock;
 * - pressing shrinks the thumb (the app's One UI press shrink), and flipping
 *   gives a toggle haptic.
 *
 * Pass [onCheckedChange] to make the switch its own tap target. Inside a row
 * that takes the tap instead (see SwitchRow), leave it null and share the
 * row's [interactionSource] so pressing anywhere on the row still presses the
 * thumb.
 */
@Composable
fun OneUiSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()

    // 0 = off, 1 = on; the underdamped spring overshoots past either end a little.
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "OneUiSwitchProgress",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) PressedThumbScale else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "OneUiSwitchPress",
    )

    val offColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    // On, the track takes the same accent-to-neighbour fill as the sliders.
    val palette = MaterialTheme.accentPalette
    val travel = progress.coerceIn(0f, 1f)
    // 0 at either end, 1 halfway across.
    val midway = 1f - abs(2f * travel - 1f)
    // How far the spring has overshot past an end, for the landing squash.
    val overshoot = (abs(progress - travel) * 4f).coerceAtMost(0.25f)

    Box(
        modifier = modifier
            .then(
                if (onCheckedChange != null) {
                    Modifier.switchToggleable(checked, source, indication = null, onCheckedChange)
                } else {
                    Modifier
                },
            )
            .size(TrackWidth, TrackHeight)
            .background(
                Brush.horizontalGradient(listOf(lerp(offColor, palette.primary, travel), lerp(offColor, palette.secondary, travel))),
                CircleShape,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                // Width grows mid-travel; x keeps the thumb inside the track at any width.
                .layout { measurable, _ ->
                    val height = ThumbSize.roundToPx()
                    val width = (ThumbSize + ThumbStretch * midway).roundToPx()
                    val inset = ThumbInset.roundToPx()
                    val room = TrackWidth.roundToPx() - inset * 2 - width
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) {
                        placeable.place(inset + (room * travel).roundToInt(), 0)
                    }
                }
                .graphicsLayer {
                    // Squash against the end it lands on: flatter and taller.
                    transformOrigin = TransformOrigin(if (checked) 1f else 0f, 0.5f)
                    scaleX = pressScale * (1f - overshoot)
                    scaleY = pressScale * (1f + overshoot * 0.6f)
                }
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/**
 * The tap behaviour behind every switch: toggles with the Switch role (so
 * TalkBack reads it as one) and plays the toggle haptic. Used by [OneUiSwitch]
 * itself, or by a whole row that owns the tap and shares [interactionSource]
 * with the switch inside it.
 */
@Composable
fun Modifier.switchToggleable(
    checked: Boolean,
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    onCheckedChange: (Boolean) -> Unit,
    // A long press that doesn't toggle, e.g. to show a settings row's hint.
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val toggle = { on: Boolean ->
        haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        onCheckedChange(on)
    }
    if (onLongClick == null) {
        return toggleable(value = checked, interactionSource = interactionSource, indication = indication, role = Role.Switch, onValueChange = toggle)
    }
    // toggleable has no long press; this is the same tap, said to TalkBack as a switch.
    return semantics { toggleableState = ToggleableState(checked) }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = indication,
            role = Role.Switch,
            onLongClick = onLongClick,
            onClick = { toggle(!checked) },
        )
}
