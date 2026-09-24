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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.data.CoverArtPrefetcher
import com.example.samsonic.data.ImageCacheSettings
import com.example.samsonic.data.PrefetchState
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.theme.oneUiRowClickable
import kotlin.math.roundToInt

// While caching, the usage shown is refreshed every this many covers.
private const val USAGE_REFRESH_EVERY = 50

/**
 * The cover art cache: its size limit (one of [ImageCacheSettings.Steps]), where it
 * lives ([locationMenu]), caching every cover up front ([cacheAllMenu] asks first;
 * only offered with [canCacheAll], i.e. for a server), and a row showing how much
 * it holds ([usage]) that empties it once [clearMenu] confirms.
 */
@Composable
internal fun ImageCacheRows(
    settings: ImageCacheSettings,
    prefetcher: CoverArtPrefetcher,
    locationMenu: PanelState,
    cacheAllMenu: PanelState,
    usage: CacheUsage,
    clearMenu: PanelState,
    canCacheAll: Boolean,
) {
    val step by settings.maxSizeStep.collectAsStateWithLifecycle()
    val locationId by settings.locationId.collectAsStateWithLifecycle()
    val prefetch by prefetcher.state.collectAsStateWithLifecycle()
    val lastStep = ImageCacheSettings.Steps.lastIndex

    SliderRow(
        icon = Icons.Filled.Storage,
        title = "Album art cache",
        valueLabel = ImageCacheSettings.label(ImageCacheSettings.Steps[step]),
        value = step.toFloat(),
        valueRange = 0f..lastStep.toFloat(),
        // The stops between the two ends.
        steps = lastStep - 1,
        onValueChange = { settings.setMaxSizeStep(it.roundToInt()) },
        supportingText = when {
            settings.changedSinceStart(step, locationId) -> "Applies the next time SamSonic starts"
            settings.usingFallback -> "SD card not found, so the cache is on the phone for now"
            else -> null
        },
    )
    val locationLabel = remember(locationId) {
        settings.locations().firstOrNull { it.id == locationId }?.label ?: "SD card (not found)"
    }
    NavRow(
        icon = Icons.Filled.Folder,
        title = "Cache location",
        value = locationLabel,
        onClick = { locationMenu.open() },
        modifier = Modifier.menuOrigin(locationMenu),
    )
    if (canCacheAll) {
        val running = prefetch is PrefetchState.Gathering || prefetch is PrefetchState.Running
        NavRow(
            icon = Icons.Filled.CloudDownload,
            title = if (running) "Stop caching" else "Cache all album art",
            value = prefetch.label,
            onClick = { if (running) prefetcher.cancel() else cacheAllMenu.open() },
            modifier = Modifier.menuOrigin(cacheAllMenu),
        )
    }
    ClearCacheRow(usage, clearMenu, refreshKey = prefetch.usageRefreshKey)
}

private val PrefetchState.label: String
    get() = when (this) {
        PrefetchState.Idle -> ""
        PrefetchState.Gathering -> "Finding covers…"
        is PrefetchState.Running -> "$done / $total"
        is PrefetchState.Finished -> if (failed > 0) "Done, $failed failed" else "Done"
        PrefetchState.Failed -> "Couldn't reach the server"
    }

private val PrefetchState.usageRefreshKey: Any
    get() = if (this is PrefetchState.Running) done / USAGE_REFRESH_EVERY else this

@Composable
private fun ClearCacheRow(usage: CacheUsage, menu: PanelState, refreshKey: Any) {
    LaunchedEffect(usage, refreshKey) { usage.refresh() }
    val usedBytes = usage.usedBytes

    Row(
        modifier = Modifier
            .menuOrigin(menu)
            .fillMaxWidth()
            .oneUiRowClickable { if (usedBytes != null) menu.open() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text = "Clear album art cache", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = usedBytes?.let { "${ImageCacheSettings.usageLabel(it)} used" } ?: "…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
