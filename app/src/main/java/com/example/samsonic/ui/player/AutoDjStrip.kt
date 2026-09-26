package com.example.samsonic.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.data.AutoDjFollow
import com.example.samsonic.data.AutoDjMode
import com.example.samsonic.model.genreNames
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.autodj.AutoDjIcon
import com.example.samsonic.ui.autodj.autoDjModeLabel
import com.example.samsonic.ui.components.ToggleChip

/**
 * Auto DJ at the foot of Up next: its mode, to switch in a tap, and, when it follows
 * the genre of a song of several, which of the playing song's genres to follow.
 * The rest of its settings are in Settings.
 */
@Composable
internal fun AutoDjStrip(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val settings = container.autoDjSettings
    val config by settings.config.collectAsStateWithLifecycle()
    val player = LocalPlayerState.current
    val genres = player.currentSong?.genreNames.orEmpty()

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AutoDjIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.auto_dj_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            AutoDjMode.entries.forEach { mode ->
                ToggleChip(autoDjModeLabel(mode), selected = config.mode == mode, onClick = { settings.setMode(mode) })
            }
        }
        AnimatedVisibility(container.autoDj.isPicking) {
            Text(
                text = stringResource(R.string.auto_dj_picking),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, top = 6.dp),
            )
        }
        AnimatedVisibility(config.mode != AutoDjMode.OFF && AutoDjFollow.GENRE in config.follow && genres.size > 1) {
            Row(
                modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.auto_dj_follow_these),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp),
                )
                genres.forEach { genre ->
                    val followed = genre !in config.ignoredGenres
                    ToggleChip(genre, followed, onClick = { settings.setGenreFollowed(genre, !followed) })
                }
            }
        }
    }
}
