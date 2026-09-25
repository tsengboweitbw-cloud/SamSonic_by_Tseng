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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.R
import com.example.samsonic.data.ImageCacheSettings
import com.example.samsonic.data.MusicCache
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.theme.oneUiRowClickable
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * How much the music cache holds, shared by the Clear row that shows it and the
 * menu that confirms clearing it. Both do disk work, so off the main thread.
 */
@Stable
internal class MusicCacheUsage(private val cache: MusicCache, private val scope: CoroutineScope) {
    /** Null while it's being measured or cleared. */
    var usedBytes by mutableStateOf<Long?>(null)
        private set

    private var clearing = false

    suspend fun refresh() {
        if (clearing) return
        val size = withContext(Dispatchers.IO) { cache.usedBytes() }
        if (!clearing) usedBytes = size
    }

    fun clear() {
        if (clearing) return
        clearing = true
        usedBytes = null
        scope.launch {
            usedBytes = withContext(Dispatchers.IO) {
                cache.clear()
                cache.usedBytes()
            }
            clearing = false
        }
    }
}

@Composable
internal fun rememberMusicCacheUsage(cache: MusicCache): MusicCacheUsage {
    val scope = rememberCoroutineScope()
    return remember(cache) { MusicCacheUsage(cache, scope) }
}

/**
 * The music cache: its size limit (one of [ImageCacheSettings.Steps]), whether songs
 * are saved ahead on mobile data, and a row that empties it once [clearMenu] confirms.
 */
@Composable
internal fun MusicCacheRows(cache: MusicCache, usage: MusicCacheUsage, clearMenu: PanelState) {
    val step by cache.maxSizeStep.collectAsStateWithLifecycle()
    val lastStep = ImageCacheSettings.Steps.lastIndex
    val context = LocalContext.current
    SliderRow(
        icon = Icons.Filled.LibraryMusic,
        title = stringResource(R.string.settings_music_cache),
        valueLabel = ImageCacheSettings.label(ImageCacheSettings.Steps[step]),
        value = step.toFloat(),
        valueRange = 0f..lastStep.toFloat(),
        // The stops between the two ends.
        steps = lastStep - 1,
        onValueChange = { cache.setMaxSizeStep(it.roundToInt()) },
        onValueChangeFinished = { if (cache.maxSizeStep.value != cache.activeStep) showAppliesOnRestartToast(context) },
        hint = stringResource(R.string.settings_music_cache_hint),
    )

    val wifiOnly by cache.prefetchWifiOnly.collectAsStateWithLifecycle()
    SwitchRow(
        icon = Icons.Filled.Wifi,
        title = stringResource(R.string.settings_wifi_only),
        checked = wifiOnly,
        onCheckedChange = cache::setPrefetchWifiOnly,
        hint = if (wifiOnly) {
            stringResource(R.string.settings_wifi_only_hint_on)
        } else {
            stringResource(R.string.settings_wifi_only_hint_off)
        },
    )

    LaunchedEffect(usage) { usage.refresh() }
    val usedBytes = usage.usedBytes
    val hint = stringResource(R.string.settings_clear_music_cache_hint)
    val hintState = rememberRowHint()
    Row(
        modifier = Modifier
            .menuOrigin(clearMenu)
            .fillMaxWidth()
            .hintHold(hintState)
            .oneUiRowClickable(onClick = { if (usedBytes != null) clearMenu.open() }, onLongClick = hintLongPress(hintState, hint))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = rowIconTint(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text = stringResource(R.string.settings_clear_music_cache), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = usedBytes?.let { stringResource(R.string.settings_cache_used, ImageCacheSettings.usageLabel(it)) } ?: "…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RowHint(hintState, hint)
    }
}

/** Asks before emptying the music cache, saying how much it frees and what that costs. */
@Composable
internal fun ClearMusicCacheMenu(panel: PanelState, haze: HazeState, usage: MusicCacheUsage) {
    SettingsMenu(panel, haze, title = stringResource(R.string.settings_clear_music_cache_title)) {
        val used = usage.usedBytes?.let(ImageCacheSettings::usageLabel)
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = if (used != null) {
                    stringResource(R.string.settings_clear_music_cache_body_amount, used)
                } else {
                    stringResource(R.string.settings_clear_music_cache_body_all)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { panel.close() }) { Text(stringResource(R.string.settings_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
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
