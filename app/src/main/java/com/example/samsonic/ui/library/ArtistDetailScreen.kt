package com.example.samsonic.ui.library

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.common.rememberScreenLoad
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Song
import com.example.samsonic.model.artSeed
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.components.BackButtonClearance
import com.example.samsonic.ui.components.GlassBackButton
import com.example.samsonic.ui.components.AlbumCard
import com.example.samsonic.ui.components.backButtonHazeSource
import com.example.samsonic.ui.components.HorizontalCarousel
import com.example.samsonic.ui.components.MediaArt
import com.example.samsonic.ui.components.SectionHeader
import com.example.samsonic.ui.components.PlayShuffleButtons
import com.example.samsonic.ui.components.SongRow
import com.example.samsonic.ui.theme.scrollTopFade
import dev.chrisbanes.haze.rememberHazeState

private data class ArtistDetail(
    val artist: Artist,
    val albums: List<Album>,
    val topSongs: List<Song>,
)

@Composable
fun ArtistDetailScreen(
    artistId: String,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val player = LocalPlayerState.current
    val repository = LocalAppContainer.current.repository

    val state = rememberScreenLoad(artistId, errorMessage = "Couldn't load artist") {
        val (artist, albums) = repository.getArtist(artistId)
        val topSongs = runCatching { repository.getTopSongs(artist.name) }.getOrDefault(emptyList())
        ArtistDetail(artist, albums, topSongs)
    }

    val listState = rememberLazyListState()
    val backHaze = rememberHazeState()
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().scrollTopFade(listState).backButtonHazeSource(backHaze),
            state = listState,
            contentPadding = PaddingValues(top = BackButtonClearance, bottom = contentPaddingBottom),
        ) {
            item {
                StateContent(state = state) { detail ->
                    val (artist, albums, topSongs) = detail
                    Column {
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
                            PlayShuffleButtons(songs = topSongs)
                        }
                        if (topSongs.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionHeader(title = "Popular")
                            topSongs.forEach { song ->
                                SongRow(
                                    song = song,
                                    isCurrent = player.currentSong?.id == song.id,
                                    liked = player.isLiked(song),
                                    onToggleLike = { player.toggleLike(song) },
                                    onClick = { player.play(song, topSongs) },
                                )
                            }
                        }
                        if (albums.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionHeader(title = "Albums")
                            HorizontalCarousel(items = albums, key = { it.id }) { album ->
                                AlbumCard(album = album, onClick = { onAlbumClick(album) })
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
        // Floats over the list: rows scroll up under it and fade out at the status bar.
        GlassBackButton(onClick = onBack, hazeState = backHaze, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    }
}
