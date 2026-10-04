package com.example.samsonic.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.samsonic.R
import com.example.samsonic.model.Song
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.playback.PlayerState
import com.example.samsonic.ui.library.LocalAddToPlaylist
import com.example.samsonic.ui.library.PlaylistItems
import com.example.samsonic.ui.theme.AccentSheen
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.accentPalette
import com.example.samsonic.ui.theme.glassSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val ListActionButtonSize = 56.dp
internal val ListActionButtonGap = 20.dp
private val QueueConfirmMillis = 1500L

private enum class ListAction { Play, Shuffle, Queue }

/**
 * The Play / Shuffle / Queue row under a detail page's header (album, artist,
 * playlist, Home shelf): round frosted-glass icon buttons, centered - Play tinted
 * with the accent ([accentGlass]), the rest neutral: Shuffle, Queue (which
 * appends the list to the play queue) and, given a [playlistTitle], Add to playlist,
 * which opens the card for all of them. Each answers a tap with a haptic
 * tick and a springy bounce ([RoundButton]) instead of an M3 ripple.
 */
@Composable
fun PlayShuffleButtons(songs: List<Song>, modifier: Modifier = Modifier, playlistTitle: String? = null) {
    val player = LocalPlayerState.current
    val queued = rememberQueuedConfirmation(key = songs)
    PlayShuffleRow(
        onAction = { action ->
            player.perform(action, songs)
            if (action == ListAction.Queue) queued.value = true
        },
        modifier = modifier,
        queued = queued.value,
        addToPlaylist = playlistTitle?.let { title -> PlaylistItems(title) { songs } },
    )
}

/**
 * [PlayShuffleButtons] for a page that lists albums rather than songs: the first tap
 * calls [loadSongs] (showing a spinner in the tapped button) and later taps reuse the
 * result. A new [key] - say, a refreshed album list - drops the cached songs.
 */
@Composable
fun PlayShuffleButtons(
    key: Any?,
    loadSongs: suspend () -> List<Song>,
    modifier: Modifier = Modifier,
    playlistTitle: String? = null,
) {
    val player = LocalPlayerState.current
    val scope = rememberCoroutineScope()
    var songs by remember(key) { mutableStateOf<List<Song>?>(null) }
    var loading by remember(key) { mutableStateOf<ListAction?>(null) }
    val queued = rememberQueuedConfirmation(key)
    fun start(action: ListAction) {
        songs?.let {
            player.perform(action, it)
            if (action == ListAction.Queue) queued.value = true
            return
        }
        if (loading != null) return
        loading = action
        scope.launch {
            try {
                val loaded = loadSongs()
                songs = loaded
                player.perform(action, loaded)
                if (action == ListAction.Queue) queued.value = true
            } finally {
                loading = null
            }
        }
    }
    PlayShuffleRow(
        onAction = ::start,
        modifier = modifier,
        loading = loading,
        queued = queued.value,
        // The songs load with the card's first pick, and stay for the other buttons.
        addToPlaylist = playlistTitle?.let { title ->
            PlaylistItems(title) { songs ?: loadSongs().also { songs = it } }
        },
    )
}

/** Set to true after a Queue tap; flips back by itself so the check mark is brief. */
@Composable
private fun rememberQueuedConfirmation(key: Any?): MutableState<Boolean> {
    val queued = remember(key) { mutableStateOf(false) }
    LaunchedEffect(queued.value) {
        if (queued.value) {
            delay(QueueConfirmMillis)
            queued.value = false
        }
    }
    return queued
}

private fun PlayerState.perform(action: ListAction, songs: List<Song>) {
    if (action == ListAction.Queue) return addToQueue(songs)
    val order = if (action == ListAction.Shuffle) songs.shuffled() else songs
    if (order.isNotEmpty()) play(order.first(), order)
}

@Composable
private fun PlayShuffleRow(
    onAction: (ListAction) -> Unit,
    modifier: Modifier = Modifier,
    loading: ListAction? = null,
    queued: Boolean = false,
    // What an Add to playlist button adds; null (or nowhere to add to) leaves the button out.
    addToPlaylist: PlaylistItems? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val playlistMenu = LocalAddToPlaylist.current.takeIf { addToPlaylist != null }
    val playlistButton = remember { arrayOf(Rect.Zero) }
    // No hazeState on the glass buttons: the row sits inside the page list's own
    // haze source (see GlassBackButton), so they use the flat translucent glass fill.
    val glass = Modifier
        .listActionShadow()
        .glassSurface(
            shape = CircleShape,
            hazeState = null,
            tint = MaterialTheme.colorScheme.surfaceContainerHigh,
            alpha = GlassAlpha.Nav,
            // The nav bar's accent sheen, as on the other chrome glass.
            sheen = AccentSheen.Chrome,
        )
    val merge = LocalListActionsMerge.current
    // A tap on any of them also tells a corner capsule holding them to fold back up.
    val afterAction = LocalListActionsAfterAction.current
    // Neutral buttons lose their own glass as the capsule comes in; Play keeps its accent.
    val neutralAlpha = { 1f - ramp(merge(), 0.15f, 0.7f) }
    MergingRow(merge = merge, modifier = modifier) {
        RoundButton(
            onClick = { onAction(ListAction.Play); afterAction() },
            contentColor = MaterialTheme.colorScheme.onPrimary,
            surface = Modifier.accentGlass(MaterialTheme.accentPalette),
        ) { color, pop -> ButtonIcon(Icons.Filled.PlayArrow, loading == ListAction.Play, color, stringResource(R.string.components_play), pop, size = 30.dp) }
        RoundButton(onClick = { onAction(ListAction.Shuffle); afterAction() }, contentColor = MaterialTheme.colorScheme.onSurface, surface = glass, surfaceAlpha = neutralAlpha) { color, pop ->
            ButtonIcon(Icons.Filled.Shuffle, loading == ListAction.Shuffle, color, stringResource(R.string.components_shuffle), pop)
        }
        RoundButton(
            onClick = { onAction(ListAction.Queue); afterAction() },
            contentColor = if (queued) accent else MaterialTheme.colorScheme.onSurface,
            surface = glass,
            surfaceAlpha = neutralAlpha,
        ) { color, pop ->
            ButtonIcon(
                icon = if (queued) Icons.Filled.Check else Icons.AutoMirrored.Filled.PlaylistAdd,
                loading = loading == ListAction.Queue,
                color = color,
                contentDescription = stringResource(R.string.components_add_to_queue),
                pop = pop,
            )
        }
        if (playlistMenu != null && addToPlaylist != null) {
            // The card grows out of this button, a circle, and folds back into it.
            RoundButton(
                // Its row has its own Add to queue button.
                onClick = {
                    playlistMenu.open(addToPlaylist, playlistButton[0], originRadius = ListActionButtonSize / 2, offersQueue = false)
                    afterAction()
                },
                contentColor = MaterialTheme.colorScheme.onSurface,
                surface = glass,
                surfaceAlpha = neutralAlpha,
                modifier = Modifier.onGloballyPositioned { playlistButton[0] = it.boundsInRoot() },
            ) { color, pop ->
                ButtonIcon(Icons.Filled.LibraryAdd, loading = false, color = color, contentDescription = stringResource(R.string.components_add_to_playlist), pop = pop)
            }
        }
    }
}

private fun ramp(value: Float, from: Float, to: Float): Float = ((value - from) / (to - from)).coerceIn(0f, 1f)
