package com.example.samsonic.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.model.Playlist
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.settings.SettingsMenu
import com.example.samsonic.ui.theme.OneUiRadius
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

/**
 * Asks before deleting [playlist] (null while none is chosen): a card grown out of what was
 * long-pressed. Its songs stay in the library. [onDeleted] runs once it's gone; an error stays
 * in the card, to try again.
 */
@Composable
internal fun DeletePlaylistMenu(panel: PanelState, haze: HazeState, playlist: Playlist?, onDeleted: () -> Unit) {
    SettingsMenu(
        panel, haze,
        title = stringResource(R.string.library_delete_playlist_title),
        originRadius = OneUiRadius.Art,
    ) {
        val target = playlist ?: return@SettingsMenu
        val repository = LocalAppContainer.current.repository
        val scope = rememberCoroutineScope()
        var deleting by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val failure = stringResource(R.string.library_delete_playlist_error)
        Column(Modifier.padding(horizontal = 24.dp)) {
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
                TextButton(onClick = { panel.close() }, enabled = !deleting) { Text(stringResource(R.string.library_cancel)) }
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
