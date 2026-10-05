package com.example.samsonic.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp

/** How a card is drawn: its size, how far it's raised (px, negative = up) and how clearly it shows. */
class CardPose(val scale: Float, val rise: Float, val alpha: Float) {
    /** This pose [weight] of the way from none (full size, in place, clear) to itself. */
    fun weighted(weight: Float): CardPose =
        if (weight >= 1f) this else CardPose(lerp(1f, scale, weight), rise * weight, lerp(1f, alpha, weight))
}

/**
 * The pose of a card [depth] into the pile of [heightPx]-high cards, [stepPx]
 * apart. In the pile (0 and on) each further back is smaller and lower, the front one
 * held up [lift] px by the finger, growing toward [PickScale] as it nears the threshold
 * ([thresholdPx]). On its way to the back (between 0 and -1), set off from [liftFrom]
 * px up, it's a card moved from the top of a pile to the bottom: it carries on up to
 * clear the pile, whole, then goes down behind it and settles into the back place at its
 * bottom ([backDepth]), taking on the look of the card there only as it arrives.
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
    // Up clear of the front card's place (and of the pile below it), whole, before it goes
    // down behind the pile and comes out at the bottom, the last card.
    val top = minOf(minOf(back.rise, 0f) - heightPx * PickLift, -liftFrom)
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

/**
 * How clearly a card [depth] back in the pile shows: fainter further back, and past the
 * last layer that shows ([ShownLayers]) fading away behind it, so however many cards
 * there are, the pile is only ever a couple of slim edges.
 */
internal fun stackAlpha(depth: Float): Float {
    val d = depth.coerceAtLeast(0f)
    if (d <= ShownLayers) return 1f - 0.25f * d
    return (1f - 0.25f * ShownLayers) * (1f - (d - ShownLayers)).coerceIn(0f, 1f)
}

/** How big a card [depth] back in the pile is drawn: each shown layer smaller, the hidden ones as the last. */
internal fun stackScale(depth: Float): Float = 1f - PileShrink * layerDepth(depth)

/**
 * How far (px, positive = down) a card [depth] back in the pile is lowered: a full [step]
 * for the first card behind, less for the next ([LayerTaper]), so the edges taper off as
 * the Now Bar's do; ones further back wait where the last shown one is.
 */
internal fun stackRise(depth: Float, step: Float): Float = step * layerDepth(depth)

// How many cards behind the front one show, as edges; any more wait unseen behind the last.
internal const val ShownLayers = 2f

// Each layer after the first sits this much of a step (and a shrink) behind the one before.
internal const val LayerTaper = 0.55f

/** A card [depth] back, in steps of the first layer's: 1 for it, tapering after, and no further than the last shown. */
internal fun layerDepth(depth: Float): Float {
    val d = depth.coerceIn(0f, ShownLayers)
    return if (d <= 1f) d else 1f + (d - 1f) * LayerTaper
}

/** How far below the front card a pile of [count] reaches: room to keep for its edges. */
fun pileExtent(count: Int): Dp = PileStep * layerDepth((count - 1).toFloat())

internal fun floorMod(value: Int, count: Int): Int = ((value % count) + count) % count

internal fun floorModFloat(value: Float, count: Float): Float = ((value % count) + count) % count

// Drawing is on the one UI thread, and saveLayer only reads it.
internal val CutFadePaint = androidx.compose.ui.graphics.Paint()

/**
 * Draws card [index] of [pile], cut away where the card just in front of it covers it
 * (see [pileCard]).
 */
internal fun ContentDrawScope.drawPiled(
    pile: CardPileState,
    index: Int,
    cut: () -> Float,
    weight: () -> Float,
    frontScale: () -> Float,
    offsetFromFront: () -> Float,
    cutout: Path,
) {
    val d = pile.depth(index)
    val inFrontIndex = floorMod(index - 1, pile.count)
    val inFront = pile.depth(inFrontIndex)
    // Cut away only where the one in front is drawn over this one: not the front
    // one itself, nor one on its way up over the top, nor by one gone behind.
    val amount = cut().coerceIn(0f, 1f)
    val underIt = (d > 0f || d < -ToTopShare) && inFront >= -ToTopShare && amount > 0f
    if (!underIt) {
        drawContent()
        return
    }
    val step = PileStep.toPx()
    val threshold = PickThreshold.toPx()
    val w = weight()
    val own = pile.pose(index, size.height, step, threshold).weighted(w)
    val front = pile.pose(inFrontIndex, size.height, step, threshold).weighted(w)
    val ratio = front.scale * frontScale() / own.scale
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
    clipPath(cutout, ClipOp.Difference) { this@drawPiled.drawContent() }
    // Partly cut: what's under the card in front shows faded, rather than switching
    // off in one frame (which, through that card's translucent glass, blinked).
    if (amount < 1f) {
        CutFadePaint.alpha = 1f - amount
        drawContext.canvas.saveLayer(cutout.getBounds(), CutFadePaint)
        clipPath(cutout) { this@drawPiled.drawContent() }
        drawContext.canvas.restore()
    }
}

/**
 * How clearly the last shown layer of a pile shows ([ShownLayers]): the least a card is
 * ever shaded. Down to it, a card is shaded toward the page; past it (a hidden layer, or
 * one hidden outright), it really fades.
 */
internal val LastLayerClarity = stackAlpha(ShownLayers)

/** The layer opacity for a card of [clarity]: whole while it's merely shaded, fading past the last shown layer. */
internal fun layerAlpha(clarity: Float): Float = (clarity / LastLayerClarity).coerceIn(0f, 1f)

/** How much a card of [clarity] is shaded toward the page, over its glass: never more than the last shown layer. */
internal fun shade(clarity: Float): Float = (1f - clarity).coerceIn(0f, 1f - LastLayerClarity)
