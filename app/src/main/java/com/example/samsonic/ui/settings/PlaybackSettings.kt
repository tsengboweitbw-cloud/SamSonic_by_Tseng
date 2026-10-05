package com.example.samsonic.ui.settings

//import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Waves
//import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.data.AutoDjMode
import com.example.samsonic.ui.autodj.AutoDjIcon
import com.example.samsonic.ui.autodj.autoDjModeLabel
import com.example.samsonic.playback.LocalPlayerState

/** The Playback group: the sleep timer, Auto DJ, the player's behaviour, the USB DAC driver and the format shown on songs. */
@Composable
internal fun PlaybackSettings(panels: SettingsPanels) {
    val container = LocalAppContainer.current
    val player = LocalPlayerState.current
    val likesEnabled by container.themeManager.likesEnabled.collectAsStateWithLifecycle()
    val autoDjConfig by container.autoDjSettings.config.collectAsStateWithLifecycle()
    val usbDriver by container.usbDacs.enabled.collectAsStateWithLifecycle()
    val nativeDsd by container.usbDacs.nativeDsd.collectAsStateWithLifecycle()
    val audioFormatDisplay by container.themeManager.audioFormatDisplay.collectAsStateWithLifecycle()
    val autoDjMenu = panels.autoDjMenu
    val audioFormatMenu = panels.audioFormatMenu
    SettingsCard {
        SleepTimerRow(player)
        NavRow(
            icon = AutoDjIcon,
            title = stringResource(R.string.auto_dj_title),
            value = autoDjConfig.mode.let { mode ->
                val label = autoDjModeLabel(mode)
                val filters = autoDjConfig.filters.count
                if (mode == AutoDjMode.OFF || filters == 0) label
                else pluralStringResource(R.plurals.auto_dj_filter_count, filters, label, filters)
            },
            hint = stringResource(R.string.auto_dj_hint),
            onClick = { autoDjMenu.open() },
            modifier = Modifier.menuOrigin(autoDjMenu),
        )
        SwitchRow(
            icon = Icons.Filled.Favorite,
            title = stringResource(R.string.settings_like_button),
            checked = likesEnabled,
            onCheckedChange = container.themeManager::setLikesEnabled,
            hint = stringResource(R.string.settings_like_button_hint),
        )
        // The mini player's swipes, with the rest of how the player behaves.
        SwipeGestureRows(container.themeManager)
        SwitchRow(
            icon = Icons.Filled.Usb,
            title = stringResource(R.string.settings_usb_driver),
            checked = usbDriver,
            onCheckedChange = container.usbDacs::setEnabled,
            hint = stringResource(R.string.settings_usb_driver_hint),
        )
        SwitchRow(
            icon = Icons.Filled.Waves,
            title = stringResource(R.string.settings_usb_native_dsd),
            checked = nativeDsd,
            onCheckedChange = container.usbDacs::setNativeDsd,
            hint = stringResource(
                if (usbDriver) R.string.settings_usb_native_dsd_hint else R.string.settings_usb_native_dsd_needs_driver_hint,
            ),
            enabled = usbDriver,
        )
        // How every song list shows each song's format, or not at all.
        NavRow(
            icon = Icons.Filled.GraphicEq,
            title = stringResource(R.string.settings_audio_format),
            value = audioFormatDisplay.label,
            hint = stringResource(R.string.settings_audio_format_hint),
            onClick = { audioFormatMenu.open() },
            modifier = Modifier.menuOrigin(audioFormatMenu),
        )
    }
}
