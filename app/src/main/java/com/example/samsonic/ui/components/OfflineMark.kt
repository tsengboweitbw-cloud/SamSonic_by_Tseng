package com.example.samsonic.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.data.offline.OfflineSongStatus

/**
 * [content] (a song's subtitle), led by a small mark when the song is kept on the phone for
 * offline: a tick once it is saved, a ring filling as it is being saved, an outline while it
 * waits, and a warning if the server refused it.
 */
@Composable
fun WithOfflineMark(songId: String, content: @Composable () -> Unit) {
    val container = LocalAppContainer.current
    val kept by container.offlineMusic.keptIds.collectAsStateWithLifecycle()
    if (songId !in kept) return content()
    val status by remember(songId) { container.offlineDownloader.statusOf(songId) }
        .collectAsStateWithLifecycle(initialValue = OfflineSongStatus.Saved)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OfflineStatusIcon(status)
        Spacer(Modifier.width(4.dp))
        content()
    }
}

@Composable
private fun OfflineStatusIcon(status: OfflineSongStatus) {
    val size = Modifier.size(14.dp)
    when (status) {
        OfflineSongStatus.Saved -> Icon(
            imageVector = Icons.Filled.OfflinePin,
            contentDescription = stringResource(R.string.offline_mark_description),
            tint = MaterialTheme.colorScheme.primary,
            modifier = size,
        )
        OfflineSongStatus.Waiting -> Icon(
            imageVector = Icons.Outlined.OfflinePin,
            contentDescription = stringResource(R.string.offline_mark_waiting),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = size,
        )
        is OfflineSongStatus.Saving -> {
            val description = stringResource(R.string.offline_mark_saving)
            // Round until the first bytes tell how long the song is.
            if (status.fraction > 0f) {
                CircularProgressIndicator(
                    progress = { status.fraction },
                    modifier = size.then(Modifier.semantics { contentDescription = description }),
                    strokeWidth = 2.dp,
                )
            } else {
                CircularProgressIndicator(
                    modifier = size.then(Modifier.semantics { contentDescription = description }),
                    strokeWidth = 2.dp,
                )
            }
        }
        OfflineSongStatus.Refused -> Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = stringResource(R.string.offline_mark_refused),
            tint = MaterialTheme.colorScheme.error,
            modifier = size,
        )
    }
}
