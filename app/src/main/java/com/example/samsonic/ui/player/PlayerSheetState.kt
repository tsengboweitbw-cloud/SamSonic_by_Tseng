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

    /** Which of the sheet and the queue the current drag moves, picked by its first step. */
    private var dragsQueue: Boolean? = null

    /** 0 collapsed .. 1 expanded. */
    val progress: Float get() = track.position

    /** Whether the sheet is expanded or heading there (drives back handling). */
    var isExpanded by mutableStateOf(false)
        private set

    val lyrics = PanelState(scope)
    val queue = PanelState(scope)
    val info = PanelState(scope)

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
        dragsQueue = null
    }

    internal fun dragBy(deltaPx: Float) {
        val toQueue = dragsQueue ?: run {
            if (deltaPx == 0f) return
            // Up from a fully open Now Playing: the queue follows the finger up.
            (progress > 0.999f && deltaPx < 0f).also { dragsQueue = it }
        }
        if (toQueue) queue.dragBy(deltaPx) else track.dragBy(deltaPx)
    }

    /** Settles after a drag released with [velocityPx] (px/s, positive = downward). */
    internal fun settle(velocityPx: Float) {
        if (dragsQueue == true) queue.settle(velocityPx) else settleTo(track.targetFor(velocityPx), velocityPx)
        dragsQueue = null
    }

    private fun settleTo(target: Float, velocityPx: Float = 0f) {
        isExpanded = target == 1f
        if (target == 0f) listOf(lyrics, queue, info).forEach { it.reset() }
        track.animateTo(target, velocityPx)
    }
}

@Composable
fun rememberPlayerSheetState(): PlayerSheetState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PlayerSheetState(scope) }
}
