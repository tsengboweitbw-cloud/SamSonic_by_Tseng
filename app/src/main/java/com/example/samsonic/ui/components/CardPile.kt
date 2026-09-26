package com.example.samsonic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

// Each card further back in a pile sits this much higher and this much smaller,
// so its top edge peeks out above the one in front.
val PileStep = 7.dp
private const val PileShrink = 0.07f

// Swiped up, the front card follows the finger; let go at least this high, it goes on
// to the back of the pile, the next coming forward; any lower, it drops back into place.
private val PickThreshold = 44.dp
// Beyond the pile's top the finger drags it on at this share of its travel.
private const val PickOverdrag = 0.35f
// Going to the back: no overshoot, which would carry the next card past the front.
private val ToBackSpring = spring<Float>(dampingRatio = 1f, stiffness = 380f)
// Dropping back into place: a little bounce as it lands.
private val DropSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 600f)
// Let go mid hand-off: the lift left eases away with the opening's settle, no overshoot.
private val HandOffSpring = spring<Float>(dampingRatio = 1f, stiffness = 700f)

// Held up off the pile, a card grows this much by the threshold, as a card picked
// up does; on its way to the back it rises to this far (in its own heights) above the
// pile's back edge before going behind.
private const val PickScale = 1.04f
private const val PickLift = 0.9f

// The share of its trip to the back a card spends rising to the top, in front of the
// pile; the rest it spends behind, settling into the back place.
const val ToTopShare = 0.3f

/**
 * A pile of [count] same-sized cards, stacked like the lock screen's Now Brief: the
 * front one full size, the rest peeking out behind it. A swipe up ([pileSwipe]) picks
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
    fun depth(index: Int): Float = floorModFloat(index - position.value + 1f, count.toFloat()) - 1f

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
private val LiftHandOff = 128.dp
private val LiftConversion = 120.dp

/**
 * The swipe up that sends the front card of [pile] (cards [cardHeight] high) to the
 * back: the card follows the finger, past the pile's top more slowly. A drag that sets
 * off downward goes to [downDrag] if given, else is left alone, for what's around.
 * Carried on up past [LiftHandOff], the rest of the drag goes to [liftDrag] if given,
 * from where the card has been lifted to (the mini player opening into Now Playing).
 * Only a gesture that starts while [enabled] says so is taken.
 */
@Composable
fun Modifier.pileSwipe(
    pile: CardPileState,
    cardHeight: Dp,
    downDrag: PileDrag? = null,
    liftDrag: PileDrag? = null,
    enabled: () -> Boolean = { true },
): Modifier {
    val haptics = LocalHapticFeedback.current
    val canStart by rememberUpdatedState(enabled)
    return pointerInput(pile, downDrag, liftDrag, cardHeight) {
        pileGesture(pile, cardHeight, downDrag, liftDrag, { canStart() }, haptics)
    }
}

private suspend fun PointerInputScope.pileGesture(
    pile: CardPileState,
    cardHeight: Dp,
    downDrag: PileDrag?,
    liftDrag: PileDrag?,
    enabled: () -> Boolean,
    haptics: HapticFeedback,
) {
    val thresholdPx = PickThreshold.toPx()
    val handOffPx = LiftHandOff.toPx()
    val conversionPx = LiftConversion.toPx()
    // Past the pile's top the finger drags the card on more slowly.
    val topPx = cardHeight.toPx() * PickLift - stackRise((pile.count - 1).toFloat(), PileStep.toPx())
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!enabled()) return@awaitEachGesture
        var over = 0f
        var downward = false
        val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, amount ->
            if (amount < 0f || downDrag != null) {
                change.consume()
                over = amount
                downward = amount > 0f
            }
        } ?: return@awaitEachGesture
        if (downward && downDrag != null) {
            // From the finger's own steps: the card moves with it, so where the finger is on
            // the card barely changes, and a velocity from that came out near nothing.
            val tracker = FingerVelocity(drag)
            downDrag.start()
            downDrag.drag(over)
            verticalDrag(drag.id) { change ->
                tracker.add(change)
                downDrag.drag(change.positionChange().y)
                change.consume()
            }
            downDrag.end(tracker.velocityY())
            return@awaitEachGesture
        }
        // One card at a time: one still on its way to the back gets there now.
        pile.finishMove()
        // How far up the finger has gone since it took hold, px; coming back down stops
        // where it started.
        var raised = -over
        var armed = false
        fun held(): Float = if (raised <= topPx) raised else topPx + (raised - topPx) * PickOverdrag
        fun follow() {
            val now = held() >= thresholdPx
            if (now && !armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            armed = now
            pile.hold(held())
        }
        follow()
        // From the finger's own steps, as above: what it opens rises with it.
        val tracker = FingerVelocity(drag)
        var handedOff = false
        // Where the finger was, and how high the card, as it was handed over.
        var handOffAt = 0f
        var liftAtHandOff = 0f
        // How far what it's handed to has been dragged open, px.
        var opened = 0f
        verticalDrag(drag.id) { change ->
            tracker.add(change)
            val dy = change.positionChange().y
            change.consume()
            if (handedOff) {
                // The card's lift turns into the opening as the finger carries on up (and
                // back, as it comes down): what opens rises with the finger, and by as much
                // again as the lift comes down, so the card's top stays under the finger
                // while its bottom follows it down into the new shape. Brought back below
                // where it was handed over, the opening is closed and the card follows the
                // finger down as it did before, to drop back into place when let go.
                raised = (raised - dy).coerceAtLeast(0f)
                val beyond = raised - handOffAt
                val lift = if (beyond >= 0f) {
                    liftAtHandOff * (1f - (beyond / conversionPx).coerceIn(0f, 1f))
                } else {
                    held()
                }
                // How far the opening is dragged open: the finger's travel past the hand-off,
                // and the lift turned into it.
                val open = if (beyond >= 0f) beyond + (liftAtHandOff - lift) else 0f
                pile.handOffLift = lift
                liftDrag!!.drag(-(open - opened))
                opened = open
                return@verticalDrag
            }
            raised = (raised - dy).coerceAtLeast(0f)
            if (liftDrag != null && raised >= handOffPx) {
                handedOff = true
                handOffAt = raised
                liftAtHandOff = held()
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                pile.beginHandOff(liftAtHandOff)
                liftDrag.start()
                return@verticalDrag
            }
            follow()
        }
        if (handedOff) {
            pile.endHandOff()
            liftDrag!!.end(tracker.velocityY())
        } else {
            pile.release(held(), thresholdPx)
        }
    }
}

/**
 * A finger's velocity from its own steps added up, not from where it is on the node it
 * touches: that node (a card, and what it opens into) moves with the finger, so the
 * finger's place on it barely changes, and a velocity from that came out near nothing.
 */
private class FingerVelocity(first: PointerInputChange) {
    private val tracker = VelocityTracker()
    private var at = Offset.Zero

    init {
        tracker.addPosition(first.uptimeMillis, at)
    }

    fun add(change: PointerInputChange) {
        at += change.positionChange()
        tracker.addPosition(change.uptimeMillis, at)
    }

    fun velocityY(): Float = tracker.calculateVelocity().y
}

/**
 * Card [index] of [pile], drawn in its place in the pile (cards [cardHeight] high, fully
 * rounded): sized, raised and faded, and cut away where the card just in front of it
 * covers it, so through translucent glass only its peeking edge shows. [whole] draws
 * it uncut, as while the one in front is away; [alpha] overrides how clearly it shows,
 * from its pose, and [squeeze] shrinks it a little more. With [ignoreTouchesBehind],
 * touches on it do nothing unless it's in front.
 *
 * [weight] eases the whole pile in and out of its poses (0 draws every card as it is,
 * 1 in the pile), for cards that come together into a pile from apart; then
 * [offsetFromFront] is how far (px, up) this card's own place is above that of the
 * card in front, so the cut still follows that card.
 */
@Composable
fun Modifier.pileCard(
    pile: CardPileState,
    index: Int,
    cardHeight: Dp,
    whole: () -> Boolean = { false },
    alpha: ((CardPose) -> Float)? = null,
    squeeze: () -> Float = { 1f },
    ignoreTouchesBehind: Boolean = false,
    weight: () -> Float = { 1f },
    offsetFromFront: () -> Float = { 0f },
): Modifier {
    val cutout = remember { Path() }
    val posed = this
        .graphicsLayer {
            val w = weight()
            val pose = pile.pose(index, cardHeight.toPx(), PileStep.toPx(), PickThreshold.toPx()).weighted(w)
            scaleX = pose.scale * squeeze()
            scaleY = pose.scale * squeeze()
            translationY = pose.rise
            this.alpha = alpha?.invoke(pose) ?: pose.alpha
        }
        .drawWithContent {
            val d = pile.depth(index)
            val inFrontIndex = floorMod(index - 1, pile.count)
            val inFront = pile.depth(inFrontIndex)
            // Cut away only where the one in front is drawn over this one: not the front
            // one itself, nor one on its way up over the top, nor by one gone behind.
            val underIt = (d > 0f || d < -ToTopShare) && inFront >= -ToTopShare && !whole()
            if (!underIt) {
                drawContent()
                return@drawWithContent
            }
            val step = PileStep.toPx()
            val threshold = PickThreshold.toPx()
            val w = weight()
            val own = pile.pose(index, size.height, step, threshold).weighted(w)
            val front = pile.pose(inFrontIndex, size.height, step, threshold).weighted(w)
            val ratio = front.scale / own.scale
            val halfW = size.width / 2 * ratio
            val halfH = size.height / 2 * ratio
            val centreY = size.height / 2 + (front.rise - own.rise + offsetFromFront()) / own.scale
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


/** How a card is drawn: its size, how far it's raised (px, negative = up) and how clearly it shows. */
class CardPose(val scale: Float, val rise: Float, val alpha: Float) {
    /** This pose [weight] of the way from none (full size, in place, clear) to itself. */
    fun weighted(weight: Float): CardPose =
        if (weight >= 1f) this else CardPose(lerp(1f, scale, weight), rise * weight, lerp(1f, alpha, weight))
}

/**
 * The pose of a card [depth] into the pile of [heightPx]-high cards, [stepPx]
 * apart. In the pile (0 and on) each further back is smaller and higher, the front one
 * held up [lift] px by the finger, growing toward [PickScale] as it nears the threshold
 * ([thresholdPx]). On its way to the back (between 0 and -1), set off from [liftFrom]
 * px up, it's a card moved from the top of a pile to the bottom: it carries on up to
 * clear the pile, whole, then goes behind and settles into the back place
 * ([backDepth]), taking on the look of the card there only as it arrives.
 */
fun cardPose(
    depth: Float,
    backDepth: Float,
    heightPx: Float,
    stepPx: Float,
    lift: Float,
    liftFrom: Float,
    thresholdPx: Float,
): CardPose {
    fun held(up: Float) = lerp(1f, PickScale, ramp(up, 0f, thresholdPx))
    if (depth == 0f) return CardPose(held(lift), -lift, 1f)
    if (depth > 0f) return CardPose(stackScale(depth), stackRise(depth, stepPx), stackAlpha(depth))
    val t = -depth
    val back = CardPose(stackScale(backDepth), stackRise(backDepth, stepPx), stackAlpha(backDepth))
    val top = minOf(back.rise - heightPx * PickLift, -liftFrom)
    return if (t < ToTopShare) {
        val u = t / ToTopShare
        val eased = 1f - (1f - u) * (1f - u)
        CardPose(lerp(held(liftFrom), PickScale, eased), lerp(-liftFrom, top, eased), 1f)
    } else {
        val u = (t - ToTopShare) / (1f - ToTopShare)
        val eased = u * u * (3 - 2 * u)
        CardPose(lerp(PickScale, back.scale, eased), lerp(top, back.rise, eased), lerp(1f, back.alpha, eased * eased))
    }
}

/** How much of its contents a card [depth] into the pile shows: the front one's, and the next one's as the front is lifted off it. */
fun CardPileState.contentAlpha(index: Int, thresholdPx: Float, inFrontAway: Boolean = false): Float {
    val d = depth(index)
    return when {
        // On its way up; gone as it passes behind the pile.
        d < 0f -> 1f - ramp(-d, ToTopShare, ToTopShare + 0.2f)
        // Next in line: showing as the front one is lifted off it, and staying shown as it
        // comes forward once that one is let go high enough, or as the front one is away.
        d == 1f -> if (inFrontAway) 1f else ramp(lift.value, 0f, thresholdPx)
        d > 0f && d < 1f -> maxOf(1f - d, ramp(liftFrom, 0f, thresholdPx))
        // In front; the rest of the pile shows none.
        else -> (1f - d).coerceIn(0f, 1f)
    }
}

/** [PickThreshold] in px. */
fun Density.pickThresholdPx(): Float = PickThreshold.toPx()

/** Maps [value] from [start]..[end] onto 0..1, clamped. */
private fun ramp(value: Float, start: Float, end: Float) = ((value - start) / (end - start)).coerceIn(0f, 1f)

/** How clearly a card [depth] back in the pile shows: fainter further back, but all of the pile shows. */
private fun stackAlpha(depth: Float): Float = (1f - 0.25f * depth).coerceIn(0f, 1f)

/** How big a card [depth] back in the pile is drawn. */
private fun stackScale(depth: Float): Float = 1f - PileShrink * depth.coerceAtLeast(0f)

/** How far (px, negative = up) a card [depth] back in the pile is raised, by [step] per place. */
private fun stackRise(depth: Float, step: Float): Float = -step * depth.coerceAtLeast(0f)

private fun floorMod(value: Int, count: Int): Int = ((value % count) + count) % count

private fun floorModFloat(value: Float, count: Float): Float = ((value % count) + count) % count
