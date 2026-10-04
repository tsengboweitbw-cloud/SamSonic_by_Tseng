package com.example.samsonic.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.settings.SettingsMenu
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

/**
 * Asks for a name and makes an empty playlist of it, a card grown out of the button that opened
 * [panel]; [onCreated] runs once it's made. An error stays in the card, to try again.
 */
@Composable
internal fun NewPlaylistMenu(panel: PanelState, haze: HazeState, onCreated: () -> Unit) {
    SettingsMenu(panel, haze, title = stringResource(R.string.library_new_playlist)) {
        val repository = LocalAppContainer.current.repository
        val scope = rememberCoroutineScope()
        var saving by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val failure = stringResource(R.string.library_add_to_playlist_error)
        NewPlaylistForm(
            saving = saving,
            onCancel = { panel.close() },
            onCreate = { name ->
                saving = true
                error = null
                scope.launch {
                    runCatching { repository.createPlaylist(name, emptyList()) }
                        .onSuccess {
                            panel.close()
                            onCreated()
                        }
                        .onFailure { error = it.message ?: failure }
                    saving = false
                }
            },
        )
        error?.let {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
