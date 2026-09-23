package com.example.samsonic.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Genre
import com.example.samsonic.model.Playlist
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.TitledPage
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.common.rememberScreenLoad
import com.example.samsonic.ui.components.AlbumCard
import com.example.samsonic.ui.components.ArtistCard
import com.example.samsonic.ui.components.GlassTabBar
import com.example.samsonic.ui.components.PlaylistCard
import kotlinx.coroutines.launch

private val tabs = listOf("Artists", "Albums", "Playlists", "Genres")

/** Space the tab content keeps clear: under the pinned tab bar, and above the floating chrome. */
private data class LibraryPadding(val top: Dp, val bottom: Dp)

@Composable
fun LibraryScreen(
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    // Saveable, so the tab and each tab's scroll position survive a trip to a
    // detail page and back, not just switching between tabs.
    var selectedTab by rememberSaveable { mutableIntStateOf(1) }
    val artistsGrid = rememberLazyGridState()
    val albumsGrid = rememberLazyGridState()
    val playlistsGrid = rememberLazyGridState()
    val genresList = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Each tab loads on its first visit and then keeps its data while on this
    // page, so switching back shows the list at once, right where it was left.
    val visited = remember { mutableStateListOf(selectedTab) }
    val repository = LocalAppContainer.current.repository
    val artists = if (0 in visited) {
        rememberScreenLoad(Unit, errorMessage = "Couldn't load artists") { repository.getArtists() }
    } else UiState.Loading
    val albums = if (1 in visited) {
        rememberScreenLoad(Unit, errorMessage = "Couldn't load albums") {
            repository.getAlbumList("alphabeticalByArtist", 500)
        }
    } else UiState.Loading
    val playlists = if (2 in visited) {
        rememberScreenLoad(Unit, errorMessage = "Couldn't load playlists") { repository.getPlaylists() }
    } else UiState.Loading
    val genres = if (3 in visited) {
        rememberScreenLoad(Unit, errorMessage = "Couldn't load genres") { repository.getGenres() }
    } else UiState.Loading

    fun onTabSelected(tab: Int) {
        if (tab == selectedTab) {
            // Tapping the current tab again: back to the top.
            scope.launch {
                when (tab) {
                    0 -> artistsGrid.animateScrollToItem(0)
                    1 -> albumsGrid.animateScrollToItem(0)
                    2 -> playlistsGrid.animateScrollToItem(0)
                    else -> genresList.animateScrollToItem(0)
                }
            }
            return
        }
        selectedTab = tab
        if (tab !in visited) visited += tab
    }

    // Fixed title, with the tab bar floating as glass right under it.
    TitledPage(
        modifier = modifier,
        title = {
            Text(
                text = "Library",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        },
        bar = { hazeState ->
            GlassTabBar(
                labels = tabs,
                selectedIndex = selectedTab,
                onSelect = ::onTabSelected,
                hazeState = hazeState,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        },
    ) { topPadding ->
        val padding = LibraryPadding(top = topPadding, bottom = contentPaddingBottom)
        when (selectedTab) {
            0 -> ArtistGrid(artists, artistsGrid, onArtistClick, padding)
            1 -> AlbumGrid(albums, albumsGrid, onAlbumClick, padding)
            2 -> PlaylistGrid(playlists, playlistsGrid, onPlaylistClick, padding)
            else -> GenreList(genres, genresList, padding)
        }
    }
}

@Composable
private fun ArtistGrid(state: UiState<List<Artist>>, gridState: LazyGridState, onArtistClick: (Artist) -> Unit, padding: LibraryPadding) {
    StateContent(state = state, modifier = Modifier.fillMaxSize()) { artists ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.top, bottom = 16.dp + padding.bottom),
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
private fun AlbumGrid(state: UiState<List<Album>>, gridState: LazyGridState, onAlbumClick: (Album) -> Unit, padding: LibraryPadding) {
    StateContent(state = state, modifier = Modifier.fillMaxSize()) { albums ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.top, bottom = 16.dp + padding.bottom),
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
private fun PlaylistGrid(state: UiState<List<Playlist>>, gridState: LazyGridState, onPlaylistClick: (Playlist) -> Unit, padding: LibraryPadding) {
    StateContent(state = state, modifier = Modifier.fillMaxSize()) { playlists ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.top, bottom = 16.dp + padding.bottom),
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
private fun GenreList(state: UiState<List<Genre>>, listState: LazyListState, padding: LibraryPadding) {
    StateContent(state = state, modifier = Modifier.fillMaxSize()) { genres ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = padding.top, bottom = padding.bottom),
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
