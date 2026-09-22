package com.example.samsonic.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.data.SubsonicRepository
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Genre
import com.example.samsonic.model.Playlist
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.components.AlbumCard
import com.example.samsonic.ui.components.ArtistCard
import com.example.samsonic.ui.components.PlaylistCard

private val tabs = listOf("Artists", "Albums", "Playlists", "Genres")

@Composable
fun LibraryScreen(
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    var selectedTab by remember { mutableIntStateOf(1) }
    val repository = LocalAppContainer.current.repository

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            edgePadding = 20.dp,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }
        when (selectedTab) {
            0 -> ArtistGrid(repository, onArtistClick, contentPaddingBottom)
            1 -> AlbumGrid(repository, onAlbumClick, contentPaddingBottom)
            2 -> PlaylistGrid(repository, onPlaylistClick, contentPaddingBottom)
            else -> GenreList(repository, contentPaddingBottom)
        }
    }
}

@Composable
private fun ArtistGrid(repository: SubsonicRepository, onArtistClick: (Artist) -> Unit, bottomPadding: Dp) {
    val state by produceState<UiState<List<Artist>>>(initialValue = UiState.Loading) {
        value = runCatching { repository.getArtists() }.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Couldn't load artists") },
        )
    }
    StateContent(state = state, modifier = Modifier.fillMaxSize()) { artists ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(artists, key = { it.id }) { artist ->
                ArtistCard(artist = artist, onClick = { onArtistClick(artist) })
            }
        }
    }
}

@Composable
private fun AlbumGrid(repository: SubsonicRepository, onAlbumClick: (Album) -> Unit, bottomPadding: Dp) {
    val state by produceState<UiState<List<Album>>>(initialValue = UiState.Loading) {
        value = runCatching { repository.getAlbumList("alphabeticalByArtist", 500) }.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Couldn't load albums") },
        )
    }
    StateContent(state = state, modifier = Modifier.fillMaxSize()) { albums ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(album = album, onClick = { onAlbumClick(album) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PlaylistGrid(repository: SubsonicRepository, onPlaylistClick: (Playlist) -> Unit, bottomPadding: Dp) {
    val state by produceState<UiState<List<Playlist>>>(initialValue = UiState.Loading) {
        value = runCatching { repository.getPlaylists() }.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Couldn't load playlists") },
        )
    }
    StateContent(state = state, modifier = Modifier.fillMaxSize()) { playlists ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                PlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun GenreList(repository: SubsonicRepository, bottomPadding: Dp) {
    val state by produceState<UiState<List<Genre>>>(initialValue = UiState.Loading) {
        value = runCatching { repository.getGenres() }.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Couldn't load genres") },
        )
    }
    StateContent(state = state, modifier = Modifier.fillMaxSize()) { genres ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(genres.size) { index ->
                val genre = genres[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = genre.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${genre.songCount} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
