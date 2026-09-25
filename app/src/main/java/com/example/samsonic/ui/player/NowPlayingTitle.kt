package com.example.samsonic.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.samsonic.model.Song
import com.example.samsonic.ui.components.pressClickable

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
            Spacer(Modifier.height(6.dp))
            SubLine(
                text = song.albumTitle,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                onClick = song.albumId?.let { id -> { links.openAlbum(id) } },
            )
        }
        Spacer(Modifier.height(8.dp))
        ArtistNames(
            song = song,
            // The artist takes the accent, so it stands out under the title and reads as tappable.
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            onOpen = links.openArtist,
        )
        // Always laid out, and keeping the last details until the next song's arrive
        // (blank only before the first), so nothing blinks or shifts as a song loads.
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { NowPlayingInfoRows(song) }
    }
}

/** A secondary line under the title; tappable (with a press squeeze) when [onClick] is set. */
@Composable
private fun SubLine(
    text: String,
    style: TextStyle,
    onClick: (() -> Unit)?,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (onClick != null) Modifier.pressClickable(onClick, pressedScale = 0.95f) else Modifier,
    )
}
