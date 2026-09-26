package com.example.samsonic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt

/** How much of the finger's travel the rows one and two places from the swiped one follow. */
private val NeighborPull = floatArrayOf(0f, 0.25f, 0.08f)
/** Letting go: the neighbors swing back past their places a little and settle. */
private val DetachSpring = spring<Float>(dampingRatio = 0.45f, stiffness = 380f)
private val ReattachSpring = spring<Float>(dampingRatio = 1f, stiffness = 500f)

/**
 * Android 16's magnetic swipe: while one row is swiped, the rows just above and below it
 * are pulled a little the same way, moving with it frame by frame. Once the swipe passes
 * its threshold the row breaks free and they snap back to their places with a bounce.
 * Only one row is swiped at a time, so a single shared record of it serves every list.
 */
internal object SwipeNeighbors {
    private var dragged: Any? by mutableStateOf(null)
    private var centerY by mutableFloatStateOf(Float.NaN)
    private var rowHeight by mutableFloatStateOf(0f)
    private var offset by mutableFloatStateOf(0f)
    private var detached by mutableStateOf(false)

    /** [row] starts being swiped; [centerY] and [height] place it on screen. */
    fun begin(row: Any, centerY: Float, height: Float) {
        dragged = row
        this.centerY = centerY
        rowHeight = height
        offset = 0f
        detached = false
    }

    /** The finger has dragged the swiped [row] [offset] px across; neighbors let go while [detached]. */
    fun update(row: Any, offset: Float, detached: Boolean) {
        if (dragged !== row) return
        this.offset = offset
        this.detached = detached
    }

    fun end(row: Any) {
        if (dragged !== row) return
        dragged = null
        offset = 0f
    }

    /** Whether the swiped row has broken free of its neighbors (past its threshold). */
    val isDetached: Boolean get() = dragged != null && detached

    /**
     * How far the row at [centerY] is pulled while attached; 0 unless it's a near neighbor
     * of the swiped one. Follows the finger directly, with no spring of its own in between.
     * [centerY] is only asked for while a row is being swiped.
     */
    fun pullFor(row: Any, centerY: () -> Float): Float {
        if (dragged == null || dragged === row || rowHeight <= 0f) return 0f
        if (this.centerY.isNaN()) return 0f
        val centerY = centerY()
        if (centerY.isNaN()) return 0f
        val steps = abs((centerY - this.centerY) / rowHeight).roundToInt()
        return offset * NeighborPull.getOrElse(steps) { 0f }
    }
}

/**
 * This row's magnetic pull toward a swiped neighbor, in px; [centerY] gives the row's
 * on-screen center, worked out only while some row is swiped (not on every scroll frame). Call the returned
 * function from a layout or draw lambda: it follows the drag every frame without
 * recomposing. Only letting go and reattaching animate, as a spring on how much of the
 * pull applies (restarting a spring per touch event would never let it take a step).
 */
@Composable
internal fun rememberNeighborPull(row: Any, centerY: () -> Float): () -> Float {
    val attached = remember { Animatable(1f) }
    LaunchedEffect(row) {
        snapshotFlow { SwipeNeighbors.isDetached }.collectLatest { detached ->
            attached.animateTo(if (detached) 0f else 1f, if (detached) DetachSpring else ReattachSpring)
        }
    }
    return remember(row) { { SwipeNeighbors.pullFor(row, centerY) * attached.value } }
}
