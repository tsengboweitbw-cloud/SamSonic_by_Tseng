package com.example.samsonic.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.R
import com.example.samsonic.data.CoverArtPrefetcher
import com.example.samsonic.data.ImageCacheSettings
import com.example.samsonic.data.PrefetchState
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.theme.oneUiRowClickable
import kotlin.math.roundToInt

// While caching, the usage shown is refreshed every this many covers.
private const val USAGE_REFRESH_EVERY = 50

/**
 * Where the music and cover art caches live; [menu] picks it. Says so when the chosen
 * SD card is missing and the phone's storage is standing in.
 */
@Composable
internal fun CacheLocationRow(settings: ImageCacheSettings, menu: PanelState) {
    val locationId by settings.locationId.collectAsStateWithLifecycle()
    val missingLabel = stringResource(R.string.settings_cache_location_missing)
    val locationLabel = remember(locationId, missingLabel) {
        settings.locations().firstOrNull { it.id == locationId }?.label ?: missingLabel
    }
    NavRow(
        icon = Icons.Filled.Folder,
        title = stringResource(R.string.settings_cache_location),
        value = locationLabel,
        hint = if (settings.usingFallback) {
            stringResource(R.string.settings_cache_location_hint_fallback)
        } else {
            stringResource(R.string.settings_cache_location_hint)
        },
        onClick = { menu.open() },
        modifier = Modifier.menuOrigin(menu),
    )
}

/**
 * The cover art cache: its size limit (one of [ImageCacheSettings.Steps]), caching
 * every cover up front ([cacheAllMenu] asks first; only offered with [canCacheAll],
 * i.e. for a server), and a row showing how much it holds ([usage]) that empties it
 * once [clearMenu] confirms.
 */
@Composable
internal fun ImageCacheRows(
    settings: ImageCacheSettings,
    prefetcher: CoverArtPrefetcher,
    cacheAllMenu: PanelState,
    usage: CacheUsage,
    clearMenu: PanelState,
    canCacheAll: Boolean,
) {
    val step by settings.maxSizeStep.collectAsStateWithLifecycle()
    val locationId by settings.locationId.collectAsStateWithLifecycle()
    val prefetch by prefetcher.state.collectAsStateWithLifecycle()
    val lastStep = ImageCacheSettings.Steps.lastIndex
    val context = LocalContext.current

    SliderRow(
        icon = Icons.Filled.Storage,
        title = stringResource(R.string.settings_album_art_cache),
        valueLabel = ImageCacheSettings.label(ImageCacheSettings.Steps[step]),
        value = step.toFloat(),
        valueRange = 0f..lastStep.toFloat(),
        // The stops between the two ends.
        steps = lastStep - 1,
        onValueChange = { settings.setMaxSizeStep(it.roundToInt()) },
        onValueChangeFinished = {
            if (settings.changedSinceStart(settings.maxSizeStep.value, locationId)) showAppliesOnRestartToast(context)
        },
        hint = stringResource(R.string.settings_album_art_cache_hint),
    )
    if (canCacheAll) {
        val running = prefetch is PrefetchState.Gathering || prefetch is PrefetchState.Running
        NavRow(
            icon = Icons.Filled.CloudDownload,
            title = if (running) stringResource(R.string.settings_stop_caching) else stringResource(R.string.settings_cache_all),
            value = prefetch.label,
            hint = if (running) stringResource(R.string.settings_stop_caching_hint) else stringResource(R.string.settings_cache_all_hint),
            onClick = { if (running) prefetcher.cancel() else cacheAllMenu.open() },
            modifier = Modifier.menuOrigin(cacheAllMenu),
        )
    }
    ClearCacheRow(usage, clearMenu, refreshKey = prefetch.usageRefreshKey)
}

private val PrefetchState.label: String
    @Composable get() = when (this) {
        PrefetchState.Idle -> ""
        PrefetchState.Gathering -> stringResource(R.string.settings_prefetch_gathering)
        is PrefetchState.Running -> "$done / $total"
        is PrefetchState.Finished -> if (failed > 0) stringResource(R.string.settings_prefetch_done_failed, failed) else stringResource(R.string.settings_prefetch_done)
        PrefetchState.Failed -> stringResource(R.string.settings_prefetch_failed)
    }

private val PrefetchState.usageRefreshKey: Any
    get() = if (this is PrefetchState.Running) done / USAGE_REFRESH_EVERY else this

@Composable
private fun ClearCacheRow(usage: CacheUsage, menu: PanelState, refreshKey: Any) {
    LaunchedEffect(usage, refreshKey) { usage.refresh() }
    val usedBytes = usage.usedBytes
    val hint = stringResource(R.string.settings_clear_art_cache_hint)
    val hintState = rememberRowHint()

    Row(
        modifier = Modifier
            .menuOrigin(menu)
            .fillMaxWidth()
            .hintHold(hintState)
            .oneUiRowClickable(onClick = { if (usedBytes != null) menu.open() }, onLongClick = hintLongPress(hintState, hint))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = rowIconTint(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text = stringResource(R.string.settings_clear_art_cache), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = usedBytes?.let { stringResource(R.string.settings_cache_used, ImageCacheSettings.usageLabel(it)) } ?: "…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RowHint(hintState, hint)
    }
}
