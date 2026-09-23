package com.example.samsonic.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRow
import com.example.samsonic.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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
 * Horizontal swipe actions for a list row, One UI style: the row (inset by [OneUiRow.Inset])
 * lifts into a floating
 * glass card and slides over a rounded panel showing the action, which turns solid with
 * a haptic tick once the swipe passes the threshold. Letting go there performs the
 * action (with a confirming haptic) and the card bounces back into place either way.
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
    val panelHaze = remember { HazeState() }

    // Raw finger travel; the row itself is drawn at [visual], which damps travel past the threshold.
    var dragX by remember { mutableFloatStateOf(0f) }
    // Set while a destructive action slides the row off screen, which bypasses the damping.
    var dismissX by remember { mutableFloatStateOf(Float.NaN) }
    // Set once the spring-back first reaches the row's place, so the lift starts fading there
    // instead of waiting out the last few pixels of the bounce.
    var returned by remember { mutableStateOf(false) }
    // The side last swiped toward, kept so the panel can finish fading after the row is home.
    var towardEnd by remember { mutableStateOf(false) }
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
        // Not once the spring-back is home: its overshoot would flip the panel mid-fade.
        if (dragX != 0f && !returned) towardEnd = dragX < 0
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
                onDragStarted = { returned = false },
                onDragStopped = {
                    val action = if (abs(dragX) < thresholdPx) null else if (dragX < 0) left else right
                    if (action?.destructive == true) {
                        animate(visual, sign(dragX) * widthPx, animationSpec = tween(180)) { value, _ -> dismissX = value }
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        action.onSwipe()
                        // Normally the row is gone by now (a list's exit animation can still show it
                        // for a moment, so stay off screen until then); if it's still here, come back.
                        delay(DismissResetDelayMillis)
                        dragX = 0f
                        dismissX = Float.NaN
                        return@draggable
                    }
                    if (action != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        action.onSwipe()
                    }
                    // Settle through the draggable state so a new drag interrupts the spring-back.
                    val from = dragX
                    dragState.drag {
                        // Stops within a pixel of rest rather than the default hundredth of one,
                        // which left the bounce crawling on long after it looked settled.
                        val spec = spring(dampingRatio = 0.72f, stiffness = 300f, visibilityThreshold = 1f)
                        animate(from, 0f, animationSpec = spec) { value, _ ->
                            if (value == 0f || sign(value) != sign(from)) returned = true
                            dragBy(value - dragX)
                        }
                    }
                    returned = true
                },
            ),
    ) {
        // While it's being swiped, the row lifts off the list as a floating glass card; once it
        // springs back to its place, the card and the panel under it fade out together.
        val lifted = visual != 0f && !returned
        val lift by animateFloatAsState(
            targetValue = if (lifted) 1f else 0f,
            animationSpec = tween(if (lifted) 180 else 360, easing = FastOutSlowInEasing),
            label = "swipeLift",
        )
        if (lift > 0f) {
            SwipeReveal(
                action = if (towardEnd) left else right,
                progress = (abs(dragX) / thresholdPx).coerceAtMost(1f),
                armed = armed,
                alignEnd = towardEnd,
                // The card's backdrop. Its own source, not the NavHost's: rows sit inside that
                // one, and a hazeEffect nested in its own source can draw recursively.
                modifier = Modifier.matchParentSize().graphicsLayer { alpha = lift }.hazeSource(panelHaze),
            )
        }
        Box(
            Modifier
                .offset { IntOffset(visual.roundToInt(), 0) }
                .padding(OneUiRow.Inset),
        ) {
            if (lift > 0f) LiftedCard(lift, panelHaze, Modifier.matchParentSize())
            content()
        }
    }
}

private val CardShape = OneUiRow.Shape

/**
 * Frosted glass behind the lifted row that blurs the action panel beneath it, so the
 * action's color glows through. Faded as one layer: the blur paints an opaque base, so
 * thinning only its tint would leave the card solid until it vanished at once.
 */
@Composable
private fun LiftedCard(lift: Float, panelHaze: HazeState, modifier: Modifier) {
    Box(
        modifier
            .shadow(
                elevation = 10.dp * lift,
                shape = CardShape,
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.3f),
            )
            .graphicsLayer { alpha = lift }
            .glassSurface(
                shape = CardShape,
                hazeState = panelHaze,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = GlassAlpha.Nav,
            ),
    )
}
