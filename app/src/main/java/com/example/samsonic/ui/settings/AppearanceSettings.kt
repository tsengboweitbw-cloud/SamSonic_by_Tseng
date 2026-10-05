package com.example.samsonic.ui.settings

//import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Style
//import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.ui.theme.toHexRgb
import com.example.samsonic.ui.common.LocalWindowLayout
import kotlin.math.roundToInt

/** The Appearance group: language, theme, accent, how the chrome stacks and the glass. */
@Composable
internal fun AppearanceSettings(panels: SettingsPanels) {
    val container = LocalAppContainer.current
    val themeMode by container.themeManager.themeMode.collectAsStateWithLifecycle()
    val accentColor by container.themeManager.accentColor.collectAsStateWithLifecycle()
    val albumArtCornerRadius by container.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()
    val stackChrome by container.themeManager.stackChrome.collectAsStateWithLifecycle()
    val railStaysPut by container.themeManager.railStaysPut.collectAsStateWithLifecycle()
    val stackPlayerActions by container.themeManager.stackPlayerActions.collectAsStateWithLifecycle()
    val languageMenu = panels.languageMenu
    val themeMenu = panels.themeMenu
    val accentMenu = panels.accentMenu
    val glassMenu = panels.glassMenu
    SettingsCard {
        LanguageRow(languageMenu)
        NavRow(
            icon = Icons.Filled.DarkMode,
            title = stringResource(R.string.settings_theme),
            value = themeMode.label,
            hint = stringResource(R.string.settings_theme_hint),
            onClick = { themeMenu.open() },
            modifier = Modifier.menuOrigin(themeMenu),
        )
        NavRow(
            icon = Icons.Filled.Palette,
            title = stringResource(R.string.settings_accent_color),
            value = "#${accentColor.toHexRgb()}",
            hint = stringResource(R.string.settings_accent_color_hint),
            onClick = { accentMenu.open() },
            modifier = Modifier.menuOrigin(accentMenu),
        )
        // Only on a phone: wider, the nav bar is a rail at the side, with nothing at
        // the foot to pile the mini player onto. Kept as set, for back on a phone.
        val phoneLayout = !LocalWindowLayout.current.usesRail
        SwitchRow(
            icon = Icons.Filled.Layers,
            title = stringResource(R.string.settings_stack_chrome),
            checked = stackChrome,
            onCheckedChange = container.themeManager::setStackChrome,
            hint = stringResource(
                if (phoneLayout) R.string.settings_stack_chrome_hint else R.string.settings_stack_chrome_phone_only_hint,
            ),
            // Shown as it's turned on: it warns of the clash with One-handed mode.
            hintWhenTurnedOn = true,
            enabled = phoneLayout,
        )
        // Its counterpart on wider screens: whether the rail moves as music starts.
        SwitchRow(
            icon = Icons.Filled.VerticalAlignCenter,
            title = stringResource(R.string.settings_rail_stays_put),
            checked = railStaysPut,
            onCheckedChange = container.themeManager::setRailStaysPut,
            hint = stringResource(
                if (phoneLayout) R.string.settings_rail_stays_put_wide_only_hint else R.string.settings_rail_stays_put_hint,
            ),
            enabled = !phoneLayout,
        )
        // Not in DeX, where Now Playing always has a row of buttons for the mouse.
        val desktop = LocalWindowLayout.current.desktop
        SwitchRow(
            icon = Icons.Filled.Style,
            title = stringResource(R.string.settings_stack_player_actions),
            checked = stackPlayerActions,
            onCheckedChange = container.themeManager::setStackPlayerActions,
            hint = stringResource(
                if (desktop) R.string.settings_stack_player_actions_dex_hint else R.string.settings_stack_player_actions_hint,
            ),
            enabled = !desktop,
        )
        SliderRow(
            icon = Icons.Filled.RoundedCorner,
            title = stringResource(R.string.settings_album_art_roundness),
            hint = stringResource(R.string.settings_album_art_roundness_hint),
            valueLabel = "${albumArtCornerRadius.value.roundToInt()}dp",
            value = albumArtCornerRadius.value,
            valueRange = 0f..48f,
            onValueChange = { container.themeManager.setAlbumArtCornerRadius(it.dp) },
        )
        GlassRow(glassMenu)
    }
}
