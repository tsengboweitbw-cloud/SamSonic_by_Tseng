package com.example.samsonic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope

/**
 * The player sheet: 0 = collapsed into the mini player pill, 1 = full-screen Now
 * Playing. Drags move [progress] with the finger; a release settles it to the
 * nearer end, or the end a fling heads for. Over Now Playing, [lyrics], the
 * [queue] and song [info] open as panels, from the capsule stack at its foot.
 */
@Stable
class PlayerSheetState internal constructor(scope: CoroutineScope) {
    private val track = SpringTrack(scope)

    /** What the current drag moves, picked by its first step; null until then. */
    private var dragTarget: DragTarget? = null

    /** 0 collapsed .. 1 expanded. */
    val progress: Float get() = track.position

    // Dragging down on the resting mini player swipes it away (the track's 1 is gone).
    private val dismissTrack = SpringTrack(scope)

    /** 0 resting .. 1 swiped away: how far a drag down has taken the mini player. */
    val dismissal: Float get() = dismissTrack.position

    /** Pixels a drag down covers to swipe the mini player away; set by the layout. */
    internal var dismissTravelPx: Float
        get() = dismissTrack.travelPx
        set(value) { dismissTrack.travelPx = value }

    /** Run once the mini player has been swiped away: stops playback. Set by the sheet. */
    internal var onDismiss: () -> Unit = {}

    /** True while the mini player is part way to being swiped away, or back. */
    val isDismissing by derivedStateOf { dismissal > 0.001f && dismissal < 0.999f }

    /** Whether the sheet is expanded or heading there (drives back handling). */
    var isExpanded by mutableStateOf(false)
        private set

    val lyrics = PanelState(scope)
    val queue = PanelState(scope)
    val info = PanelState(scope)
    val autoDj = PanelState(scope)

    /** Whether a panel covers Now Playing, or is heading there. */
    val hasPanelOpen: Boolean get() = lyrics.isOpen || queue.isOpen || info.isOpen || autoDj.isOpen

    /** Pixels the sheet's top travels between collapsed and expanded; set by the layout. */
    internal var travelPx: Float
        get() = track.travelPx
        set(value) { track.travelPx = value }

    /** True between the two ends: while dragging or settling. */
    val isMoving by derivedStateOf { progress > 0.001f && progress < 0.999f }

    fun expand() = settleTo(1f)

    fun collapse() = settleTo(0f)

    internal fun snapTo(target: Float) = track.snapTo(target)

    internal fun stop() = track.stop()

    // Whether the current drag took hold nearer the mini player than Now Playing: only
    // then does pulling it down past rest carry on into swiping it away, so a long
    // swipe down on an open Now Playing only ever closes it.
    private var dragFromMini = false

    internal fun startDrag() {
        track.stop()
        dismissTrack.stop()
        dragTarget = null
        dragFromMini = progress < 0.5f
    }

    internal fun dragBy(deltaPx: Float) {
        val target = dragTarget ?: run {
            if (deltaPx == 0f) return
            val upFromOpen = progress > 0.999f && deltaPx < 0f
            // Down from the resting mini player (or caught mid-swipe away): it follows the
            // finger down, to be swiped away.
            val downFromRest = progress < 0.001f && (deltaPx > 0f || dismissal > 0f)
            // Up from a fully open Now Playing: nothing (there's nowhere further to go),
            // and for the whole drag, so Now Playing doesn't follow the finger back
            // down either.
            when {
                downFromRest -> DragTarget.Dismiss
                upFromOpen -> DragTarget.None
                else -> DragTarget.Sheet
            }.also { dragTarget = it }
        }
        when (target) {
            DragTarget.Sheet -> {
                // Pulled back down past the mini player's rest: the rest of the way swipes
                // it away, in the same gesture.
                val toRest = progress * travelPx
                if (dragFromMini && deltaPx > toRest) {
                    track.snapTo(0f)
                    dragTarget = DragTarget.Dismiss
                    dismissTrack.dragBy(-(deltaPx - toRest))
                } else {
                    track.dragBy(deltaPx)
                }
            }
            DragTarget.Dismiss -> {
                // Brought back up past rest: the rest of the way opens the sheet, likewise.
                // (The track's 1 is down here, so the finger's direction flips.)
                val toRest = dismissal * dismissTravelPx
                if (-deltaPx > toRest) {
                    dismissTrack.snapTo(0f)
                    dragTarget = DragTarget.Sheet
                    track.dragBy(deltaPx + toRest)
                } else {
                    dismissTrack.dragBy(-deltaPx)
                }
            }
            DragTarget.None -> Unit
        }
    }

    /** Settles after a drag released with [velocityPx] (px/s, positive = downward). */
    internal fun settle(velocityPx: Float) {
        when (dragTarget) {
            DragTarget.Dismiss -> settleDismissal(velocityPx)
            DragTarget.None -> Unit
            // A drag that never moved (null) settles the sheet back where it was.
            DragTarget.Sheet, null -> settleTo(track.targetFor(velocityPx), velocityPx)
        }
        dragTarget = null
    }

    // Null [velocityPx]: carry on at the speed of any settle under way (see SpringTrack.animateTo).
    private fun settleTo(target: Float, velocityPx: Float? = null) {
        isExpanded = target == 1f
        if (target == 0f) listOf(lyrics, queue, info, autoDj).forEach { it.reset() }
        track.animateTo(target, velocityPx)
    }

    /**
     * Past halfway, or flung down, the mini player collapses away and playback stops
     * once it's gone (the sheet then leaves with the song, and the next song brings the
     * mini player back at rest); otherwise it springs back.
     */
    private fun settleDismissal(velocityPx: Float) {
        val away = dismissTrack.targetFor(-velocityPx) == 1f
        dismissTrack.animateTo(if (away) 1f else 0f, -velocityPx).invokeOnCompletion { cause ->
            if (away && cause == null) {
                onDismiss()
                dismissTrack.snapTo(0f)
            }
        }
    }
}

/** What a drag on the sheet moves: the sheet, the mini player away, or nothing. */
private enum class DragTarget { Sheet, Dismiss, None }

@Composable
fun rememberPlayerSheetState(): PlayerSheetState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PlayerSheetState(scope) }
}
