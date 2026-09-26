package com.example.samsonic.ui.autodj

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.samsonic.R
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.settings.SettingsMenu
import dev.chrisbanes.haze.HazeState

/**
 * All of Auto DJ's settings in a menu over Settings, grown out of its row like the
 * other settings' menus: the same as its panel in Now Playing ([AutoDjPanel]). It
 * takes all the height between the page's title and the floating chrome, and
 * scrolls within, so the card doesn't resize as filters open up.
 */
@Composable
internal fun AutoDjMenu(panel: PanelState, haze: HazeState) {
    SettingsMenu(panel, haze, title = stringResource(R.string.auto_dj_title)) {
        AutoDjPanel(Modifier.fillMaxWidth().fillMaxHeight())
    }
}
