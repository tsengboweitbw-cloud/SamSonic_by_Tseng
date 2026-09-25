package com.example.samsonic.ui.settings

import android.net.ConnectivityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.R
import com.example.samsonic.data.CoverArtPrefetcher
import com.example.samsonic.data.ImageCacheSettings
import com.example.samsonic.data.PrefetchState
import com.example.samsonic.ui.player.PanelState
import dev.chrisbanes.haze.HazeState

/** Where the cover art cache lives: the phone's storage or an SD card, each with its free space. */
@Composable
internal fun CacheLocationMenu(panel: PanelState, haze: HazeState, settings: ImageCacheSettings) {
    SettingsMenu(panel, haze, title = stringResource(R.string.settings_cache_location)) {
        val context = LocalContext.current
        val current by settings.locationId.collectAsStateWithLifecycle()
        // Composed afresh at every open, so a card put in since shows up.
        val locations = remember { settings.locations() }
        locations.forEach { location ->
            MenuOption(
                icon = if (location.isInternal) Icons.Filled.PhoneAndroid else Icons.Filled.SdCard,
                label = location.label,
                supporting = stringResource(R.string.settings_cache_free, ImageCacheSettings.usageLabel(location.freeBytes)),
                selected = location.id == current,
                onClick = {
                    settings.setLocation(location.id)
                    if (settings.changedSinceStart(settings.maxSizeStep.value, location.id)) showAppliesOnRestartToast(context)
                    panel.close()
                },
            )
        }
        MenuNote(
            if (locations.size == 1) {
                stringResource(R.string.settings_cache_no_sd)
            } else {
                stringResource(R.string.settings_cache_move_note)
            },
        )
    }
}

/**
 * Asks before caching every cover, spelling out that it takes storage and data
 * (and saying so louder on a metered network); [onConfirm] starts it.
 */
@Composable
internal fun CacheAllMenu(panel: PanelState, haze: HazeState, settings: ImageCacheSettings, onConfirm: () -> Unit) {
    SettingsMenu(panel, haze, title = stringResource(R.string.settings_cache_all_title)) {
        val context = LocalContext.current
        val metered = remember {
            context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false
        }
        val limit = ImageCacheSettings.label(settings.activeMaxSizeBytes)
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(R.string.settings_cache_all_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_cache_all_limit, limit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (metered) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_cache_all_metered),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { panel.close() }) { Text(stringResource(R.string.settings_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onConfirm()
                        panel.close()
                    },
                ) { Text(stringResource(R.string.settings_cache_all_confirm)) }
            }
        }
    }
}

/**
 * Asks before emptying the cover art cache, saying how much it frees and that the
 * covers download again as they're shown. A run of [prefetcher] stops too, or it
 * would carry on filling the cache straight back up.
 */
@Composable
internal fun ClearCacheMenu(panel: PanelState, haze: HazeState, usage: CacheUsage, prefetcher: CoverArtPrefetcher) {
    SettingsMenu(panel, haze, title = stringResource(R.string.settings_clear_art_cache_title)) {
        val prefetch by prefetcher.state.collectAsStateWithLifecycle()
        val caching = prefetch is PrefetchState.Gathering || prefetch is PrefetchState.Running
        val used = usage.usedBytes?.let(ImageCacheSettings::usageLabel)
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = if (used != null) {
                    stringResource(R.string.settings_clear_art_cache_body_amount, used)
                } else {
                    stringResource(R.string.settings_clear_art_cache_body_all)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (caching) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_clear_art_cache_running),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { panel.close() }) { Text(stringResource(R.string.settings_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (caching) prefetcher.cancel()
                        usage.clear()
                        panel.close()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.settings_clear)) }
            }
        }
    }
}

@Composable
private fun MenuNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
    )
}
