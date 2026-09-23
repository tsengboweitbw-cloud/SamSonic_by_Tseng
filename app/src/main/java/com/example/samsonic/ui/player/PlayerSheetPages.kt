package com.example.samsonic.ui.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException

// How far the sheet shrinks while a predictive back gesture is held, before release.
private const val BackPeek = 0.3f

/**
 * What the expanded sheet shows: Now Playing, with the queue or lyrics sliding
 * up over it (and back down) the way they used to open as their own screens.
 */
@Composable
internal fun PlayerPages(sheet: PlayerSheetState) {
    AnimatedContent(
        targetState = sheet.page,
        transitionSpec = {
            if (targetState != PlayerPage.NowPlaying) {
                (slideInVertically(tween(260)) { it } + fadeIn(tween(260))) togetherWith fadeOut(tween(260))
            } else {
                fadeIn(tween(220)) togetherWith (slideOutVertically(tween(220)) { it } + fadeOut(tween(220)))
            }
        },
        label = "playerPage",
    ) { page ->
        when (page) {
            PlayerPage.NowPlaying -> NowPlayingScreen(
                onCollapse = { sheet.collapse() },
                onShowQueue = { sheet.page = PlayerPage.Queue },
                onShowLyrics = { sheet.page = PlayerPage.Lyrics },
            )
            PlayerPage.Queue -> SheetPage { QueueScreen(onCollapse = { sheet.page = PlayerPage.NowPlaying }) }
            PlayerPage.Lyrics -> SheetPage { LyricsScreen(onCollapse = { sheet.page = PlayerPage.NowPlaying }) }
        }
    }
}

/** Queue and lyrics have no backdrop of their own; give them an opaque page. */
@Composable
private fun SheetPage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
}

/**
 * Back from queue or lyrics returns to Now Playing. Back from Now Playing
 * follows the gesture: the sheet shrinks with the swipe and collapses on
 * release, or grows back if the gesture is cancelled.
 */
@Composable
internal fun PlayerSheetBackHandling(sheet: PlayerSheetState) {
    PredictiveBackHandler(enabled = sheet.isExpanded && sheet.page == PlayerPage.NowPlaying) { events ->
        try {
            events.collect { event -> sheet.snapTo(1f - event.progress * BackPeek) }
            sheet.collapse()
        } catch (e: CancellationException) {
            sheet.expand()
            throw e
        }
    }
    BackHandler(enabled = sheet.isExpanded && sheet.page != PlayerPage.NowPlaying) {
        sheet.page = PlayerPage.NowPlaying
    }
}
