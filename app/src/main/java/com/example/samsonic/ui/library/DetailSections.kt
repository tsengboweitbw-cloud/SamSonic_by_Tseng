package com.example.samsonic.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Song
import com.example.samsonic.playback.PlayerState
import com.example.samsonic.ui.components.AlbumCard
import com.example.samsonic.ui.components.ArtistCard
import com.example.samsonic.ui.components.HorizontalCarousel
import com.example.samsonic.ui.components.SectionHeader
import com.example.samsonic.ui.components.SongRow

// The sections of an artist's or genre's page: a centered header, then shelves
// of albums or artists and runs of songs, each titled and left out while empty.

/** The page's [art], [title] and [subtitle], centered over [actions] (play and shuffle). */
@Composable
internal fun DetailHeader(
    title: String,
    subtitle: String,
    art: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        art()
        Spacer(Modifier.height(16.dp))
        Text(text = title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        actions()
    }
}

/** A titled run of song rows; tapping one plays it within them. */
internal fun LazyListScope.songSection(
    key: String,
    title: String,
    songs: List<Song>,
    player: PlayerState,
    onTitleClick: (() -> Unit)?,
) {
    if (songs.isEmpty()) return
    item(key = "$key:title") { SectionTitle(title, onTitleClick) }
    items(songs, key = { "$key:${it.id}" }) { song ->
        SongRow(
            song = song,
            isCurrent = player.currentSong?.id == song.id,
            liked = player.isLiked(song),
            onToggleLike = { player.toggleLike(song) },
            onClick = { player.play(song, songs) },
        )
    }
}

/** A titled, sideways-scrolling shelf of album cards in [rows] rows, filled column by column. */
internal fun LazyListScope.albumShelf(
    key: String,
    title: String,
    albums: List<Album>,
    onTitleClick: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    rows: Int = 1,
) {
    if (albums.isEmpty()) return
    item(key = key) {
        Column {
            SectionTitle(title, onTitleClick)
            HorizontalCarousel(items = albums.chunked(rows), key = { it.first().id }) { column ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    column.forEach { album -> AlbumCard(album = album, onClick = { onAlbumClick(album) }) }
                }
            }
        }
    }
}

/** A titled, sideways-scrolling shelf of artist cards. */
internal fun LazyListScope.artistShelf(
    key: String,
    title: String,
    artists: List<Artist>,
    onTitleClick: () -> Unit,
    onArtistClick: (Artist) -> Unit,
) {
    if (artists.isEmpty()) return
    item(key = key) {
        Column {
            SectionTitle(title, onTitleClick)
            HorizontalCarousel(items = artists, key = { it.id }) { artist ->
                ArtistCard(artist = artist, onClick = { onArtistClick(artist) })
            }
        }
    }
}

/** A section's title, spaced from the one above; with [onTitleClick] it opens the full list. */
@Composable
private fun SectionTitle(title: String, onTitleClick: (() -> Unit)?) {
    Column {
        Spacer(Modifier.height(16.dp))
        SectionHeader(title = title, onTitleClick = onTitleClick)
    }
}
