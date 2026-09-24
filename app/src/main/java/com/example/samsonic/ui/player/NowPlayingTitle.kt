package com.example.samsonic.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.samsonic.model.Song
import com.example.samsonic.ui.components.pressClickable
import com.example.samsonic.util.formatAudioInfo

/** The song's title, album, artist and audio info; the album and artist open their pages. */
@Composable
internal fun NowPlayingTitle(song: Song, modifier: Modifier = Modifier) {
    val links = LocalPlayerLinks.current
    Column(modifier) {
        Text(
            text = song.title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (song.albumTitle.isNotBlank()) {
            SubLine(
                text = song.albumTitle,
                style = MaterialTheme.typography.bodyMedium,
                onClick = song.albumId?.let { id -> { links.openAlbum(id) } },
            )
        }
        Spacer(Modifier.height(4.dp))
        ArtistNames(
            song = song,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            onOpen = links.openArtist,
        )
        val audioInfo = formatAudioInfo(song)
        if (audioInfo != null) {
            Spacer(Modifier.height(2.dp))
            SubLine(text = audioInfo, style = MaterialTheme.typography.labelSmall, onClick = null)
        }
    }
}

/** A secondary line under the title; tappable (with a press squeeze) when [onClick] is set. */
@Composable
private fun SubLine(text: String, style: TextStyle, onClick: (() -> Unit)?) {
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (onClick != null) Modifier.pressClickable(onClick, pressedScale = 0.95f) else Modifier,
    )
}
