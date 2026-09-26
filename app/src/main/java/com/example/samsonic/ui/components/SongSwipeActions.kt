package com.example.samsonic.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.model.Album
import com.example.samsonic.model.Song
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRow
import com.example.samsonic.ui.theme.glassSurface
import com.example.samsonic.ui.theme.outerShadow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private val SwipeThreshold = 88.dp
/**
 * Until the swipe passes its threshold the row is held back by its neighbors (Android 16's
 * magnetic swipe), moving this fraction of the finger's travel; past it, it breaks free and
 * springs up to the finger.
 */
private const val AttachedFollow = 0.75f
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
        swipeRight = SwipeAction(stringResource(R.string.components_play_next), Icons.Filled.SkipNext) { player.playNext(song) },
        swipeLeft = SwipeAction(stringResource(R.string.components_add_to_queue), Icons.AutoMirrored.Filled.PlaylistAdd) { player.addToQueue(listOf(song)) },
        modifier = modifier,
        content = content,
    )
}

/**
 * [SongSwipeActions] for an album's list row: its songs, in album order, play next
 * or go to the end of the queue, once fetched.
 */
@Composable
fun AlbumSwipeActions(album: Album, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val player = LocalPlayerState.current
    val container = LocalAppContainer.current
    val load: suspend () -> List<Song> = { container.repository.getAlbum(album.id).second }
    SwipeActions(
        swipeRight = SwipeAction(stringResource(R.string.components_play_next), Icons.Filled.SkipNext) { player.queueLater(next = true, load) },
        swipeLeft = SwipeAction(stringResource(R.string.components_add_to_queue), Icons.AutoMirrored.Filled.PlaylistAdd) { player.queueLater(next = false, load) },
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
    // For the magnetic pull on neighboring rows (see SwipeNeighbors): this row's identity, and
    // its on-screen center and height in plain holders, so scrolling doesn't recompose.
    val rowToken = remember { Any() }
    val center = remember { floatArrayOf(Float.NaN, 0f) }
    val rowCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // Where the row's center is on screen now, from its coordinates as last placed.
    val rowCenterY = remember {
        { rowCoordinates[0]?.takeIf { it.isAttached }?.let { it.positionInRoot().y + it.size.height / 2f } ?: Float.NaN }
    }
    // Set when a release fires an action: the neighbors stay let go through the spring-back.
    val releasedArmed = remember { booleanArrayOf(false) }
    DisposableEffect(rowToken) { onDispose { SwipeNeighbors.end(rowToken) } }

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
    // How much of the finger's travel the row follows: held back until the threshold, then
    // springing free with a small overshoot. Only while the finger is down: after release the
    // spring-back carries the row home at whatever ratio it had, without a hitch midway.
    var dragging by remember { mutableStateOf(false) }
    val follow = remember { Animatable(AttachedFollow) }
    LaunchedEffect(armed, dragging) {
        if (!dragging) return@LaunchedEffect
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        val spec = if (armed) spring<Float>(dampingRatio = 0.5f, stiffness = 600f) else spring(dampingRatio = 0.85f, stiffness = 500f)
        follow.animateTo(if (armed) 1f else AttachedFollow, spec)
    }
    val visual = if (!dismissX.isNaN()) dismissX else dragX * follow.value

    val dragState = rememberDraggableState { delta ->
        dragX = (dragX + delta).coerceIn(-thresholdPx * 3, thresholdPx * 3)
        // Not once the spring-back is home: its overshoot would flip the panel mid-fade.
        if (dragX != 0f && !returned) towardEnd = dragX < 0
        SwipeNeighbors.update(rowToken, dragX, detached = releasedArmed[0] || abs(dragX) >= thresholdPx)
    }
    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width }
            // Only kept as it's placed; where the row is gets worked out when a swipe starts,
            // not on every frame the list scrolls.
            .onPlaced { rowCoordinates[0] = it }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(left.label) { left.onSwipe(); true },
                    CustomAccessibilityAction(right.label) { right.onSwipe(); true },
                )
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    dragging = true
                    follow.snapTo(if (abs(dragX) >= thresholdPx) 1f else AttachedFollow)
                    returned = false
                    releasedArmed[0] = false
                    center[0] = rowCenterY()
                    center[1] = rowCoordinates[0]?.takeIf { it.isAttached }?.size?.height?.toFloat() ?: 0f
                    SwipeNeighbors.begin(rowToken, centerY = center[0], height = center[1])
                },
                onDragStopped = {
                    dragging = false
                    val action = if (abs(dragX) < thresholdPx) null else if (dragX < 0) left else right
                    if (action != null) {
                        releasedArmed[0] = true
                        SwipeNeighbors.update(rowToken, dragX, detached = true)
                    }
                    if (action?.destructive == true) {
                        animate(visual, sign(dragX) * widthPx, animationSpec = tween(180)) { value, _ -> dismissX = value }
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        action.onSwipe()
                        // Normally the row is gone by now (a list's exit animation can still show it
                        // for a moment, so stay off screen until then); if it's still here, come back.
                        delay(DismissResetDelayMillis)
                        dragX = 0f
                        dismissX = Float.NaN
                        SwipeNeighbors.end(rowToken)
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
                    SwipeNeighbors.end(rowToken)
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
        val pull = rememberNeighborPull(rowToken, rowCenterY)
        Box(
            Modifier
                // Its own swipe, plus any magnetic pull while a neighboring row is swiped.
                .offset { IntOffset((visual + pull()).roundToInt(), 0) }
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
 *
 * Its shadow is only drawn outside the card: an elevation shadow would also lie under
 * the fading glass and show through it as a light bar across the row.
 */
@Composable
private fun LiftedCard(lift: Float, panelHaze: HazeState, modifier: Modifier) {
    Box(
        modifier
            .outerShadow(
                elevation = 10.dp * lift,
                shape = CardShape,
                color = Color.Black.copy(alpha = 0.2f * lift),
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
