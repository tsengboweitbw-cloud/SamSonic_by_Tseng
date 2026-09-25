package com.example.samsonic.ui.home

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.R
import com.example.samsonic.model.Album
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.TitledPage
import com.example.samsonic.ui.components.AlbumCard
import com.example.samsonic.ui.components.HorizontalCarousel
import com.example.samsonic.ui.components.OneUiPullToRefresh
import com.example.samsonic.ui.components.SectionHeader

/**
 * Hidden while empty: history shelves until there's play history, and song shelves,
 * which a library without per-song dates or history can't fill.
 */
private fun HomeShelf.hiddenWhenEmpty() = history || kind == ShelfKind.Songs

@Composable
fun HomeScreen(
    onAlbumClick: (Album) -> Unit,
    onShelfClick: (HomeShelf) -> Unit,
    modifier: Modifier = Modifier,
    // Also run when the user pulls to refresh, for what else a refresh resets.
    onRefresh: () -> Unit = {},
    contentPaddingBottom: Dp = 0.dp,
) {
    val viewModel = rememberHomeViewModel()

    TitledPage(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.home_your_library),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        },
    ) { topPadding ->
        StateContent(state = viewModel.state, modifier = Modifier.fillMaxSize(), onRetry = viewModel::retry) { sections ->
            val shown = sections.filter { (shelf, items) -> !items.isEmpty || !shelf.hiddenWhenEmpty() }
            // Pulling down past the top reloads every shelf, re-rolling "Picked For You".
            OneUiPullToRefresh(
                isRefreshing = viewModel.isRefreshing,
                onRefresh = {
                    viewModel.refresh()
                    onRefresh()
                },
                topInset = topPadding,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topPadding, bottom = contentPaddingBottom),
                ) {
                    shown.entries.forEachIndexed { index, (shelf, items) ->
                        item(key = shelf.key) {
                            // The title opens the shelf's full list as its own page.
                            SectionHeader(title = stringResource(shelf.title), onTitleClick = { onShelfClick(shelf) })
                            when (items) {
                                is ShelfItems.Albums -> HorizontalCarousel(items = items.albums, key = { it.id }) { album ->
                                    AlbumCard(album = album, onClick = { onAlbumClick(album) })
                                }
                                is ShelfItems.Songs -> SongRowsCarousel(items.songs)
                            }
                            Spacer(Modifier.height(if (index == shown.size - 1) 24.dp else 20.dp))
                        }
                    }
                }
            }
        }
    }
}
