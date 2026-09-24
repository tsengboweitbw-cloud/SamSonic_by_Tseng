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
 * [queue] and song [info] open as panels; dragging up once the sheet is open
 * pulls the queue up instead.
 */
@Stable
class PlayerSheetState internal constructor(scope: CoroutineScope) {
    private val track = SpringTrack(scope)

    /** What the current drag moves, picked by its first step; null until then. */
    private var dragTarget: DragTarget? = null

    /** 0 collapsed .. 1 expanded. */
    val progress: Float get() = track.position

    /** Whether the sheet is expanded or heading there (drives back handling). */
    var isExpanded by mutableStateOf(false)
        private set

    val lyrics = PanelState(scope)
    val queue = PanelState(scope)
    val info = PanelState(scope)

    /** Whether dragging up on an open Now Playing pulls the queue up (its switch in Settings). */
    internal var swipeUpForQueue = true

    /** Whether a panel covers Now Playing, or is heading there. */
    val hasPanelOpen: Boolean get() = lyrics.isOpen || queue.isOpen || info.isOpen

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

    internal fun startDrag() {
        track.stop()
        queue.stop()
        dragTarget = null
    }

    internal fun dragBy(deltaPx: Float) {
        val target = dragTarget ?: run {
            if (deltaPx == 0f) return
            val upFromOpen = progress > 0.999f && deltaPx < 0f
            // Up from a fully open Now Playing: the queue follows the finger up. Switched
            // off, the whole drag does nothing, so Now Playing doesn't follow the finger
            // back down either.
            when {
                !upFromOpen -> DragTarget.Sheet
                swipeUpForQueue -> DragTarget.Queue
                else -> DragTarget.None
            }.also { dragTarget = it }
        }
        when (target) {
            DragTarget.Sheet -> track.dragBy(deltaPx)
            DragTarget.Queue -> queue.dragBy(deltaPx)
            DragTarget.None -> Unit
        }
    }

    /** Settles after a drag released with [velocityPx] (px/s, positive = downward). */
    internal fun settle(velocityPx: Float) {
        when (dragTarget) {
            DragTarget.Queue -> queue.settle(velocityPx)
            DragTarget.None -> Unit
            // A drag that never moved (null) settles the sheet back where it was.
            DragTarget.Sheet, null -> settleTo(track.targetFor(velocityPx), velocityPx)
        }
        dragTarget = null
    }

    private fun settleTo(target: Float, velocityPx: Float = 0f) {
        isExpanded = target == 1f
        if (target == 0f) listOf(lyrics, queue, info).forEach { it.reset() }
        track.animateTo(target, velocityPx)
    }
}

/** What a drag on the sheet moves: the sheet, the queue up over it, or nothing. */
private enum class DragTarget { Sheet, Queue, None }

@Composable
fun rememberPlayerSheetState(): PlayerSheetState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PlayerSheetState(scope) }
}
