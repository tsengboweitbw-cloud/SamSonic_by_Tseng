package com.example.samsonic.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Song
import com.example.samsonic.model.artSeed
import com.example.samsonic.playback.PlayerState
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.rememberScreenLoad
import com.example.samsonic.ui.components.AlbumCard
import com.example.samsonic.ui.components.BackButtonClearance
import com.example.samsonic.ui.components.GlassBackButton
import com.example.samsonic.ui.components.HorizontalCarousel
import com.example.samsonic.ui.components.MediaArt
import com.example.samsonic.ui.components.PlayShuffleButtons
import com.example.samsonic.ui.components.SectionHeader
import com.example.samsonic.ui.components.SongRow
import com.example.samsonic.ui.components.backButtonHazeSource
import com.example.samsonic.ui.theme.scrollTopFade
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** How many of the artist's songs their page lists; the section's title opens them all. */
private const val SONGS_PREVIEW = 10

private data class ArtistDetail(
    val artist: Artist,
    val albums: List<Album>,
    val topSongs: List<Song>,
    val songs: List<Song>,
    val appearsOn: List<Album>,
)

@Composable
fun ArtistDetailScreen(
    artistId: String,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAllAlbumsClick: () -> Unit,
    onAllSongsClick: () -> Unit,
    onAppearsOnClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val player = LocalPlayerState.current
    val repository = LocalAppContainer.current.repository

    val state = rememberScreenLoad(artistId, errorMessage = "Couldn't load artist") {
        val (artist, albums) = repository.getArtist(artistId)
        coroutineScope {
            val topSongs = async { runCatching { repository.getTopSongs(artist.name) }.getOrDefault(emptyList()) }
            // One search for their songs feeds both the Songs and the Appears on sections.
            val songsBy = runCatching { repository.getSongsBy(artist) }.getOrDefault(emptyList())
            val songs = async {
                runCatching { repository.getArtistSongs(artist, albums, SONGS_PREVIEW, songsBy) }.getOrDefault(emptyList())
            }
            val appearsOn = async { runCatching { repository.getAppearsOn(artist, albums, songsBy) }.getOrDefault(emptyList()) }
            ArtistDetail(artist, albums, topSongs.await(), songs.await(), appearsOn.await())
        }
    }

    val listState = rememberLazyListState()
    val backHaze = rememberHazeState()
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        StateContent(state = state, modifier = Modifier.fillMaxSize()) { detail ->
            val (artist, albums, topSongs, songs, appearsOn) = detail
            LazyColumn(
                modifier = Modifier.fillMaxSize().scrollTopFade(listState).backButtonHazeSource(backHaze),
                state = listState,
                contentPadding = PaddingValues(top = BackButtonClearance, bottom = contentPaddingBottom),
            ) {
                item(key = "header") {
                    ArtistHeader(artist) {
                        // Plays everything, not just the songs listed here.
                        PlayShuffleButtons(key = artist.id, loadSongs = { repository.getArtistSongs(artist, albums) })
                    }
                }
                songSection("popular", "Popular", topSongs, player, onTitleClick = null)
                if (albums.isNotEmpty()) {
                    item(key = "albums") {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            SectionHeader(title = "Albums", onTitleClick = onAllAlbumsClick)
                            // Two rows, filled column by column, scrolling together.
                            HorizontalCarousel(items = albums.chunked(2), key = { it.first().id }) { column ->
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    column.forEach { album -> AlbumCard(album = album, onClick = { onAlbumClick(album) }) }
                                }
                            }
                        }
                    }
                }
                songSection("songs", "Songs", songs, player, onTitleClick = onAllSongsClick)
                if (appearsOn.isNotEmpty()) {
                    item(key = "appearsOn") {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            SectionHeader(title = "Appears on", onTitleClick = onAppearsOnClick)
                            HorizontalCarousel(items = appearsOn, key = { it.id }) { album ->
                                AlbumCard(album = album, onClick = { onAlbumClick(album) })
                            }
                        }
                    }
                }
                item(key = "end") { Spacer(Modifier.height(24.dp)) }
            }
        }
        // Floats over the list: rows scroll up under it and fade out at the status bar.
        GlassBackButton(onClick = onBack, hazeState = backHaze, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    }
}

/** The artist's picture, name and album count over [actions]. */
@Composable
private fun ArtistHeader(artist: Artist, actions: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MediaArt(
            coverArt = artist.coverArt,
            colorSeed = artist.id.artSeed(),
            size = 140.dp,
            cornerRadius = 70.dp,
            icon = false,
            fit = false,
            modifier = Modifier.clip(CircleShape),
        )
        Spacer(Modifier.height(16.dp))
        Text(text = artist.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${artist.albumCount} albums",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        actions()
    }
}

/** A titled run of song rows, left out while [songs] is empty; tapping one plays it within them. */
private fun LazyListScope.songSection(
    key: String,
    title: String,
    songs: List<Song>,
    player: PlayerState,
    onTitleClick: (() -> Unit)?,
) {
    if (songs.isEmpty()) return
    item(key = "$key:title") {
        Column {
            Spacer(Modifier.height(16.dp))
            SectionHeader(title = title, onTitleClick = onTitleClick)
        }
    }
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
