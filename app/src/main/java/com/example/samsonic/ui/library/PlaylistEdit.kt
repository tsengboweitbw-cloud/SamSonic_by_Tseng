package com.example.samsonic.ui.library

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.settings.SettingsMenu
import dev.chrisbanes.haze.HazeState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.samsonic.R
import com.example.samsonic.model.Song
import com.example.samsonic.model.artSeed
import com.example.samsonic.ui.components.MediaArt
import com.example.samsonic.ui.components.LocalRowPrefs
import com.example.samsonic.ui.theme.OneUiRow
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** The playlist's name as a field to change while editing. */
@Composable
internal fun PlaylistNameField(name: String, onNameChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.library_playlist_name)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Asks, when the user leaves editing with changes made, whether to save them: a card grown out of
 * the Save button ([panel]). Tapping outside it or going back keeps editing.
 */
@Composable
internal fun SaveChangesMenu(panel: PanelState, haze: HazeState, playlistName: String, onSave: () -> Unit, onDiscard: () -> Unit) {
    SettingsMenu(panel, haze, title = stringResource(R.string.library_save_changes_title)) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(R.string.library_save_changes_body, playlistName),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.library_discard)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSave) { Text(stringResource(R.string.library_save)) }
            }
        }
    }
}

/** A song in the playlist being edited; [uid] tells it from the same song listed again, and stays with it as it moves. */
internal class DraftSong(val uid: Long, val song: Song)

/**
 * Dragging a song of the playlist being edited to another place in [draft]: the dragged row follows
 * the finger ([offset] from where its place in the list puts it) and swaps places with each row its
 * middle reaches. The list scrolls on its own while the finger is within [edgePx] of the top or
 * bottom of what is visible, up to [maxStepPx] a frame, even if the finger keeps still.
 */
internal class PlaylistDragState(
    private val listState: LazyListState,
    private val draft: SnapshotStateList<DraftSong>,
    private val edgePx: Float,
    private val maxStepPx: Float,
) {
    /** The song being dragged, null when none is. */
    var draggedUid by mutableStateOf<Long?>(null)
        private set

    /** How far the dragged row is from the place the list gives it, in pixels. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** The list is being scrolled during the drag, by the edge or by the other hand. */
    var scrolling by mutableStateOf(false)
        private set

    /** Where the list is on the screen, kept up to date by the list itself. */
    var listBounds: Rect = Rect.Zero

    /** A second finger is scrolling the list or its fling is running, so the edge scroll keeps out of the way. */
    var scrolledByHand = false

    // The finger's height on the screen while dragging.
    private var fingerY = 0f

    // How far below the row's top the finger took hold of it.
    private var grabY = 0f

    // Where the dragged row was laid out before the last swap, until the list is laid out afresh;
    // its old position would still say the row sits over the one it just passed.
    private var awaitingLayoutFrom: Int? = null

    // The list's place was just pinned by position, so this frame's scroll would be lost and the row
    // would drift from the finger.
    private var holdingAnchor = false

    // The row the last swap was made with, and when.
    private var lastSwapKey: Any? = null
    private var lastSwapNanos = 0L

    /** The key a song's row has in the list. */
    fun key(item: DraftSong) = "$KEY_PREFIX${item.uid}"

    fun start(uid: Long, fingerY: Float) {
        draggedUid = uid
        offset = 0f
        this.fingerY = fingerY
        awaitingLayoutFrom = null
        lastSwapKey = null
        val row =listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "$KEY_PREFIX$uid" }
        grabY = if (row != null) fingerY - listBounds.top - row.offset else 0f
    }

    fun end() {
        draggedUid = null
        offset = 0f
        awaitingLayoutFrom = null
        scrolling = false
        scrolledByHand = false
        pendingScroll = 0f
        flingVelocity = 0f
    }

    // The other hand's scroll not yet applied, and its fling's speed (px/s, content moving up if positive).
    // They are applied by scrollNearEdges right after the swaps of the same frame: a scroll made before
    // a swap that pins the list's place by position would be thrown away, and the list would stutter.
    private var pendingScroll = 0f
    private var flingVelocity = 0f

    /** The other hand moved the list by [px] (content moving up if positive). */
    fun scrollByHand(px: Float) {
        if (draggedUid != null) pendingScroll += px
    }

    /** The other hand let go at [velocity] px/s (content moving up if positive); the list carries on, slowing down. */
    fun flingByHand(velocity: Float) {
        flingVelocity = if (draggedUid != null) velocity else 0f
    }

    /** A new touch of the other hand stops the fling. */
    fun stopFling() {
        flingVelocity = 0f
    }

    /** The finger moved [dy] pixels down (up if negative), to [fingerY] on the screen. */
    fun drag(dy: Float, fingerY: Float) {
        if (draggedUid == null) return
        offset += dy
        this.fingerY = fingerY
        reorder()
    }

    /** Swaps the dragged row with the one its middle is over, if any. */
    private fun reorder(layoutIsFresh: Boolean = false) {
        val uid = draggedUid ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == "$KEY_PREFIX$uid" } ?: return
        // A frame's start comes after the last frame's layout, swap included, so a fresh layout never waits.
        if (layoutIsFresh) awaitingLayoutFrom = null
        awaitingLayoutFrom?.let { if (current.offset == it) return else awaitingLayoutFrom = null }
        // Offsets built up from each step can drift (a scroll lost to a position request, say); where the
        // row is laid out and where the finger is give it exactly, once the layout has caught up.
        if (layoutIsFresh) offset = fingerY - listBounds.top - grabY - current.offset
        val middle = current.offset + current.size / 2f + offset
        val target = items.firstOrNull {
            it.key != current.key && (it.key as? String)?.startsWith(KEY_PREFIX) == true && middle >= it.offset && middle < it.offset + it.size
        } ?: return
        // Two rows swapping back and forth every frame (the held row's middle sits on their border) would
        // freeze the list and flicker; a swap straight back waits a moment.
        val now = System.nanoTime()
        if (target.key == lastSwapKey && now - lastSwapNanos < SWAP_BACK_NANOS) return
        lastSwapKey = target.key
        lastSwapNanos = now
        val from = draft.indexOfFirst { it.uid == uid }
        val to = draft.indexOfFirst { key(it) == target.key }
        if (from < 0 || to < 0) return
        // The list holds its place by the key of the first visible row. When that row is one of the
        // two swapping it would follow its row to the new place and the whole list would jump a row,
        // worst when flinging up with the dragged row at the top; hold the place by position instead.
        val anchorMoves = items.first().key.let { it == current.key || it == target.key }
        val anchorIndex = listState.firstVisibleItemIndex
        val anchorOffset = listState.firstVisibleItemScrollOffset
        draft.add(to, draft.removeAt(from))
        if (anchorMoves) {
            listState.requestScrollToItem(anchorIndex, anchorOffset)
            // That request resets the position at the next layout, throwing away any scroll made before it.
            holdingAnchor = true
        }
        // The row is laid out at the target's place now; keep it under the finger.
        offset -= target.offset - current.offset
        awaitingLayoutFrom = current.offset
    }

    /**
     * Scrolls the list while the finger is near the top or bottom of what is visible (past the
     * back button above and the bars below, which cover the list's padding), and swaps the dragged
     * row with those that pass under it; runs until cancelled.
     */
    suspend fun scrollNearEdges() {
        var lastNanos = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
            lastNanos = now
            if (draggedUid == null) continue
            // The layout now has the last scroll in it, so rows can be matched with the row's offset.
            reorder(layoutIsFresh = true)
            val info = listState.layoutInfo
            val top = listBounds.top + info.beforeContentPadding + edgePx
            val bottom = listBounds.bottom - info.afterContentPadding - edgePx
            val step = when {
                fingerY < top -> -maxStepPx * ramp((top - fingerY) / edgePx)
                fingerY > bottom -> maxStepPx * ramp((fingerY - bottom) / edgePx)
                else -> 0f
            }
            // The other hand's scroll and fling, decaying the way exponentialDecay does.
            var handStep = pendingScroll
            pendingScroll = 0f
            if (flingVelocity != 0f) {
                val k = 4.2f
                handStep += flingVelocity * (1f - exp(-k * dt)) / k
                flingVelocity *= exp(-k * dt)
                if (abs(flingVelocity) < 30f) flingVelocity = 0f
            }
            val edgeStep = if (handStep == 0f && step != 0f && !scrolledByHand && flingVelocity == 0f) step else 0f
            if (holdingAnchor) {
                // This frame's measure resets the position; apply it after. The edge's scroll must not be
                // dropped here, or a swap every frame would hold the list still.
                pendingScroll += handStep + edgeStep
            } else if (handStep != 0f) {
                // The row moves with the list, so keeping it under the finger takes the offset along.
                val used = listState.scrollBy(handStep)
                offset += used
                if (abs(handStep - used) > 0.5f) flingVelocity = 0f
            } else if (edgeStep != 0f) {
                offset += listState.scrollBy(edgeStep)
            }
            holdingAnchor = false
            // Rows sliding to their places lag behind a list that is scrolling, leaving gaps under the held row.
            scrolling = handStep != 0f || flingVelocity != 0f || (step != 0f && !scrolledByHand)
        }
    }

    /**
     * Scroll speed (0.15 to 1.0 of the maximum) for how far into the edge zone the finger is: gentle
     * at first, quick at the very edge. It stops speeding up past the zone, so a finger resting at the
     * back button's height scrolls at a steady, controllable pace.
     */
    private fun ramp(depth: Float): Float {
        val d = depth.coerceIn(0f, 1f)
        return 0.15f + 0.85f * d * d
    }

    private companion object {
        const val KEY_PREFIX = "edit-"
        const val SWAP_BACK_NANOS = 120_000_000L
    }
}

/**
 * A song of the playlist being edited, with a handle on the right to drag it to another place;
 * [modifier] is where the list animates the others out of the way.
 */
@Composable
internal fun EditSongRow(
    item: DraftSong,
    cornerRadius: Dp,
    drag: PlaylistDragState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val song = item.song
    val haptics = LocalHapticFeedback.current
    val dragging = drag.draggedUid == item.uid
    val likesEnabled = LocalRowPrefs.current.likesEnabled
    val lifted = MaterialTheme.colorScheme.surfaceContainerHigh
    // The handle's place on the screen, for the finger's height there.
    val handle = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun rootY(local: Offset) = handle[0]?.takeIf { it.isAttached }?.localToRoot(local)?.y ?: 0f
    val body = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun bodyY(local: Offset) = body[0]?.takeIf { it.isAttached }?.localToRoot(local)?.y ?: 0f
    Row(
        modifier = modifier
            .then(
                if (dragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            translationY = drag.offset
                            shadowElevation = 12.dp.toPx()
                            shape = OneUiRow.Shape
                            clip = false
                        }
                        .background(lifted, OneUiRow.Shape)
                } else {
                    Modifier
                },
            )
            .fillMaxWidth()
            .onGloballyPositioned { body[0] = it }
            .then(
                if (enabled && (drag.draggedUid == null || dragging)) {
                    // Holding anywhere on the row drags it too, so the last row can be moved even when a bar covers its handle.
                    Modifier.pointerInput(item.uid) {
                        detectHoldToDrag(
                            onStart = { start ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                drag.start(item.uid, bodyY(start))
                            },
                            onEnd = drag::end,
                        ) { position, dy -> drag.drag(dy, bodyY(position)) }
                    }
                } else {
                    Modifier
                },
            )
            .padding(OneUiRow.Inset)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaArt(
            coverArt = song.coverArt,
            colorSeed = song.id.artSeed(),
            size = 44.dp,
            cornerRadius = cornerRadius,
            shadowElevation = 0.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                // As tall as a song row's end is (its heart button, when the Like button is on), so the list does not move.
                .size(width = 48.dp, height = if (likesEnabled) 48.dp else 44.dp)
                .onGloballyPositioned { handle[0] = it }
                .then(
                    if (enabled && (drag.draggedUid == null || dragging)) {
                        Modifier.pointerInput(item.uid) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    drag.start(item.uid, rootY(start))
                                },
                                onDragEnd = drag::end,
                                onDragCancel = drag::end,
                            ) { change, amount ->
                                change.consume()
                                drag.drag(amount.y, rootY(change.position))
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = stringResource(R.string.library_drag_to_move),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Starts a drag once a finger has stayed down for the long-press time, then reports its moves until
 * it lifts. Unlike detectDragGesturesAfterLongPress it ignores other fingers and other handlers'
 * consumption, and it uses up the finger's small drifts while waiting so the list does not start
 * scrolling and take the hold away.
 */
private suspend fun PointerInputScope.detectHoldToDrag(
    onStart: (Offset) -> Unit,
    onEnd: () -> Unit,
    onMove: (position: Offset, dy: Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop * 1.5f
        var last = down.position
        val abandoned = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!change.pressed || (change.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
                change.consume()
                last = change.position
            }
        }
        if (abandoned != null) return@awaitEachGesture
        onStart(last)
        try {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    change.consume()
                    break
                }
                val dy = change.positionChangeIgnoreConsumed().y
                change.consume()
                if (dy != 0f) onMove(change.position, dy)
            }
        } finally {
            onEnd()
        }
    }
}

/**
 * While a song is held for dragging, a second finger put down anywhere on the list scrolls it (and
 * flings it), so the song can be held with one hand and the list moved with the other. The second
 * finger's touches are used up here, so the rows and the list itself leave them alone.
 */
internal fun Modifier.scrollWithSecondFinger(drag: PlaylistDragState): Modifier =
    pointerInput(drag) {
        run {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var scroller: PointerId? = null
                val tracker = VelocityTracker()
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.firstOrNull { it.id == first.id }?.pressed != true) break
                    if (drag.draggedUid == null) continue
                    if (scroller == null) {
                        val down = event.changes.firstOrNull { it.id != first.id && it.pressed && !it.previousPressed }
                        if (down != null) {
                            scroller = down.id
                            drag.scrolledByHand = true
                            drag.stopFling()
                            tracker.resetTracking()
                        }
                    }
                    val touch = scroller?.let { id -> event.changes.firstOrNull { it.id == id } } ?: continue
                    touch.consume()
                    if (touch.pressed) {
                        val dy = touch.positionChangeIgnoreConsumed().y
                        if (dy != 0f) {
                            tracker.addPosition(touch.uptimeMillis, touch.position)
                            drag.scrollByHand(-dy)
                        }
                    } else {
                        scroller = null
                        drag.scrolledByHand = false
                        drag.flingByHand(-tracker.calculateVelocity().y)
                    }
                }
            }
        }
    }
