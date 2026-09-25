package com.example.samsonic.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.R
import com.example.samsonic.data.ThemeManager

/**
 * Which Now Playing swipes open a panel, each on its own: right for lyrics, left
 * for song info, up for the queue (the only one on by default). The panels'
 * buttons work either way.
 */
@Composable
internal fun SwipeGestureRows(themeManager: ThemeManager) {
    val lyrics by themeManager.swipeForLyrics.collectAsStateWithLifecycle()
    val info by themeManager.swipeForInfo.collectAsStateWithLifecycle()
    val queue by themeManager.swipeForQueue.collectAsStateWithLifecycle()

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
}
