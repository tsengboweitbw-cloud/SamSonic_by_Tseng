package com.example.samsonic.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        title = "Swipe right for lyrics",
        checked = lyrics,
        onCheckedChange = themeManager::setSwipeForLyrics,
        hint = "On Now Playing, swipe right to open the lyrics",
    )
    SwitchRow(
        icon = Icons.Filled.Info,
        title = "Swipe left for song info",
        checked = info,
        onCheckedChange = themeManager::setSwipeForInfo,
        hint = "On Now Playing, swipe left to open the song's details",
    )
    SwitchRow(
        icon = Icons.AutoMirrored.Filled.QueueMusic,
        title = "Swipe up for the queue",
        checked = queue,
        onCheckedChange = themeManager::setSwipeForQueue,
        hint = "On Now Playing, swipe up to open what plays next",
    )
}
