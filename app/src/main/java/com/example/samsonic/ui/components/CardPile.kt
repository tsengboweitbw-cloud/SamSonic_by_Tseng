package com.example.samsonic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

// Each card further back in a pile sits this much lower and this much smaller,
// so its bottom edge peeks out below the one in front, as the Now Bar's do.
val PileStep = 7.dp
internal const val PileShrink = 0.07f

// Swiped up, the front card follows the finger; let go at least this high, it goes on
// to the back of the pile, the next coming forward; any lower, it drops back into place.
internal val PickThreshold = 44.dp
// Beyond the pile's top the finger drags it on at this share of its travel.
internal const val PickOverdrag = 0.35f
// Going to the back: no overshoot, which would carry the next card past the front.
private val ToBackSpring = spring<Float>(dampingRatio = 1f, stiffness = 380f)
// Dropping back into place: a little bounce as it lands.
private val DropSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 600f)
// Let go mid hand-off: the lift left eases away with the opening's settle, no overshoot.
private val HandOffSpring = spring<Float>(dampingRatio = 1f, stiffness = 700f)

// Held up off the pile, a card grows this much by the threshold, as a card picked
// up does; on its way to the back it rises to this far (in its own heights) above the
// front card's place, clear of the pile, before going down behind it.
internal const val PickScale = 1.04f
internal const val PickLift = 0.9f

// The share of its trip to the back a card spends rising to the top, in front of the
// pile; the rest it spends behind, settling into the back place.
const val ToTopShare = 0.3f

/**
 * A pile of [count] same-sized cards, stacked like the lock screen's Now Brief: the
 * front one full size, the rest peeking out below it. A swipe up ([pileSwipe]) picks
 * the front card up and it follows the finger; let go high enough it goes to the back
 * and the next comes forward, one card a swipe; lower, it drops back. Now Playing's
 * capsules are one; the stacked nav bar and mini player another.
 */
@Stable
class CardPileState(val count: Int, private val scope: CoroutineScope) {
    /**
     * Which card is in front: whole numbers at rest, in between only while one goes to
     * the back. Counts on past the ends, the pile going round and round.
     */
    val position = Animatable(0f)

    /** How high the front card is held up by the finger, px. */
    val lift = Animatable(0f)

    /** How high it was let go, which its trip to the back sets off from, px. */
    var liftFrom by mutableFloatStateOf(0f)
        private set

    val front: Int get() = floorMod(position.value.roundToInt(), count)

    /** The one card that can be lifted over what's around it: the front one, or the one on its way from there to the back. */
    val liftable: Int get() = floorMod(floor(position.value).toInt(), count)

    /**
     * Where card [index] is in the pile: 0 in front, 1, 2... further back, and between
     * -1 and 0 on its way from the front to the back (see [pose]).
     */
    fun depth(index: Int): Float {
        val depth = floorModFloat(index - position.value + 1f, count.toFloat()) - 1f
        // All the way to the back and at rest, it's in the back place: drawn just the same,
        // but counted as that place, so a pile of two knows its back card is next in line
        // (and shows its contents as the front one is lifted off it).
        return if (depth == -1f) (count - 1).toFloat() else depth
    }

    /** How it's drawn, for cards [heightPx] high and [stepPx] apart; [thresholdPx] is [PickThreshold]. */
    fun pose(index: Int, heightPx: Float, stepPx: Float, thresholdPx: Float): CardPose =
        cardPose(depth(index), (count - 1).toFloat(), heightPx, stepPx, max(lift.value, handOffLift), liftFrom, thresholdPx)

    /** How it stacks: in front highest, and one going to the back behind the pile from the top of its lift. */
    fun zIndex(index: Int): Float {
        val depth = depth(index)
        return if (depth < -ToTopShare) -count.toFloat() else -depth
    }

    /** Brings card [index] to the front, those before it going to the back one by one as a swipe sends them. */
    fun bringToFront(index: Int) {
        scope.launch {
            position.snapTo(position.targetValue)
            lift.snapTo(0f)
            val steps = floorMod(index - front, count)
            if (steps == 0) return@launch
            liftFrom = 0f
            position.animateTo(position.value + steps, ToBackSpring)
        }
    }

    /** Puts card [index] in front at once. */
    fun snapTo(index: Int) {
        scope.launch {
            position.snapTo(index.toFloat())
            lift.snapTo(0f)
        }
    }

    internal fun finishMove() {
        handOffEase?.cancel()
        handOffLift = 0f
        scope.launch {
            position.snapTo(position.targetValue)
            lift.snapTo(0f)
        }
    }

    internal fun hold(px: Float) {
        scope.launch { lift.snapTo(px) }
    }

    /**
     * The lift of a card handed over to what it opens into (see [pileSwipe]'s liftDrag),
     * px: set straight from the finger, frame by frame, as it converts into that opening,
     * and eased away once let go. Plain state rather than [lift], whose writes suspend
     * and would land a frame after the opening they have to keep step with.
     */
    internal var handOffLift by mutableFloatStateOf(0f)

    private var handOffEase: Job? = null

    /** Takes over from [lift] at [px]: the card stays where it's held. */
    internal fun beginHandOff(px: Float) {
        handOffEase?.cancel()
        handOffLift = px
        scope.launch { lift.snapTo(0f) }
    }

    /** Let go mid hand-off: what's left of the lift eases away as the opening settles. */
    internal fun endHandOff() {
        val from = handOffLift
        if (from == 0f) return
        handOffEase = scope.launch {
            animate(from, 0f, animationSpec = HandOffSpring) { value, _ -> handOffLift = value }
        }
    }

    internal fun release(px: Float, thresholdPx: Float) {
        scope.launch {
            if (px >= thresholdPx) {
                // Off to the back, setting off from where it was let go. Nudged onto its way
                // there before the lift is dropped: each snap can take a frame, and one drawn
                // in front with no lift flashes it back into place.
                liftFrom = px
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

@Composable
fun rememberCardPileState(count: Int): CardPileState {
    val scope = rememberCoroutineScope()
    return remember(count) { CardPileState(count, scope) }
}

/** A drag a pile hands to something else: one setting off downward, or a lift carried on past [LiftHandOff]. */
interface PileDrag {
    fun start()
    fun drag(deltaPx: Float)
    fun end(velocityPx: Float)
}

// Lifted this far (the finger's own travel: just clear of the pile, past where letting go
// sends the card to the back), a card with somewhere to go on to hands the drag over to
// it; over the next [LiftConversion] of travel, its lift turns into that opening.

/**
 * Card [index] of [pile], drawn in its place in the pile (cards [cardHeight] high, fully
 * rounded): sized, raised and faded, and cut away where the card just in front of it
 * covers it, so through translucent glass only its peeking edge shows. [cut] is how much
 * of it is cut away there, 0 (drawn whole, as while the one in front is away) to 1, in
 * between fading, so the cut comes and goes with that card; [alpha] overrides how clearly it shows,
 * from its pose, and [squeeze] shrinks it a little more. With [ignoreTouchesBehind],
 * touches on it do nothing unless it's in front.
 *
 * [weight] eases the whole pile in and out of its poses (0 draws every card as it is,
 * 1 in the pile), for cards that come together into a pile from apart; then
 * [offsetFromFront] is how far (px, up) this card's own place is above that of the
 * card in front, and [frontScale] how much that card is scaled on top of its pose, so the
 * cut still follows it.
 */
@Composable
fun Modifier.pileCard(
    pile: CardPileState,
    index: Int,
    cardHeight: Dp,
    cut: () -> Float = { 1f },
    alpha: ((CardPose) -> Float)? = null,
    squeeze: () -> Float = { 1f },
    ignoreTouchesBehind: Boolean = false,
    weight: () -> Float = { 1f },
    offsetFromFront: () -> Float = { 0f },
    frontScale: () -> Float = { 1f },
): Modifier {
    val cutout = remember { Path() }
    // A card further back is shaded toward the page rather than faded: faded, the page's
    // own sharp content bled through its glass (whose blur hides it with an opaque base).
    val shadeColor = MaterialTheme.colorScheme.background
    fun Density.clarity(heightPx: Float): Float {
        val pose = pile.pose(index, heightPx, PileStep.toPx(), PickThreshold.toPx()).weighted(weight())
        return alpha?.invoke(pose) ?: pose.alpha
    }
    val posed = this
        .graphicsLayer {
            val pose = pile.pose(index, cardHeight.toPx(), PileStep.toPx(), PickThreshold.toPx()).weighted(weight())
            scaleX = pose.scale * squeeze()
            scaleY = pose.scale * squeeze()
            translationY = pose.rise
            val clear = clarity(cardHeight.toPx())
            this.alpha = layerAlpha(clear)
            // The shade is painted over just what's drawn, which needs a layer of its own.
            compositingStrategy = if (shade(clear) > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        .drawWithContent {
            drawPiled(pile, index, cut, weight, frontScale, offsetFromFront, cutout)
            val shade = shade(clarity(size.height))
            if (shade > 0f) drawRect(shadeColor, alpha = shade, blendMode = BlendMode.SrcAtop)
        }
    if (!ignoreTouchesBehind) return posed
    return posed.pointerInput(pile, index) {
            // Behind, its peeking edge takes no taps or drags meant for it.
            awaitEachGesture {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (pile.front != index || pile.position.isRunning) event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
}
