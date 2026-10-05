package com.example.samsonic.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val LiftHandOff = 128.dp
internal val LiftConversion = 120.dp

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
    val topPx = cardHeight.toPx() * PickLift
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
internal class FingerVelocity(first: PointerInputChange) {
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
