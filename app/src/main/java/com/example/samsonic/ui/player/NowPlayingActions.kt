package com.example.samsonic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.example.samsonic.R
import com.example.samsonic.model.Song
import com.example.samsonic.ui.components.pressClickable
import com.example.samsonic.ui.library.AddToPlaylistState
import com.example.samsonic.ui.library.toPlaylistItems
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

/** Each panel's button icon, which the panel also carries as it grows out of the button. */
internal object PanelIcons {
    val Lyrics = Icons.Filled.Lyrics
    val Queue = Icons.AutoMirrored.Filled.QueueMusic
    val Info = Icons.Outlined.Info
}

/** One capsule of the stack: what it shows, and the panel (or card) it opens. */
private class StackCapsule(
    val icon: ImageVector,
    val label: String,
    /** How far its panel is open, and its close bounce ([PanelState.landing]). */
    val progress: () -> Float,
    val landing: () -> Float,
    /** Opens it, growing out of the capsule at [from] (in root coordinates). */
    val open: (from: Rect) -> Unit,
)

private val CapsuleWidth = 220.dp
private val CapsuleHeight = 52.dp

// Each capsule further back in the stack sits this much higher and this much smaller,
// so its top edge peeks out above the one in front.
private val StackStep = 7.dp
private const val StackShrink = 0.07f

// Swiped up, the front capsule follows the finger; let go at least this high, it goes on
// to the back of the pile, the next coming forward; any lower, it drops back into place.
private val PickThreshold = 44.dp
// Beyond the pile's top the finger drags it on at this share of its travel.
private const val PickOverdrag = 0.35f
// Going to the back: no overshoot, which would carry the next capsule past the front.
private val ToBackSpring = spring<Float>(dampingRatio = 1f, stiffness = 380f)
// Dropping back into place: a little bounce as it lands.
private val DropSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 600f)

// How much a capsule shrinks per unit of its panel's close overshoot (a few % at most).
private const val LandingSqueeze = 3f

/**
 * Now Playing's bottom capsules, [icon] and text, stacked like the lock screen's
 * Now Brief: the front one is full size, the rest peek out behind it, a pile of cards.
 * A swipe up picks the front capsule up, and it follows the finger while the rest of
 * the pile stays; let go high enough ([PickThreshold]) it goes to the bottom of the
 * pile and the next comes forward (see [capsulePose]), one capsule a swipe; lower, it
 * drops back. A drag down is left to Now Playing. A tap opens the front one: lyrics,
 * the queue, song info, or Add to playlist ([addToPlaylist], if there are playlists to
 * add to), each growing out of the front capsule as the rest of the pile stays.
 */
@Composable
internal fun NowPlayingActions(
    lyrics: PanelState,
    queue: PanelState,
    info: PanelState,
    addToPlaylist: AddToPlaylistState?,
    song: Song,
    haze: HazeState?,
    modifier: Modifier = Modifier,
) {
    val lyricsLabel = stringResource(R.string.player_lyrics)
    val queueLabel = stringResource(R.string.player_queue)
    val infoLabel = stringResource(R.string.player_song_info)
    val addLabel = stringResource(R.string.components_add_to_playlist)
    val currentSong by rememberUpdatedState(song)
    val capsules = remember(lyrics, queue, info, addToPlaylist, lyricsLabel, queueLabel, infoLabel, addLabel) {
        fun PanelState.capsule(icon: ImageVector, label: String) =
            StackCapsule(icon, label, { progress }, { landing }) { open() }
        listOfNotNull(
            lyrics.capsule(PanelIcons.Lyrics, lyricsLabel),
            queue.capsule(PanelIcons.Queue, queueLabel),
            info.capsule(PanelIcons.Info, infoLabel),
            addToPlaylist?.let { state ->
                StackCapsule(Icons.Filled.LibraryAdd, addLabel, { state.panel.progress }, { state.panel.landing }) { from ->
                    state.open(currentSong.toPlaylistItems(), from, originRadius = null)
                }
            },
        )
    }
    val count = capsules.size
    // Which capsule is in front: whole numbers at rest, in between only while one goes to
    // the back. Counts on past the ends, the stack going round and round.
    val position = remember { Animatable(0f) }
    val front = floorMod(position.value.roundToInt(), count)
    // The one capsule that can be lifted over the controls, the front one or the one on its
    // way from there to the back, is the only one whose glass blurs; the rest show only an
    // edge, over art already blurred, where a blur of their own would cost without showing.
    val liftable by remember(count) { derivedStateOf { floorMod(floor(position.value).toInt(), count) } }
    // How high the front capsule is held up by the finger, px; and how high it was let go,
    // which its trip to the back sets off from.
    val lift = remember { Animatable(0f) }
    var liftFrom by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val bounds = remember { arrayOf(Rect.Zero) }
    val thresholdPx = with(LocalDensity.current) { PickThreshold.toPx() }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                // Room above for the capsules peeking out behind.
                .padding(top = StackStep * (count - 1))
                .size(CapsuleWidth, CapsuleHeight)
                .onGloballyPositioned { coordinates ->
                    // Every panel grows out of the stack.
                    val at = coordinates.boundsInRoot()
                    bounds[0] = at
                    lyrics.origin = at
                    queue.origin = at
                    info.origin = at
                }
                // A swipe up picks the front capsule up. Only up: a drag that sets off
                // downward is left alone, for Now Playing to close.
                .pointerInput(count) {
                    // Past the pile's top the finger drags the capsule on more slowly.
                    val topPx = CapsuleHeight.toPx() * PickLift - stackRise((count - 1).toFloat(), StackStep.toPx())
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var over = 0f
                        val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, amount ->
                            if (amount < 0f) {
                                change.consume()
                                over = amount
                            }
                        } ?: return@awaitEachGesture
                        // One capsule at a time: one still on its way to the back gets there now.
                        scope.launch {
                            position.snapTo(position.targetValue)
                            lift.snapTo(0f)
                        }
                        // How far up the finger has gone since it took hold, px; coming back
                        // down stops where it started.
                        var raised = -over
                        var armed = false
                        fun held(): Float = if (raised <= topPx) raised else topPx + (raised - topPx) * PickOverdrag
                        fun follow() {
                            val now = held() >= thresholdPx
                            if (now && !armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            armed = now
                            val to = held()
                            scope.launch { lift.snapTo(to) }
                        }
                        follow()
                        verticalDrag(drag.id) { change ->
                            raised = (raised - change.positionChange().y).coerceAtLeast(0f)
                            change.consume()
                            follow()
                        }
                        val height = held()
                        scope.launch {
                            if (height >= thresholdPx) {
                                // Off to the back, setting off from where it was let go. Nudged
                                // onto its way there before the lift is dropped: each snap can
                                // take a frame, and one drawn in front with no lift flashes it
                                // back into place.
                                liftFrom = height
                                val target = position.value + 1f
                                position.snapTo(position.value + 0.001f)
                                lift.snapTo(0f)
                                position.animateTo(target, ToBackSpring)
                            } else {
                                lift.animateTo(0f, DropSpring)
                            }
                        }
                    }
                }
                .pressClickable(onClick = { capsules[front].open(bounds[0]) }, pressedScale = 0.96f),
        ) {
            // Whether each capsule's panel is out, changing only as it sets out and as it's
            // back: read instead of how far open it is, so the stack doesn't redraw every
            // frame of the panel growing, which, blurring Now Playing, would redo its blur.
            val open = remember(capsules) { capsules.map { derivedStateOf { it.progress() > 0f } } }
            capsules.forEachIndexed { index, capsule ->
                // Where this capsule is in the stack: 0 in front, 1, 2... further back, and
                // between -1 and 0 on its way from the front to the back (see capsulePose).
                val depth = floorModFloat(index - position.value + 1f, count.toFloat()) - 1f
                // On its way to the back, it goes behind the pile at the top of its lift.
                val z = if (depth < -ToTopShare) -count.toFloat() else -depth
                StackedCapsule(
                    capsule = capsule,
                    depth = { floorModFloat(index - position.value + 1f, count.toFloat()) - 1f },
                    // The one just in front of it, which it hides behind.
                    depthInFront = { floorModFloat(index - 1 - position.value + 1f, count.toFloat()) - 1f },
                    isOpen = { open[index].value },
                    inFrontOpen = { open[floorMod(index - 1, count)].value },
                    backDepth = (count - 1).toFloat(),
                    lift = { lift.value },
                    liftFrom = { liftFrom },
                    thresholdPx = thresholdPx,
                    haze = haze.takeIf { index == liftable },
                    modifier = Modifier.zIndex(z),
                )
            }
        }
    }
}

/**
 * A capsule of the stack at [depth] (see [NowPlayingActions]; its pose, [capsulePose]),
 * its contents only on the front one. Where the capsule just in front of it
 * ([depthInFront]) covers it, it's cut away, so through the translucent glass only
 * its peeking edge shows, not its outline.
 */
@Composable
private fun StackedCapsule(
    capsule: StackCapsule,
    depth: () -> Float,
    depthInFront: () -> Float,
    // Whether its own panel is out, and that of the one in front: while that one's is
    // (and so that one hidden), this one shows whole, with its contents.
    isOpen: () -> Boolean,
    inFrontOpen: () -> Boolean,
    backDepth: Float,
    lift: () -> Float,
    liftFrom: () -> Float,
    thresholdPx: Float,
    haze: HazeState?,
    modifier: Modifier = Modifier,
) {
    val cutout = remember { Path() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val pose = capsulePose(depth(), backDepth, size.height, StackStep.toPx(), lift(), liftFrom(), thresholdPx)
                // Catching its panel's close bounce with a small squeeze; the rest of the
                // pile stays still.
                val squeeze = 1f - capsule.landing() * LandingSqueeze
                scaleX = pose.scale * squeeze
                scaleY = pose.scale * squeeze
                translationY = pose.rise
                // Hidden while its panel is out, which starts as a copy of it; the rest of
                // the pile stays in place behind, the next one, left in front, as clear as
                // a front one. Each switch is made under the panel's copy of the capsule.
                alpha = when {
                    isOpen() -> 0f
                    depth() == 1f && inFrontOpen() -> 1f
                    else -> pose.alpha
                }
            }
            .drawWithContent {
                val d = depth()
                val inFront = depthInFront()
                // Cut away only where the one in front is drawn over this one: not the front
                // one itself, nor one on its way up over the top, nor by one gone behind.
                val underIt = (d > 0f || d < -ToTopShare) && inFront >= -ToTopShare && !inFrontOpen()
                if (!underIt) {
                    drawContent()
                    return@drawWithContent
                }
                // The capsule in front, brought into this one's own (scaled, raised) frame.
                val step = StackStep.toPx()
                val own = capsulePose(d, backDepth, size.height, step, lift(), liftFrom(), thresholdPx)
                val front = capsulePose(inFront, backDepth, size.height, step, lift(), liftFrom(), thresholdPx)
                val ratio = front.scale / own.scale
                val halfW = size.width / 2 * ratio
                val halfH = size.height / 2 * ratio
                val centreY = size.height / 2 + (front.rise - own.rise) / own.scale
                cutout.reset()
                cutout.addRoundRect(
                    RoundRect(
                        left = size.width / 2 - halfW,
                        top = centreY - halfH,
                        right = size.width / 2 + halfW,
                        bottom = centreY + halfH,
                        cornerRadius = CornerRadius(halfH),
                    ),
                )
                clipPath(cutout, ClipOp.Difference) { this@drawWithContent.drawContent() }
            }
            .nowPlayingGlass(haze),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.graphicsLayer {
                val d = depth()
                alpha = when {
                    // On its way up; gone as it passes behind the pile.
                    d < 0f -> 1f - ramp(-d, ToTopShare, ToTopShare + 0.2f)
                    // Next in line: showing as the front one is lifted off it, and staying
                    // shown as it comes forward once that one is let go high enough.
                    // ...or as the front one's panel opens out of it.
                    d == 1f -> if (inFrontOpen()) 1f else ramp(lift(), 0f, thresholdPx)
                    d > 0f && d < 1f -> maxOf(1f - d, ramp(liftFrom(), 0f, thresholdPx))
                    // In front; the rest of the pile shows none.
                    else -> (1f - d).coerceIn(0f, 1f)
                }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(capsule.icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = capsule.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

// Held up off the pile, a capsule grows this much by the threshold, as a card picked
// up does; on its way to the back it rises to this far (in its own heights) above the
// pile's back edge before going behind.
private const val PickScale = 1.04f
private const val PickLift = 0.9f

// The share of its trip to the back a capsule spends rising to the top, in front of the
// pile; the rest it spends behind, settling into the back place.
private const val ToTopShare = 0.3f

/** How a capsule is drawn: its size, how far it's raised (px, negative = up) and how clearly it shows. */
private class CapsulePose(val scale: Float, val rise: Float, val alpha: Float)

/**
 * The pose of a capsule [depth] into the stack of [heightPx]-high capsules, [stepPx]
 * apart. In the pile (0 and on) each further back is smaller and higher, the front one
 * held up [lift] px by the finger, growing toward [PickScale] as it nears the threshold
 * ([thresholdPx]). On its way to the back (between 0 and -1), set off from [liftFrom]
 * px up, it's a card moved from the top of a pile to the bottom: it carries on up to
 * clear the pile, whole, then goes behind and settles into the back place
 * ([backDepth]), taking on the look of the capsule there only as it arrives.
 */
private fun capsulePose(
    depth: Float,
    backDepth: Float,
    heightPx: Float,
    stepPx: Float,
    lift: Float,
    liftFrom: Float,
    thresholdPx: Float,
): CapsulePose {
    fun held(up: Float) = lerp(1f, PickScale, ramp(up, 0f, thresholdPx))
    if (depth == 0f) return CapsulePose(held(lift), -lift, 1f)
    if (depth > 0f) return CapsulePose(stackScale(depth), stackRise(depth, stepPx), stackAlpha(depth))
    val t = -depth
    val back = CapsulePose(stackScale(backDepth), stackRise(backDepth, stepPx), stackAlpha(backDepth))
    val top = minOf(back.rise - heightPx * PickLift, -liftFrom)
    return if (t < ToTopShare) {
        val u = t / ToTopShare
        val eased = 1f - (1f - u) * (1f - u)
        CapsulePose(lerp(held(liftFrom), PickScale, eased), lerp(-liftFrom, top, eased), 1f)
    } else {
        val u = (t - ToTopShare) / (1f - ToTopShare)
        val eased = u * u * (3 - 2 * u)
        CapsulePose(lerp(PickScale, back.scale, eased), lerp(top, back.rise, eased), lerp(1f, back.alpha, eased * eased))
    }
}

/** Maps [value] from [start]..[end] onto 0..1, clamped. */
private fun ramp(value: Float, start: Float, end: Float) = ((value - start) / (end - start)).coerceIn(0f, 1f)

/** How clearly a capsule [depth] back in the stack shows: fainter further back, but all of the pile shows. */
private fun stackAlpha(depth: Float): Float = (1f - 0.25f * depth).coerceIn(0f, 1f)

/** How big a capsule [depth] back in the stack is drawn. */
private fun stackScale(depth: Float): Float = 1f - StackShrink * depth.coerceAtLeast(0f)

/** How far (px, negative = up) a capsule [depth] back in the stack is raised, by [step] per place. */
private fun stackRise(depth: Float, step: Float): Float = -step * depth.coerceAtLeast(0f)

private fun floorMod(value: Int, count: Int): Int = ((value % count) + count) % count

private fun floorModFloat(value: Float, count: Float): Float = ((value % count) + count) % count
