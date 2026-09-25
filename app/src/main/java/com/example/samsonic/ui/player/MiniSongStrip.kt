package com.example.samsonic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.samsonic.model.Song
import com.example.samsonic.playback.LocalPlayerState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

// A swipe this far across (a share of the strip's width), or flung, changes song.
private const val ChangeAt = 0.3f

// Letting go this fast carries on to the song the swipe was heading for.
private val FlingVelocity = 600.dp

// Past the first or last song there's nothing to bring in: the strip gives only this much.
private const val EndResistance = 0.3f

// The soft right edge where a song sliding across meets the buttons.
private val EdgeFade = 16.dp

// Settling on the new song: it swings a little past the middle and back, a bit
// quicker than a plain settle so the new song isn't kept waiting.
private val ChangeSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 700f)

// Springing back, for a swipe that didn't go far enough: a small wobble home.
private val ReturnSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 600f)

/**
 * The mini player's song, shown by [content], which a sideways swipe (with
 * [swipeEnabled]) slides under the finger while the next song (swiping left) or
 * the previous one (right) follows it in from the side, in play order. Let go
 * past a third of the way, or flung, it carries on and that song plays; short of
 * that, it springs back. At the first or last song it only gives a little. A
 * haptic tick marks passing the point of changing song, and another the change.
 */
@Composable
internal fun SongStrip(
    song: Song,
    swipeEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (shown: Song, isCurrent: Boolean) -> Unit,
) {
    val player = LocalPlayerState.current
    // The neighbours of the song shown, found from it rather than from the player's
    // current index: the two change a moment apart as a song starts, and the strip
    // must never line up one song's neighbours around another.
    val at = player.currentIndex.takeIf { player.queue.getOrNull(it)?.id == song.id }
        ?: player.queue.indexOfFirst { it.id == song.id }
    val next = player.playOrderNeighbor(1, from = at)
    val previous = player.playOrderNeighbor(-1, from = at)
    val nextSong = next?.let { player.queue.getOrNull(it) }
    val previousSong = previous?.let { player.queue.getOrNull(it) }
    val latestNext by rememberUpdatedState(next)
    val latestPrevious by rememberUpdatedState(previous)
    val latestSongId by rememberUpdatedState(song.id)

    // How far the strip is slid from [slidSong], in px: negative toward the next song.
    val offset = remember { Animatable(0f) }
    // The song the slide is measured from, and the one a released swipe is landing on
    // ([landing] px along). The swiped-to song starts playing as the finger lets go,
    // so the player can catch up at any point of the landing: once it shows, the slide
    // carries on measured from it, in that same frame, so nothing jumps and the next
    // swipe can start at once. Any other song showing is simply centred.
    var slidSong by remember { mutableStateOf<String?>(null) }
    var slidTo by remember { mutableStateOf<String?>(null) }
    var landing by remember { mutableFloatStateOf(0f) }
    fun shift(): Float = when (latestSongId) {
        slidSong -> offset.value
        slidTo -> offset.value - landing
        else -> 0f
    }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(0) }

    val haptics = LocalHapticFeedback.current
    val swipe = if (!swipeEnabled) Modifier else Modifier.pointerInput(Unit) {
        val tracker = VelocityTracker()
        val flingPx = FlingVelocity.toPx()
        var drag = 0f
        // Past the point where letting go changes song: a tick each time the swipe gets
        // there, as the song rows' swipe actions give.
        var armed = false
        fun settle(velocity: Float) {
            val w = width.toFloat()
            // Past the threshold (unless flung back), or flung that way.
            val towardNext = velocity < -flingPx || (drag < -w * ChangeAt && velocity <= flingPx)
            val towardPrevious = velocity > flingPx || (drag > w * ChangeAt && velocity >= -flingPx)
            val (target, lands) = when {
                towardNext && latestNext != null -> latestNext to -w
                towardPrevious && latestPrevious != null -> latestPrevious to w
                else -> null to 0f
            }
            if (target == null) {
                scope.launch { offset.animateTo(0f, ReturnSpring, initialVelocity = velocity) }
                return
            }
            // Felt, and heard, as the finger lets go: the change is decided then.
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            val leaving = latestSongId
            slidTo = player.queue.getOrNull(target)?.id
            landing = lands
            player.playQueueIndex(target)
            val arriving = slidTo
            scope.launch {
                offset.animateTo(lands, ChangeSpring, initialVelocity = velocity)
                // Landed: once the song is there, measure from it outright, so the slide
                // no longer hangs on the song left (should that come back some other way).
                val arrived = withTimeoutOrNull(1500) { snapshotFlow { latestSongId }.first { it == arriving } }
                if (arrived != null) {
                    slidSong = arrived
                    slidTo = null
                    offset.snapTo(0f)
                } else if (latestSongId == leaving) {
                    // The song didn't change: come back to it.
                    offset.animateTo(0f, ReturnSpring)
                }
            }
        }
        detectHorizontalDragGestures(
            onDragStart = {
                tracker.resetTracking()
                // Picks up the strip wherever it is, even mid-landing, measured afresh
                // from the song showing.
                drag = shift()
                slidSong = latestSongId
                slidTo = null
                armed = false
                val from = drag
                scope.launch { offset.snapTo(from) }
            },
            onDragEnd = { settle(tracker.calculateVelocity().x) },
            onDragCancel = { settle(0f) },
        ) { change, dx ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            val heading = drag + dx
            val hasSong = if (heading < 0f) latestNext != null else latestPrevious != null
            drag += if (hasSong) dx else dx * EndResistance
            val nowArmed = hasSong && abs(drag) >= width * ChangeAt
            if (nowArmed && !armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            armed = nowArmed
            val at = drag
            scope.launch { offset.snapTo(at) }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { width = it.width }
            .clipToBounds()
            // Offscreen, so the edge fade masks the songs alone, not what's behind them.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val moving = abs(shift())
                if (moving > 0.5f) {
                    val fade = EdgeFade.toPx()
                    // Eases in over the first few px, so the edge doesn't pop as a swipe starts.
                    val strength = (moving / fade).coerceAtMost(1f)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Black, Color.Black.copy(alpha = 1f - strength)),
                            startX = size.width - fade,
                            endX = size.width,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
            .then(swipe),
    ) {
        // Each song keyed by itself, so the one swiped in stays the same on screen as it
        // becomes the current song (its cover already there), rather than being drawn
        // afresh in the middle.
        val slots = if (swipeEnabled) {
            listOfNotNull(previousSong?.let { it to -1 }, song to 0, nextSong?.let { it to 1 })
        } else {
            listOf(song to 0)
        }
        slots.forEach { (shown, place) ->
            key(shown.id) {
                Box(Modifier.fillMaxWidth().graphicsLayer { translationX = shift() + place * size.width }) {
                    content(shown, place == 0)
                }
            }
        }
    }
}
