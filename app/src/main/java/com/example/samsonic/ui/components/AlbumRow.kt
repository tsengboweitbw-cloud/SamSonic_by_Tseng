package com.example.samsonic.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.ui.common.ArtKeys
import com.example.samsonic.ui.common.rememberSharedArt
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.model.Album
import com.example.samsonic.model.artSeed
import com.example.samsonic.ui.library.rememberAddToPlaylistLongPress
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.OneUiRow

/**
 * An album as a list row: cover, title, and "artist · year · N songs" underneath.
 * With [swipeActions] it swipes to play next or queue the album, as song rows do;
 * off where sideways swipes already mean something else, like the Library's tabs.
 */
@Composable
fun AlbumRow(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    swipeActions: Boolean = true,
) {
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()
    val art = rememberSharedArt(ArtKeys.album(album.id), album, onClick)
    val details = listOfNotNull(
        album.artistName,
        album.year?.toString(),
        if (album.trackCount > 0) pluralStringResource(R.plurals.components_song_count, album.trackCount, album.trackCount) else null,
    ).joinToString(" · ")

    // The menu grows out of the whole row, as it does for a song row.
    val addToPlaylist = rememberAddToPlaylistLongPress(album, originRadius = OneUiRadius.Art)

    val row: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(OneUiRow.Shape)
                .then(addToPlaylist.origin)
                .combinedClickable(
                    onClick = art.onClick,
                    onLongClick = addToPlaylist.onLongClick,
                    onLongClickLabel = addToPlaylist.label,
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaArt(
                coverArt = album.coverArt,
                colorSeed = album.id.artSeed(),
                size = 64.dp,
                cornerRadius = cornerRadius,
                shadowElevation = 0.dp,
                modifier = art.modifier,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = album.title,
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
    // The swipes inset the row themselves; without them it's inset here to match.
    if (swipeActions) {
        AlbumSwipeActions(album = album, modifier = modifier, content = row)
    } else {
        Box(modifier.padding(OneUiRow.Inset)) { row() }
    }
}
