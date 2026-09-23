package com.example.samsonic.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.samsonic.model.Song
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.theme.OneUiRadius
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private val SwipeThreshold = 88.dp
/** Past the threshold the row keeps following the finger, but only at this fraction of its speed. */
private const val OverDragResistance = 0.3f
private const val DismissResetDelayMillis = 500L

/**
 * What a swipe does. A [destructive] action (say, removing the row) is armed in the error
 * color and slides the row all the way off screen before [onSwipe] runs.
 */
class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onSwipe: () -> Unit,
)

/**
 * The browsing swipe actions for a song row: swipe right to play the song next, swipe
 * left to add it to the end of the queue.
 */
@Composable
fun SongSwipeActions(song: Song, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val player = LocalPlayerState.current
    SwipeActions(
        swipeRight = SwipeAction("Play next", Icons.Filled.SkipNext) { player.playNext(song) },
        swipeLeft = SwipeAction("Add to queue", Icons.AutoMirrored.Filled.PlaylistAdd) { player.addToQueue(listOf(song)) },
        modifier = modifier,
        content = content,
    )
}

/**
 * Horizontal swipe actions for a list row. The revealed side grows into an accent
 * pill with a haptic tick once the swipe passes the threshold; letting go there
 * performs the action and the row springs back into place either way.
 */
@Composable
fun SwipeActions(
    swipeLeft: SwipeAction,
    swipeRight: SwipeAction,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val thresholdPx = with(LocalDensity.current) { SwipeThreshold.toPx() }
    val left by rememberUpdatedState(swipeLeft)
    val right by rememberUpdatedState(swipeRight)
    var widthPx by remember { mutableIntStateOf(0) }

    // Raw finger travel; the row itself is drawn at [visual], which damps travel past the threshold.
    var dragX by remember { mutableFloatStateOf(0f) }
    // Set while a destructive action slides the row off screen, which bypasses the damping.
    var dismissX by remember { mutableFloatStateOf(Float.NaN) }
    val armed = abs(dragX) >= thresholdPx
    val visual = when {
        !dismissX.isNaN() -> dismissX
        armed -> sign(dragX) * (thresholdPx + (abs(dragX) - thresholdPx) * OverDragResistance)
        else -> dragX
    }
    LaunchedEffect(armed) {
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    val dragState = rememberDraggableState { delta ->
        dragX = (dragX + delta).coerceIn(-thresholdPx * 3, thresholdPx * 3)
    }
    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(left.label) { left.onSwipe(); true },
                    CustomAccessibilityAction(right.label) { right.onSwipe(); true },
                )
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    val action = if (abs(dragX) < thresholdPx) null else if (dragX < 0) left else right
                    if (action?.destructive == true) {
                        animate(visual, sign(dragX) * widthPx, animationSpec = tween(180)) { value, _ -> dismissX = value }
                        action.onSwipe()
                        // Normally the row is gone by now (a list's exit animation can still show it
                        // for a moment, so stay off screen until then); if it's still here, come back.
                        delay(DismissResetDelayMillis)
                        dragX = 0f
                        dismissX = Float.NaN
                        return@draggable
                    }
                    action?.onSwipe?.invoke()
                    // Settle through the draggable state so a new drag interrupts the spring-back.
                    dragState.drag {
                        animate(dragX, 0f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f)) { value, _ ->
                            dragBy(value - dragX)
                        }
                    }
                },
            ),
    ) {
        if (visual != 0f) {
            val action = if (visual < 0) left else right
            SwipeReveal(
                action = action,
                widthPx = abs(visual),
                progress = (abs(dragX) / thresholdPx).coerceAtMost(1f),
                armed = armed,
                alignEnd = visual < 0,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(Modifier.offset { IntOffset(visual.roundToInt(), 0) }) { content() }
    }
}

/** The pill uncovered beside the sliding row, on the side the row moved away from. */
@Composable
private fun SwipeReveal(
    action: SwipeAction,
    widthPx: Float,
    progress: Float,
    armed: Boolean,
    alignEnd: Boolean,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(OneUiRadius.Chip)
    val colors = MaterialTheme.colorScheme
    val color by animateColorAsState(
        targetValue = when {
            !armed -> colors.surfaceContainerHighest
            action.destructive -> colors.error
            else -> colors.primary
        },
        label = "swipeRevealColor",
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            !armed -> colors.onSurfaceVariant
            action.destructive -> colors.onError
            else -> colors.onPrimary
        },
        label = "swipeRevealTint",
    )
    val width = with(LocalDensity.current) { widthPx.toDp() } - 8.dp
    Box(modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        if (width > 0.dp) {
            Box(
                modifier = Modifier
                    .align(if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(width)
                    .clip(shape)
                    .background(color, shape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            val scale = 0.6f + 0.4f * progress
                            scaleX = scale
                            scaleY = scale
                            alpha = progress
                        },
                )
            }
        }
    }
}
