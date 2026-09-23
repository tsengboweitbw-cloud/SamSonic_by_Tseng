package com.example.samsonic.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.model.Album
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.TitledPage
import com.example.samsonic.ui.components.AlbumCard
import com.example.samsonic.ui.components.HorizontalCarousel
import com.example.samsonic.ui.components.OneUiPullToRefresh
import com.example.samsonic.ui.components.SectionHeader

/** Shelves that only make sense once the user has play history; hidden while empty. */
private val historyShelves = setOf(AlbumShelf.RecentlyPlayed, AlbumShelf.MostPlayed)

@Composable
fun HomeScreen(
    onAlbumClick: (Album) -> Unit,
    onShelfClick: (AlbumShelf) -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val viewModel = rememberHomeViewModel()

    TitledPage(
        modifier = modifier,
        title = {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(text = "Welcome back", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Your library", style = MaterialTheme.typography.displaySmall)
            }
        },
    ) { topPadding ->
        StateContent(state = viewModel.state, modifier = Modifier.fillMaxSize(), onRetry = viewModel::retry) { sections ->
            val shown = sections.filter { (shelf, albums) -> albums.isNotEmpty() || shelf !in historyShelves }
            // Pulling down past the top reloads every shelf, re-rolling "Picked For You".
            OneUiPullToRefresh(
                isRefreshing = viewModel.isRefreshing,
                onRefresh = viewModel::refresh,
                topInset = topPadding,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topPadding, bottom = contentPaddingBottom),
                ) {
                    shown.entries.forEachIndexed { index, (shelf, albums) ->
                        item(key = shelf.type) {
                            // The title opens the shelf's full list as its own page.
                            SectionHeader(title = shelf.title, onTitleClick = { onShelfClick(shelf) })
                            HorizontalCarousel(items = albums, key = { it.id }) { album ->
                                AlbumCard(album = album, onClick = { onAlbumClick(album) })
                            }
                            Spacer(Modifier.height(if (index == shown.size - 1) 24.dp else 20.dp))
                        }
                    }
                }
            }
        }
    }
}
