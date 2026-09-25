package com.example.samsonic.ui.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.example.samsonic.R
import com.example.samsonic.ui.library.AddToPlaylistMenu
import com.example.samsonic.ui.library.LocalAddToPlaylist
import com.example.samsonic.ui.library.rememberAddToPlaylistState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException

// How far the sheet (or a panel) shrinks while a predictive back gesture is held, before release.
private const val BackPeek = 0.3f

/**
 * What the expanded sheet shows: Now Playing, with lyrics, the queue and song
 * info each growing out of their button over it as a floating glass card (and
 * folding back into it).
 */
@Composable
internal fun PlayerPages(sheet: PlayerSheetState) {
    // Now Playing is what the cards' glass blurs; they sit beside it, not inside it,
    // as a haze effect nested in its own source can draw recursively. graphicsLayer()
    // flattens it first, as for the NavHost's source, or text and edges stay sharp.
    val haze = remember { HazeState() }
    // Its own Add to playlist card, over Now Playing and blurring it, like the panels'; the
    // app's one blurs the page under the sheet. Only where there are playlists to add to.
    val canAddToPlaylist = LocalAddToPlaylist.current != null
    val addToPlaylist = rememberAddToPlaylistState()
    Box(Modifier.fillMaxSize()) {
        NowPlayingScreen(
            modifier = Modifier.graphicsLayer().hazeSource(haze),
            onCollapse = { sheet.collapse() },
            lyrics = sheet.lyrics,
            queue = sheet.queue,
            info = sheet.info,
            addToPlaylist = addToPlaylist.takeIf { canAddToPlaylist },
        )
        PanelCard(sheet.lyrics, PanelIcons.Lyrics, title = stringResource(R.string.player_lyrics), haze = haze) {
            LyricsScreen(onCollapse = { sheet.lyrics.close() })
        }
        PanelCard(sheet.queue, PanelIcons.Queue, title = stringResource(R.string.player_up_next), haze = haze) {
            QueueScreen()
        }
        SongInfoPanel(sheet.info, haze)
        if (canAddToPlaylist) AddToPlaylistMenu(addToPlaylist, haze)
    }
}

/**
 * Back follows the gesture: from a panel, it shrinks back toward its button and
 * closes on release; from Now Playing, the sheet shrinks and collapses. Either
 * springs back open if the gesture is cancelled.
 */
@Composable
internal fun PlayerSheetBackHandling(sheet: PlayerSheetState) {
    val panel = listOf(sheet.info, sheet.queue, sheet.lyrics).firstOrNull { it.isOpen }
    // Each shrinks from wherever it is when the gesture starts (still opening, even),
    // not from fully open, so backing out of an opening sheet or panel doesn't jump.
    PredictiveBackHandler(enabled = sheet.isExpanded && panel != null) { events ->
        val open = panel ?: return@PredictiveBackHandler
        var from = Float.NaN
        try {
            events.collect { event ->
                if (from.isNaN()) from = open.progress
                open.snapTo(from * (1f - event.progress * BackPeek))
            }
            open.close()
        } catch (e: CancellationException) {
            open.open()
            throw e
        }
    }
    PredictiveBackHandler(enabled = sheet.isExpanded && panel == null) { events ->
        var from = Float.NaN
        try {
            events.collect { event ->
                if (from.isNaN()) from = sheet.progress
                sheet.snapTo(from * (1f - event.progress * BackPeek))
            }
            sheet.collapse()
        } catch (e: CancellationException) {
            sheet.expand()
            throw e
        }
    }
}
