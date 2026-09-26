package com.example.samsonic.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Song
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.components.CardPileState
import com.example.samsonic.ui.components.PileDrag
import com.example.samsonic.ui.components.PileStep
import com.example.samsonic.ui.components.contentAlpha
import com.example.samsonic.ui.components.pickThresholdPx
import com.example.samsonic.ui.components.pileCard
import com.example.samsonic.ui.components.pileSwipe
import com.example.samsonic.ui.theme.AccentSheen
import com.example.samsonic.ui.theme.ChromeBlurScale
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.LocalHazeState
import com.example.samsonic.ui.theme.OneUiChrome
import com.example.samsonic.ui.theme.glassSurface
import kotlin.math.roundToInt

private val PillMargin = 12.dp
private val PillRadius = OneUiChrome.BarHeight / 2

/**
 * How the mini player's pill is drawn off its place as it comes in ([coming], 1 to 0)
 * or is swiped away ([away], 0 to 1): moved down (px), scaled about its centre, faded.
 * Shared with the nav bar behind it in the stacked pile, which is cut away just where
 * the pill is drawn, as clearly as it's drawn, so it dissolves under the pill as that
 * comes over it rather than being cut where the pill isn't yet.
 */
internal object MiniPillMotion {
    fun shift(coming: Float, away: Float, heightPx: Float, awayTravelPx: Float): Float =
        coming * heightPx * 0.9f + away * awayTravelPx

    fun scale(coming: Float, away: Float): Float = lerp(1f, 0.92f, coming) * lerp(1f, 0.85f, away)

    fun alpha(coming: Float, away: Float): Float = (1f - coming) * (1f - ramp(away, 0.3f, 1f))
}

/** The mini player's place in the pile it shares with the nav bar (card 0). */
const val MiniPlayerCard = 1

// How far (in pill heights) a drag down takes the mini player to swipe it away. Piled
// with the nav bar at the foot of the screen there's little room below it (and the
// edge is the system's), so there a short swipe or a flick does.
private const val DismissTravel = 1.5f
private const val PiledDismissTravel = 0.45f
// And there a slow swipe goes once past a third of that.
private const val PiledDismissCommit = 0.35f
// Let go of a lift handed over to Now Playing moving at least this fast (per second), and
// its direction decides: on up opens it, back down closes it. Slower, it opens once this
// far open, a quarter of the way rather than halfway: getting there was the lift's intent.
private val LiftDirectionSpeed = 80.dp
private const val LiftOpenAt = 0.25f

/** Maps [value] from [start]..[end] onto 0..1, clamped. */
private fun ramp(value: Float, start: Float, end: Float) = ((value - start) / (end - start)).coerceIn(0f, 1f)

/**
 * The player as one sheet: collapsed it is the mini player's glass pill; dragged
 * up (or tapped) it grows, with the finger, into full-screen Now Playing, and
 * dragged down it shrinks back; dragged down from rest, the pill is swiped away
 * and playback stops. The pill widens and its corners square off as
 * it grows, the mini player's contents fade out as Now Playing's fade in, and
 * the cover and progress line travel between the two ([PlayerMorphState]).
 * Lyrics, queue and song info open inside the sheet, growing out of the capsule
 * stack at the foot of Now Playing.
 *
 * [collapsedBottom] is the gap between the pill and the bottom of the screen, and
 * [collapsedMargin] that at each side. Given a [pile], the pill is its card
 * [MiniPlayerCard], piled with the nav bar: a swipe up at rest sends it to the back
 * (or does nothing while it's behind) instead of opening Now Playing, which a tap does.
 * [onAlbumClick] and [onArtistClick] open those pages from Now Playing, after the sheet folds away.
 */
@Composable
fun PlayerSheet(
    sheet: PlayerSheetState,
    collapsedBottom: Dp,
    onAlbumClick: (albumId: String) -> Unit,
    onArtistClick: (artistId: String) -> Unit,
    modifier: Modifier = Modifier,
    collapsedMargin: Dp = PillMargin,
    pile: CardPileState? = null,
    pileWeight: () -> Float = { 1f },
    pileOffset: () -> Float = { 0f },
    // How far the pill has come in (1 at rest), rising from below as it appears in the pile.
    entrance: () -> Float = { 1f },
) {
    val player = LocalPlayerState.current
    val song = player.currentSong
    SideEffect { if (song == null && sheet.isExpanded) sheet.collapse() }
    // Swiping the mini player away stops the music and empties the queue.
    SideEffect { sheet.onDismiss = player::stopAndClearQueue }
    // Swiped away, the pill stays gone until music comes back.
    LaunchedEffect(song != null) { if (song != null) sheet.clearDismissal() }
    val morph = remember(sheet) { PlayerMorphState(progress = { sheet.progress }, active = { sheet.isMoving }) }
    val links = remember(sheet, onAlbumClick, onArtistClick) {
        PlayerLinks(
            openAlbum = { sheet.collapse(); onAlbumClick(it) },
            openArtist = { sheet.collapse(); onArtistClick(it) },
        )
    }

    AnimatedVisibility(visible = song != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        if (song == null) return@AnimatedVisibility
        CompositionLocalProvider(LocalPlayerMorph provides morph, LocalPlayerLinks provides links) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val full = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                val collapsed = with(density) {
                    val bottom = full.bottom - collapsedBottom.toPx()
                    Rect(collapsedMargin.toPx(), bottom - OneUiChrome.BarHeight.toPx(), full.right - collapsedMargin.toPx(), bottom)
                }
                SideEffect {
                    sheet.travelPx = collapsed.top
                    sheet.dismissTravelPx = collapsed.height * (if (pile != null) PiledDismissTravel else DismissTravel)
                    sheet.dismissCommitAt = if (pile != null) PiledDismissCommit else 0.5f
                    // Mini rides the frame's corner; Now Playing its top edge at full width.
                    // Piled, both also ride the card's lift in the pile (its layer moves the
                    // frame, but the cover and progress line in flight are drawn apart).
                    fun cardPose() = pile?.pose(
                        MiniPlayerCard,
                        collapsed.height,
                        with(density) { PileStep.toPx() },
                        density.pickThresholdPx(),
                    )?.weighted(pileWeight())
                    morph.surfaceOrigin = { surface ->
                        val frame = lerp(collapsed, full, sheet.progress)
                        val rise = cardPose()?.rise ?: 0f
                        if (surface == PlayerSurface.Mini) frame.topLeft + Offset(0f, rise) else Offset(0f, frame.top + rise)
                    }
                    // A lifted card is a touch bigger too, about its frame's centre.
                    morph.surfaceScale = {
                        val pose = cardPose()
                        if (pose == null || pose.scale == 1f) {
                            Offset.Zero to 1f
                        } else {
                            lerp(collapsed, full, sheet.progress).center + Offset(0f, pose.rise) to pose.scale
                        }
                    }
                }
                SheetSurface(sheet, song, collapsed, full, pile, pileWeight, pileOffset, entrance)
                // Above the sheet: the cover and progress line in flight.
                PlayerMorphOverlay(morph, Modifier.fillMaxSize())
            }
        }
    }
    PlayerSheetBackHandling(sheet)
}

@Composable
private fun SheetSurface(
    sheet: PlayerSheetState,
    song: Song,
    collapsed: Rect,
    full: Rect,
    pile: CardPileState?,
    pileWeight: () -> Float,
    pileOffset: () -> Float,
    entrance: () -> Float,
) {
    // Kept until fully open: its art and progress line are the morph's start points.
    // Gone once open, so its (invisible) pill can't catch Now Playing's taps.
    val miniShowing by remember(sheet) { derivedStateOf { sheet.progress < 0.999f } }
    val atRest by remember(sheet) { derivedStateOf { sheet.progress == 0f } }
    val pillBlurs by remember(sheet) { derivedStateOf { sheet.progress < 0.15f } }
    val dragState = rememberDraggableState { sheet.dragBy(it) }
    val radiusPx = with(LocalDensity.current) { PillRadius.toPx() }
    // Piled and at rest, a swipe up goes to the pile, and carried on up opens Now Playing;
    // one down still swipes the pill away. Drawn in the pile as it comes together, but
    // swiped only once it has. The pile's gesture stays on through the opening it hands
    // over to (it's only taken at rest), so the drag carries on under the finger.
    val posed = pile != null
    val settled by remember(pileWeight) { derivedStateOf { pileWeight() >= 1f } }
    val swipes = posed && settled
    val piled = swipes && atRest
    // A lift carried on past the hand-off (see pileSwipe): let go, and the way the finger
    // is going decides, however slowly (a slow, steady lift is never a fling).
    val liftFlingPx = with(LocalDensity.current) { LiftDirectionSpeed.toPx() }
    val liftIntoSheet = remember(sheet, liftFlingPx) {
        object : PileDrag {
            override fun start() = sheet.startDrag()
            override fun drag(deltaPx: Float) = sheet.dragBy(deltaPx)
            override fun end(velocityPx: Float) = sheet.settle(velocityPx, flingPx = liftFlingPx, openAt = LiftOpenAt)
        }
    }
    val sheetDrag = remember(sheet) {
        object : PileDrag {
            override fun start() = sheet.startDrag()
            override fun drag(deltaPx: Float) = sheet.dragBy(deltaPx)
            override fun end(velocityPx: Float) = sheet.settle(velocityPx)
        }
    }

    Box(
        modifier = Modifier
            // The sheet's frame: the pill's rect grown toward the full screen.
            .layout { measurable, constraints ->
                val bounds = lerp(collapsed, full, sheet.progress)
                val placeable = measurable.measure(
                    Constraints.fixed(bounds.width.roundToInt(), bounds.height.roundToInt()),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(bounds.left.roundToInt(), bounds.top.roundToInt())
                }
            }
            .then(
                if (posed) {
                    Modifier.pileCard(
                        pile!!,
                        MiniPlayerCard,
                        OneUiChrome.BarHeight,
                        ignoreTouchesBehind = true,
                        // Kept as the sheet opens out of it: a lift handed over to the
                        // opening eases away with it (CardPileState.easeLiftInto).
                        weight = pileWeight,
                        offsetFromFront = pileOffset,
                    )
                } else {
                    Modifier
                },
            )
            .graphicsLayer {
                // Pill corners hold until the sheet nearly fills the screen, then square off.
                val radius = lerp(radiusPx, 0f, ramp(sheet.progress, 0.75f, 1f))
                shape = RoundedCornerShape(radius)
                clip = true
                // Swiped down, the pill goes with the finger, under the nav bar, and
                // draws in a little (to 85%) as it fades, until it's gone.
                // Coming in (up from below its place, growing a touch, as it fades in) or
                // swiped away (down with the finger, drawing in, fading out): see MiniPillMotion.
                val coming = 1f - entrance()
                val away = sheet.dismissal
                if (coming > 0f || away > 0f) {
                    translationY = MiniPillMotion.shift(coming, away, size.height, sheet.dismissTravelPx)
                    val scale = MiniPillMotion.scale(coming, away)
                    scaleX = scale
                    scaleY = scale
                    alpha = MiniPillMotion.alpha(coming, away)
                }
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                // Panels over Now Playing take their own drags; piled at rest, the pile does.
                enabled = !sheet.hasPanelOpen && !piled,
                startDragImmediately = sheet.isMoving || sheet.isDismissing,
                onDragStarted = { sheet.startDrag() },
                onDragStopped = { velocity -> sheet.settle(velocity) },
            )
            .then(
                if (swipes) {
                    Modifier.pileSwipe(
                        pile!!,
                        OneUiChrome.BarHeight,
                        downDrag = sheetDrag,
                        liftDrag = liftIntoSheet,
                        enabled = { sheet.progress == 0f },
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        // The mini player's glass, fading once Now Playing's backdrop covers it.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - ramp(sheet.progress, 0.3f, 0.6f) }
                .glassSurface(
                    shape = RoundedCornerShape(PillRadius),
                    // Only near rest: the frame grows every frame of the morph, and a blur
                    // of a new size each frame costs, while Now Playing fades in over it.
                    hazeState = LocalHazeState.current.takeIf { pillBlurs },
                    tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                    alpha = GlassAlpha.MiniPlayer,
                    sheen = AccentSheen.Chrome,
                    // At rest only: a scaled copy is a buffer the glass's size, remade each
                    // frame the frame grows.
                    inputScale = ChromeBlurScale.takeIf { atRest },
                ),
        )
        // Now Playing, laid out full-screen and riding the sheet's top edge.
        // Parked off-screen while fully collapsed so it can't catch touches.
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints.fixed(full.width.roundToInt(), full.height.roundToInt()),
                    )
                    val bounds = lerp(collapsed, full, sheet.progress)
                    // Report the frame's size, not the larger content's, or it gets re-centred.
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val y = if (atRest) full.height.roundToInt() else 0
                        placeable.place(-bounds.left.roundToInt(), y)
                    }
                }
                .graphicsLayer { alpha = ramp(sheet.progress, 0f, 0.45f) },
        ) {
            PlayerPages(sheet)
        }
        if (miniShowing) {
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        // Keeps the pill's width while the frame grows, fading out in place.
                        val placeable = measurable.measure(
                            Constraints.fixed(collapsed.width.roundToInt(), collapsed.height.roundToInt()),
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                    }
                    .graphicsLayer {
                        // Behind the nav bar, only its glass edge shows.
                        val piledAlpha = pile?.let { lerp(1f, it.contentAlpha(MiniPlayerCard, pickThresholdPx()), pileWeight()) } ?: 1f
                        alpha = (1f - ramp(sheet.progress, 0f, 0.25f)) * piledAlpha
                    },
            ) {
                MiniPlayer(song = song, onExpand = { sheet.expand() })
            }
        }
    }
}
