package com.example.samsonic.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.R
import com.example.samsonic.data.ThemeManager

/** The player's swipes: whether swiping the mini player sideways changes song (on by default). */
@Composable
internal fun SwipeGestureRows(themeManager: ThemeManager) {
    val miniSong by themeManager.swipeMiniForSong.collectAsStateWithLifecycle()

    SwitchRow(
        icon = Icons.Filled.SwapHoriz,
        title = stringResource(R.string.settings_swipe_mini_song),
        checked = miniSong,
        onCheckedChange = themeManager::setSwipeMiniForSong,
        hint = stringResource(R.string.settings_swipe_mini_song_hint),
    )
}
