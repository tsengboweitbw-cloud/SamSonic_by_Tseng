package com.example.samsonic.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.data.ListActionsPin
import com.example.samsonic.ui.player.PanelState
import dev.chrisbanes.haze.HazeState

private val ListActionsPin.label: Int
    get() = when (this) {
        ListActionsPin.OFF -> R.string.settings_list_actions_scroll
        ListActionsPin.TOP -> R.string.settings_list_actions_top
        ListActionsPin.BOTTOM_LEFT -> R.string.settings_list_actions_bottom_left
        ListActionsPin.BOTTOM_RIGHT -> R.string.settings_list_actions_bottom_right
        ListActionsPin.SIDE_LEFT -> R.string.settings_list_actions_side_left
        ListActionsPin.SIDE_RIGHT -> R.string.settings_list_actions_side_right
        ListActionsPin.CORNER -> R.string.settings_list_actions_corner
    }

private val ListActionsPin.icon: ImageVector
    get() = when (this) {
        ListActionsPin.OFF -> Icons.Filled.SwipeVertical
        ListActionsPin.TOP -> Icons.Filled.VerticalAlignTop
        ListActionsPin.BOTTOM_LEFT -> Icons.Filled.SouthWest
        ListActionsPin.BOTTOM_RIGHT -> Icons.Filled.SouthEast
        ListActionsPin.SIDE_LEFT -> Icons.Filled.AlignHorizontalLeft
        ListActionsPin.SIDE_RIGHT -> Icons.Filled.AlignHorizontalRight
        ListActionsPin.CORNER -> Icons.AutoMirrored.Filled.PlaylistPlay
    }

/** The Play buttons row: where a page's Play / Shuffle / Queue buttons go as it scrolls. */
@Composable
internal fun ListActionsRow(menu: PanelState) {
    val pin by LocalAppContainer.current.libraryLayoutManager.listActionsPin.collectAsStateWithLifecycle()
    NavRow(
        icon = Icons.Filled.PushPin,
        title = stringResource(R.string.settings_list_actions),
        value = stringResource(pin.label),
        hint = stringResource(R.string.settings_list_actions_hint),
        onClick = { menu.open() },
        modifier = Modifier.menuOrigin(menu),
    )
}

/** The Play buttons row's secondary menu ([SettingsMenu]): each place they can go. */
@Composable
internal fun ListActionsMenu(panel: PanelState, haze: HazeState) {
    val manager = LocalAppContainer.current.libraryLayoutManager
    val current by manager.listActionsPin.collectAsStateWithLifecycle()
    SettingsMenu(panel, haze, title = stringResource(R.string.settings_list_actions)) {
        // The default first, then from the least to the most set apart: with the page, one button, the docks.
        listOf(
            ListActionsPin.TOP, ListActionsPin.OFF, ListActionsPin.CORNER,
            ListActionsPin.BOTTOM_LEFT, ListActionsPin.BOTTOM_RIGHT,
            ListActionsPin.SIDE_LEFT, ListActionsPin.SIDE_RIGHT,
        ).forEach { pin ->
            MenuOption(icon = pin.icon, label = stringResource(pin.label), selected = pin == current, onClick = {
                panel.close()
                manager.setListActionsPin(pin)
            })
        }
    }
}
