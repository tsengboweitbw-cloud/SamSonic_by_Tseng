package com.example.samsonic.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.common.TitledPage
import com.example.samsonic.ui.components.AlbumCard
import com.example.samsonic.ui.components.ArtistCard
import com.example.samsonic.ui.components.HorizontalCarousel
import com.example.samsonic.ui.components.ListItemFade
import com.example.samsonic.ui.components.ListItemMove
import com.example.samsonic.ui.components.SectionHeader
import com.example.samsonic.ui.components.SongRow
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

private fun LazyListState.isScrolled(): Boolean = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

@Composable
fun SearchScreen(
    session: SearchSession,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val query = session.query
    val player = LocalPlayerState.current
    val repository = LocalAppContainer.current.repository

    val listState = rememberLazyListState()

    LaunchedEffect(query) {
        if (query.isBlank()) {
            session.show(query, null)
            return@LaunchedEffect
        }
        // Back from a page opened from these results: they're already here.
        if (session.resultsQuery == query) return@LaunchedEffect
        delay(350) // debounce
        val next = runCatching { repository.search(query) }.getOrNull()
        // Scrolled into the old results: glide back to the top first, so the new
        // results fade in from their top instead of the list snapping wherever
        // the old scroll position lands.
        if (listState.isScrolled()) listState.animateScrollToItem(0)
        session.show(query, next)
    }
    val results = session.results

    // Fixed title, with the search pill floating as glass right under it.
    TitledPage(
        modifier = modifier,
        title = {
            Text(
                text = "Search",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        },
        bar = { hazeState ->
            SearchField(
                query = query,
                onQueryChange = { session.query = it },
                hazeState = hazeState,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        },
    ) { topPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = topPadding, bottom = contentPaddingBottom),
        ) {
            val current = results
            val hasResults = current != null && (current.artists.isNotEmpty() || current.albums.isNotEmpty() || current.songs.isNotEmpty())

            // Every row below is keyed and animated, so a new query's results
            // cross-fade in and surviving rows slide to their new spot.
            when {
                query.isBlank() -> item(key = "hint") {
                    Box(modifier = Modifier.animateItem(ListItemFade, ListItemMove, ListItemFade).fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Search your library",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                current == null -> item(key = "loading") {
                    Box(modifier = Modifier.animateItem(ListItemFade, ListItemMove, ListItemFade).fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                !hasResults -> item(key = "empty") {
                    Box(modifier = Modifier.animateItem(ListItemFade, ListItemMove, ListItemFade).fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No results for “$query”",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    if (current.artists.isNotEmpty()) {
                        // The section stays put; HorizontalCarousel fades its cards one by one.
                        item(key = "artists") {
                            Column(modifier = Modifier.animateItem(ListItemFade, ListItemMove, ListItemFade)) {
                                SectionHeader(title = "Artists")
                                HorizontalCarousel(items = current.artists, key = { it.id }) { artist ->
                                    ArtistCard(artist = artist, onClick = { onArtistClick(artist) })
                                }
                            }
                        }
                    }
                    if (current.albums.isNotEmpty()) {
                        item(key = "albums") {
                            Column(modifier = Modifier.animateItem(ListItemFade, ListItemMove, ListItemFade)) {
                                SectionHeader(title = "Albums")
                                HorizontalCarousel(items = current.albums, key = { it.id }) { album ->
                                    AlbumCard(album = album, onClick = { onAlbumClick(album) })
                                }
                            }
                        }
                    }
                    if (current.songs.isNotEmpty()) {
                        item(key = "songsHeader") {
                            SectionHeader(title = "Songs", modifier = Modifier.animateItem(ListItemFade, ListItemMove, ListItemFade))
                        }
                        items(current.songs, key = { "song:" + it.id }) { song ->
                            SongRow(
                                song = song,
                                isCurrent = player.currentSong?.id == song.id,
                                onClick = { player.play(song, current.songs) },
                                modifier = Modifier.animateItem(ListItemFade, ListItemMove, ListItemFade),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    // Same frosted pill as the floating nav bar.
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(OneUiRadius.Pill),
                hazeState = hazeState,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = GlassAlpha.Nav,
            ),
        placeholder = { Text("Artists, albums, songs") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(OneUiRadius.Pill),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
        ),
    )
}
