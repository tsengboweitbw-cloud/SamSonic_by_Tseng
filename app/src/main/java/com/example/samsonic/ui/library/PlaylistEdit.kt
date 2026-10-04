package com.example.samsonic.ui.library

import androidx.compose.foundation.background
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
import androidx.compose.ui.input.pointer.pointerInput
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

    /** Where the list is on the screen, kept up to date by the list itself. */
    var listBounds: Rect = Rect.Zero

    // The finger's height on the screen while dragging.
    private var fingerY = 0f

    // Where the dragged row was laid out before the last swap, until the list is laid out afresh;
    // its old position would still say the row sits over the one it just passed.
    private var awaitingLayoutFrom: Int? = null

    /** The key a song's row has in the list. */
    fun key(item: DraftSong) = "$KEY_PREFIX${item.uid}"

    fun start(uid: Long, fingerY: Float) {
        draggedUid = uid
        offset = 0f
        this.fingerY = fingerY
        awaitingLayoutFrom = null
    }

    fun end() {
        draggedUid = null
        offset = 0f
        awaitingLayoutFrom = null
    }

    /** The finger moved [dy] pixels down (up if negative), to [fingerY] on the screen. */
    fun drag(dy: Float, fingerY: Float) {
        if (draggedUid == null) return
        offset += dy
        this.fingerY = fingerY
        reorder()
    }

    /** Swaps the dragged row with the one its middle is over, if any. */
    private fun reorder() {
        val uid = draggedUid ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == "$KEY_PREFIX$uid" } ?: return
        awaitingLayoutFrom?.let { if (current.offset == it) return else awaitingLayoutFrom = null }
        val middle = current.offset + current.size / 2f + offset
        val target = items.firstOrNull {
            it.key != current.key && (it.key as? String)?.startsWith(KEY_PREFIX) == true && middle >= it.offset && middle < it.offset + it.size
        } ?: return
        val from = draft.indexOfFirst { it.uid == uid }
        val to = draft.indexOfFirst { key(it) == target.key }
        if (from < 0 || to < 0) return
        draft.add(to, draft.removeAt(from))
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
        while (true) {
            withFrameNanos { }
            if (draggedUid == null) continue
            // The layout now has the last scroll in it, so rows can be matched with the row's offset.
            reorder()
            val info = listState.layoutInfo
            val top = listBounds.top + info.beforeContentPadding + edgePx
            val bottom = listBounds.bottom - info.afterContentPadding - edgePx
            val step = when {
                fingerY < top -> -maxStepPx * ((top - fingerY) / edgePx).coerceIn(0.1f, 1.5f)
                fingerY > bottom -> maxStepPx * ((fingerY - bottom) / edgePx).coerceIn(0.1f, 1.5f)
                else -> 0f
            }
            // The row moves with the list, so keeping it under the finger takes the offset along.
            if (step != 0f) offset += listState.scrollBy(step)
        }
    }

    private companion object {
        const val KEY_PREFIX = "edit-"
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
                    if (enabled) {
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
