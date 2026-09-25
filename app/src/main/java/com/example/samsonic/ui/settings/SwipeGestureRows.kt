package com.example.samsonic.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.R
import com.example.samsonic.data.ThemeManager

/**
 * The player's swipes, each on its own: which Now Playing swipes open a panel
 * (right for lyrics, left for song info, up for the queue, the only one of those
 * on by default; the panels' buttons work either way), and whether swiping the
 * mini player sideways changes song (on by default).
 */
@Composable
internal fun SwipeGestureRows(themeManager: ThemeManager) {
    val lyrics by themeManager.swipeForLyrics.collectAsStateWithLifecycle()
    val info by themeManager.swipeForInfo.collectAsStateWithLifecycle()
    val queue by themeManager.swipeForQueue.collectAsStateWithLifecycle()
    val miniSong by themeManager.swipeMiniForSong.collectAsStateWithLifecycle()

    SwitchRow(
        icon = Icons.Filled.Lyrics,
        title = stringResource(R.string.settings_swipe_lyrics),
        checked = lyrics,
        onCheckedChange = themeManager::setSwipeForLyrics,
        hint = stringResource(R.string.settings_swipe_lyrics_hint),
    )
    SwitchRow(
        icon = Icons.Filled.Info,
        title = stringResource(R.string.settings_swipe_info),
        checked = info,
        onCheckedChange = themeManager::setSwipeForInfo,
        hint = stringResource(R.string.settings_swipe_info_hint),
    )
    SwitchRow(
        icon = Icons.AutoMirrored.Filled.QueueMusic,
        title = stringResource(R.string.settings_swipe_queue),
        checked = queue,
        onCheckedChange = themeManager::setSwipeForQueue,
        hint = stringResource(R.string.settings_swipe_queue_hint),
    )
    SwitchRow(
        icon = Icons.Filled.SwapHoriz,
        title = stringResource(R.string.settings_swipe_mini_song),
        checked = miniSong,
        onCheckedChange = themeManager::setSwipeMiniForSong,
        hint = stringResource(R.string.settings_swipe_mini_song_hint),
    )
}
