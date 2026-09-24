package com.example.samsonic.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.ui.common.ArtKeys
import com.example.samsonic.ui.common.rememberSharedArt
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.artSeed
import com.example.samsonic.ui.theme.oneUiRowClickable

private val RowArtSize = 64.dp

/** An artist as a list row, laid out like [AlbumRow]: round photo, name, album count. */
@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val art = rememberSharedArt(ArtKeys.artist(artist.id), artist, onClick)
    LibraryRow(
        title = artist.name,
        details = "${artist.albumCount} albums",
        onClick = art.onClick,
        modifier = modifier,
    ) {
        MediaArt(
            coverArt = artist.coverArt,
            colorSeed = artist.id.artSeed(),
            size = RowArtSize,
            cornerRadius = RowArtSize / 2,
            icon = false,
            fit = false,
            shadowElevation = 0.dp,
            modifier = art.modifier.clip(CircleShape),
        )
    }
}

/** A playlist as a list row, laid out like [AlbumRow]: cover, name, song count. */
@Composable
fun PlaylistRow(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()
    val art = rememberSharedArt(ArtKeys.playlist(playlist.id), playlist, onClick)
    LibraryRow(
        title = playlist.name,
        details = "${playlist.songCount} songs",
        onClick = art.onClick,
        modifier = modifier,
    ) {
        PlaylistArt(
            playlist = playlist,
            size = RowArtSize,
            cornerRadius = cornerRadius,
            shadowElevation = 0.dp,
            modifier = art.modifier,
        )
    }
}

@Composable
private fun LibraryRow(
    title: String,
    details: String,
    onClick: () -> Unit,
    modifier: Modifier,
    art: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .oneUiRowClickable(onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
