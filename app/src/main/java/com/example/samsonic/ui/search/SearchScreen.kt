package com.example.samsonic.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.SearchResults
import com.example.samsonic.playback.LocalPlayerState
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
import com.example.samsonic.ui.theme.scrollTopFade
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

private val ListTopPadding = 12.dp


/** True once the list has scrolled further than the title, i.e. the pill is pinned over results. */
private fun LazyListState.isScrolledPast(titleHeight: Int): Boolean =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > titleHeight

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

    val listState = rememberLazyListState()
    // A screen-local haze source, separate from the NavHost's: the pinned search
    // pill sits inside that outer source, so it samples this list instead.
    val listHaze = rememberHazeState()
    val density = LocalDensity.current
    var titleHeight by remember { mutableIntStateOf(0) }
    var barHeight by remember { mutableIntStateOf(0) }

    val results by produceState<SearchResults?>(initialValue = null, key1 = query) {
        if (query.isBlank()) {
            value = null
            return@produceState
        }
        delay(350) // debounce
        val next = runCatching { repository.search(query) }.getOrNull()
        // Scrolled deep into the old results: glide back to just under the
        // pinned pill first, so the new results fade in from their top instead
        // of the list snapping wherever the old scroll position lands.
        if (listState.isScrolledPast(titleHeight)) {
            listState.animateScrollToItem(0, titleHeight)
        }
        value = next
    }

    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // Outside hazeSource, so the pill blurs the list unfaded.
                .scrollTopFade(listState)
                .graphicsLayer()
                .hazeSource(listHaze),
            state = listState,
            contentPadding = PaddingValues(top = ListTopPadding, bottom = contentPaddingBottom),
        ) {
            item(key = "header") {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { titleHeight = it.height }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
                // Holds the search pill's place; the pill itself floats above the list.
                Spacer(modifier = Modifier.height(with(density) { barHeight.toDp() }))
            }

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

        // Rides up with the title, then stays pinned where the title started.
        SearchField(
            query = query,
            onQueryChange = { query = it },
            hazeState = listHaze,
            modifier = Modifier
                .offset {
                    val scrolled = if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset
                    } else {
                        Int.MAX_VALUE
                    }
                    val pinned = ListTopPadding.roundToPx()
                    IntOffset(0, (pinned + titleHeight - scrolled).coerceAtLeast(pinned))
                }
                .onSizeChanged { barHeight = it.height }
                .padding(horizontal = 20.dp),
        )
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
