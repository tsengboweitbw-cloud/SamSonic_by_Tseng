package com.example.samsonic.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R

/** [content] (a song's subtitle), led by a small mark when the song is kept on the phone for offline. */
@Composable
fun WithOfflineMark(songId: String, content: @Composable () -> Unit) {
    val kept by LocalAppContainer.current.offlineMusic.keptIds.collectAsStateWithLifecycle()
    if (songId !in kept) return content()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.OfflinePin,
            contentDescription = stringResource(R.string.offline_mark_description),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        content()
    }
}
