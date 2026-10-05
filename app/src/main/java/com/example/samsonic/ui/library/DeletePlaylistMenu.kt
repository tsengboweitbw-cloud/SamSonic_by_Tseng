package com.example.samsonic.ui.library

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.Song
import com.example.samsonic.model.isFavourites
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.settings.MenuOption
import com.example.samsonic.ui.settings.SettingsMenu
import com.example.samsonic.ui.theme.OneUiRadius
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

/**
 * What a long press on [playlist] (null while none is chosen) offers: keep its songs for offline
 * (or let them go) and, if [canDelete], delete it, which asks first. A card grown out of what was
 * long-pressed. Its songs stay in the library. [onDeleted] runs once it's gone; an error stays
 * in the card, to try again.
 */
@Composable
internal fun DeletePlaylistMenu(panel: PanelState, haze: HazeState, playlist: Playlist?, canDelete: Boolean, onDeleted: () -> Unit) {
    SettingsMenu(
        panel, haze,
        title = stringResource(R.string.library_more_options),
        originRadius = OneUiRadius.Art,
    ) {
        val target = playlist ?: return@SettingsMenu
        val repository = LocalAppContainer.current.repository
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val resources = LocalResources.current
        var deleting by remember { mutableStateOf(false) }
        var confirming by remember { mutableStateOf(false) }
        var working by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val failure = stringResource(R.string.library_delete_playlist_error)
        val offline = LocalAppContainer.current.offlineMusic
        val kept by offline.keptIds.collectAsStateWithLifecycle()
        var songs by remember { mutableStateOf<List<Song>?>(null) }
        suspend fun fetchSongs(): List<Song> =
            if (target.isFavourites) repository.getLikedSongs() else repository.getPlaylist(target.id).second
        LaunchedEffect(target.id) {
            if (offline.available) songs = runCatching { fetchSongs() }.getOrNull()
        }
        val player = LocalPlayerState.current
        if (!confirming) {
            MenuOption(
                icon = Icons.Filled.SkipNext,
                label = stringResource(R.string.components_play_next),
                selected = false,
                onClick = {
                    player.queueLater(next = true) { fetchSongs() }
                    Toast.makeText(context, resources.getString(R.string.library_playing_next, target.name), Toast.LENGTH_SHORT).show()
                    panel.close()
                },
            )
            MenuOption(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = stringResource(R.string.components_add_to_queue),
                selected = false,
                onClick = {
                    player.queueLater(next = false) { fetchSongs() }
                    Toast.makeText(context, resources.getString(R.string.library_added_to_queue, target.name), Toast.LENGTH_SHORT).show()
                    panel.close()
                },
            )
            if (offline.available) {
                val loaded = songs
                val keeping = loaded != null && loaded.isNotEmpty() && loaded.all { it.id in kept }
                MenuOption(
                    icon = if (keeping) Icons.Filled.CloudOff else Icons.Filled.OfflinePin,
                    label = stringResource(if (keeping) R.string.offline_remove_option else R.string.offline_keep_option),
                    selected = keeping,
                    onClick = {
                        if (!working) {
                            working = true
                            error = null
                            scope.launch {
                                runCatching {
                                    val all = loaded ?: fetchSongs()
                                    if (keeping) offline.remove(all) else offline.keep(all)
                                }.onSuccess {
                                    val message = if (keeping) R.string.offline_removed else R.string.offline_kept
                                    Toast.makeText(context, resources.getString(message, target.name), Toast.LENGTH_SHORT).show()
                                    panel.close()
                                }.onFailure { error = it.message ?: failure }
                                working = false
                            }
                        }
                    },
                )
            }
            if (canDelete) {
                MenuOption(
                    icon = Icons.Filled.Delete,
                    label = stringResource(R.string.library_delete),
                    selected = false,
                    onClick = { if (!working) confirming = true },
                )
            }
            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        } else Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(R.string.library_delete_playlist_body, target.name),
                style = MaterialTheme.typography.bodyMedium,
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirming = false; error = null }, enabled = !deleting) { Text(stringResource(R.string.library_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        error = null
                        scope.launch {
                            runCatching { repository.deletePlaylist(target.id) }
                                .onSuccess {
                                    panel.close()
                                    onDeleted()
                                }
                                .onFailure { error = it.message ?: failure }
                            deleting = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.library_delete)) }
            }
        }
    }
}
