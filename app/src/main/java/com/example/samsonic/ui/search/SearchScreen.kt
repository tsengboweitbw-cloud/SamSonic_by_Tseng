package com.example.samsonic.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.SearchResults
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.components.AlbumCard
import com.example.samsonic.ui.components.ArtistCard
import com.example.samsonic.ui.components.HorizontalCarousel
import com.example.samsonic.ui.components.SectionHeader
import com.example.samsonic.ui.components.SongRow
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    var query by remember { mutableStateOf("") }
    val player = LocalPlayerState.current
    val repository = LocalAppContainer.current.repository

    val results by produceState<SearchResults?>(initialValue = null, key1 = query) {
        if (query.isBlank()) {
            value = null
            return@produceState
        }
        delay(350) // debounce
        value = runCatching { repository.search(query) }.getOrNull()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = contentPaddingBottom),
    ) {
        item {
            Text(
                text = "Search",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("Artists, albums, songs") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        }

        val current = results
        val hasResults = current != null && (current.artists.isNotEmpty() || current.albums.isNotEmpty() || current.songs.isNotEmpty())

        when {
            query.isBlank() -> item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Search your library",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            current == null -> item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            !hasResults -> item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No results for “$query”",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                if (current.artists.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Artists")
                        HorizontalCarousel(items = current.artists) { artist ->
                            ArtistCard(artist = artist, onClick = { onArtistClick(artist) })
                        }
                    }
                }
                if (current.albums.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Albums")
                        HorizontalCarousel(items = current.albums) { album ->
                            AlbumCard(album = album, onClick = { onAlbumClick(album) })
                        }
                    }
                }
                if (current.songs.isNotEmpty()) {
                    item { SectionHeader(title = "Songs") }
                    items(current.songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            isCurrent = player.currentSong?.id == song.id,
                            onClick = { player.play(song, current.songs) },
                        )
                    }
                }
            }
        }
    }
}
