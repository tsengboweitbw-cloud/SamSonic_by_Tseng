package com.example.samsonic.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.R
import com.example.samsonic.data.ImageCacheSettings
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.theme.oneUiRowClickable
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.ui.res.pluralStringResource
import com.example.samsonic.data.offline.OfflineDownloader
import com.example.samsonic.data.offline.OfflineStore
import com.example.samsonic.data.offline.OfflineOnlySetting

/** What the songs kept for offline take, measured off the main thread, for the Offline music row. */
@Stable
internal class OfflineUsage(private val downloader: OfflineDownloader) {
    /** Null while it's being measured. */
    var savedBytes by mutableStateOf<Long?>(null)
        private set

    suspend fun refresh() {
        savedBytes = withContext(Dispatchers.IO) { downloader.savedBytes() }
    }
}

/** The songs kept for offline: how many and how much they take, how far saving has got, and a way to remove them. */
@Composable
internal fun OfflineMusicRow(store: OfflineStore, downloader: OfflineDownloader, removeMenu: PanelState, usage: OfflineUsage) {
    val entries by store.entries.collectAsStateWithLifecycle()
    val progress by downloader.progress.collectAsStateWithLifecycle()
    // Measured again as songs come in.
    LaunchedEffect(usage, entries.size, progress?.done) { usage.refresh() }
    val bytes = usage.savedBytes
    val hint = stringResource(R.string.settings_offline_music_hint)
    val hintState = rememberRowHint()
    val summary = when {
        progress != null -> stringResource(R.string.settings_offline_music_saving, progress?.done?.plus(1) ?: 0, progress?.total ?: 0)
        entries.isEmpty() -> stringResource(R.string.settings_offline_music_empty)
        else -> pluralStringResource(R.plurals.library_song_count, entries.size, entries.size) +
            (bytes?.let { " · " + ImageCacheSettings.usageLabel(it) } ?: "")
    }
    Row(
        modifier = Modifier
            .menuOrigin(removeMenu)
            .fillMaxWidth()
            .hintHold(hintState)
            .oneUiRowClickable(onClick = { if (entries.isNotEmpty()) removeMenu.open() }, onLongClick = hintLongPress(hintState, hint))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.DownloadForOffline, contentDescription = null, tint = rowIconTint(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text = stringResource(R.string.settings_offline_music), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RowHint(hintState, hint)
    }
}

/** Shown when the server refused some songs kept for offline: a tap asks it for them again. */
@Composable
internal fun OfflineRefusedRow(store: OfflineStore, downloader: OfflineDownloader) {
    val refused by downloader.refusedIds.collectAsStateWithLifecycle()
    val entries by store.entries.collectAsStateWithLifecycle()
    // Only those still kept.
    val count = entries.count { it.song.id in refused }
    if (count == 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .oneUiRowClickable(onClick = { downloader.retry() })
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text = stringResource(R.string.settings_offline_refused), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = pluralStringResource(R.plurals.settings_offline_refused_summary, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Asks before removing every song kept for offline from the phone. */
@Composable
internal fun RemoveOfflineMusicMenu(panel: PanelState, haze: HazeState, store: OfflineStore, downloader: OfflineDownloader) {
    val scope = rememberCoroutineScope()
    SettingsMenu(panel, haze, title = stringResource(R.string.settings_offline_remove_title)) {
        val count by store.entries.collectAsStateWithLifecycle()
        val songs = count.size
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = pluralStringResource(R.plurals.settings_offline_remove_body, songs, songs),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { panel.close() }) { Text(stringResource(R.string.settings_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        downloader.cancel()
                        scope.launch { store.clear() }
                        panel.close()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.settings_offline_remove)) }
            }
        }
    }
}

/** The switch for showing only the songs kept for offline, in every page and list. */
@Composable
internal fun OfflineOnlyRow(setting: OfflineOnlySetting) {
    val on by setting.enabled.collectAsStateWithLifecycle()
    SwitchRow(
        icon = Icons.Filled.CloudOff,
        title = stringResource(R.string.settings_offline_only),
        checked = on,
        onCheckedChange = setting::set,
        hint = stringResource(R.string.settings_offline_only_hint),
    )
}
