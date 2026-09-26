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
import com.example.samsonic.ui.theme.AccentSheen
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.LocalHazeState
import com.example.samsonic.ui.theme.OneUiChrome
import com.example.samsonic.ui.theme.glassSurface
import kotlin.math.roundToInt

private val PillMargin = 12.dp
private val PillRadius = OneUiChrome.BarHeight / 2

// How far (in pill heights) a drag down takes the mini player to swipe it away.
private const val DismissTravel = 1.5f

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
 * [collapsedBottom] is the gap between the pill and the bottom of the screen.
 * [onAlbumClick] and [onArtistClick] open those pages from Now Playing, after the sheet folds away.
 */
@Composable
fun PlayerSheet(
    sheet: PlayerSheetState,
    collapsedBottom: Dp,
    onAlbumClick: (albumId: String) -> Unit,
    onArtistClick: (artistId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = LocalPlayerState.current
    val song = player.currentSong
    SideEffect { if (song == null && sheet.isExpanded) sheet.collapse() }
    // Swiping the mini player away stops the music and empties the queue.
    SideEffect { sheet.onDismiss = player::stopAndClearQueue }
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
                    Rect(PillMargin.toPx(), bottom - OneUiChrome.BarHeight.toPx(), full.right - PillMargin.toPx(), bottom)
                }
                SideEffect {
                    sheet.travelPx = collapsed.top
                    sheet.dismissTravelPx = collapsed.height * DismissTravel
                    // Mini rides the frame's corner; Now Playing its top edge at full width.
                    morph.surfaceOrigin = { surface ->
                        val frame = lerp(collapsed, full, sheet.progress)
                        if (surface == PlayerSurface.Mini) frame.topLeft else Offset(0f, frame.top)
                    }
                }
                SheetSurface(sheet, song, collapsed, full)
                // Above the sheet: the cover and progress line in flight.
                PlayerMorphOverlay(morph, Modifier.fillMaxSize())
            }
        }
    }
    PlayerSheetBackHandling(sheet)
}

@Composable
private fun SheetSurface(sheet: PlayerSheetState, song: Song, collapsed: Rect, full: Rect) {
    // Kept until fully open: its art and progress line are the morph's start points.
    // Gone once open, so its (invisible) pill can't catch Now Playing's taps.
    val miniShowing by remember(sheet) { derivedStateOf { sheet.progress < 0.999f } }
    val atRest by remember(sheet) { derivedStateOf { sheet.progress == 0f } }
    val dragState = rememberDraggableState { sheet.dragBy(it) }
    val radiusPx = with(LocalDensity.current) { PillRadius.toPx() }

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
            .graphicsLayer {
                // Pill corners hold until the sheet nearly fills the screen, then square off.
                val radius = lerp(radiusPx, 0f, ramp(sheet.progress, 0.75f, 1f))
                shape = RoundedCornerShape(radius)
                clip = true
                // Swiped down, the pill goes with the finger, under the nav bar, and
                // draws in a little (to 85%) as it fades, until it's gone.
                val away = sheet.dismissal
                if (away > 0f) {
                    translationY = away * sheet.dismissTravelPx
                    val scale = lerp(1f, 0.85f, away)
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - ramp(away, 0.3f, 1f)
                }
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                // Panels over Now Playing take their own drags.
                enabled = !sheet.hasPanelOpen,
                startDragImmediately = sheet.isMoving || sheet.isDismissing,
                onDragStarted = { sheet.startDrag() },
                onDragStopped = { velocity -> sheet.settle(velocity) },
            ),
    ) {
        // The mini player's glass, fading once Now Playing's backdrop covers it.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - ramp(sheet.progress, 0.3f, 0.6f) }
                .glassSurface(
                    shape = RoundedCornerShape(PillRadius),
                    hazeState = LocalHazeState.current,
                    tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                    alpha = GlassAlpha.MiniPlayer,
                    sheen = AccentSheen.Chrome,
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
                    .graphicsLayer { alpha = 1f - ramp(sheet.progress, 0f, 0.25f) },
            ) {
                MiniPlayer(song = song, onExpand = { sheet.expand() })
            }
        }
    }
}
