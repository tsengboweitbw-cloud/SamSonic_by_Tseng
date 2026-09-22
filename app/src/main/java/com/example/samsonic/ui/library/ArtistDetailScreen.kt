package com.example.samsonic.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Song
import com.example.samsonic.model.artSeed
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.components.AlbumCard
import com.example.samsonic.ui.components.HorizontalCarousel
import com.example.samsonic.ui.components.MediaArt
import com.example.samsonic.ui.components.SectionHeader
import com.example.samsonic.ui.components.SongRow
import com.example.samsonic.ui.theme.OneUiRadius

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
) {
    val player = LocalPlayerState.current
    val repository = LocalAppContainer.current.repository

    val state by produceState<UiState<ArtistDetail>>(initialValue = UiState.Loading, key1 = artistId) {
        value = UiState.Loading
        value = runCatching {
            val (artist, albums) = repository.getArtist(artistId)
            val topSongs = runCatching { repository.getTopSongs(artist.name) }.getOrDefault(emptyList())
            ArtistDetail(artist, albums, topSongs)
        }.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Couldn't load artist") },
        )
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { if (topSongs.isNotEmpty()) player.play(topSongs.first(), topSongs) },
                                shape = RoundedCornerShape(OneUiRadius.Pill),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.height(52.dp),
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play")
                            }
                            OutlinedButton(
                                onClick = {
                                    val shuffled = topSongs.shuffled()
                                    if (shuffled.isNotEmpty()) player.play(shuffled.first(), shuffled)
                                },
                                shape = RoundedCornerShape(OneUiRadius.Pill),
                                modifier = Modifier.height(52.dp),
                            ) {
                                Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Shuffle")
                            }
                        }
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
                        HorizontalCarousel(items = albums) { album ->
                            AlbumCard(album = album, onClick = { onAlbumClick(album) })
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
