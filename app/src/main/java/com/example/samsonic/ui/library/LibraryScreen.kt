package com.example.samsonic.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Person
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.data.LibrarySection
import com.example.samsonic.data.LibraryViewMode
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Genre
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.favouritesPlaylist
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.PageTitle
import com.example.samsonic.ui.common.TitledPage
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.common.ScreenRefresh
import com.example.samsonic.ui.common.rememberScreenLoad
import com.example.samsonic.ui.components.AlbumCard
import com.example.samsonic.ui.components.AlbumRow
import com.example.samsonic.ui.components.ArtistCard
import com.example.samsonic.ui.components.ArtistRow
import com.example.samsonic.ui.components.GlassTabBar
import com.example.samsonic.ui.components.OneUiPullToRefresh
import com.example.samsonic.ui.components.GlassTabBarSize
import com.example.samsonic.ui.components.PlaylistCard
import com.example.samsonic.ui.components.PlaylistRow
import com.example.samsonic.ui.theme.oneUiRowClickable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val tabs = listOf(R.string.library_artists, R.string.library_albums, R.string.library_playlists, R.string.library_genres)
private val tabIcons = listOf(
    Icons.Filled.Person,
    Icons.Filled.Album,
    Icons.AutoMirrored.Filled.QueueMusic,
    Icons.Filled.Category,
)

/** The tab's user-adjustable view, by pager page; Genres (null) is always a list. */
private fun sectionOf(page: Int): LibrarySection? = LibrarySection.Tabs.getOrNull(page)

@Composable
fun LibraryScreen(
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onGenreClick: (Genre) -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    // Swipe between tabs. Pager state is saveable, so the tab and each tab's
    // scroll position survive a trip to a detail page and back.
    val pagerState = rememberPagerState(initialPage = 1) { tabs.size }
    val artistsGrid = rememberLazyGridState()
    val albumsGrid = rememberLazyGridState()
    val playlistsGrid = rememberLazyGridState()
    val genresList = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val layoutManager = LocalAppContainer.current.libraryLayoutManager
    val layouts by layoutManager.layouts.collectAsStateWithLifecycle()
    val albumArtistsOnly by layoutManager.albumArtistsOnly.collectAsStateWithLifecycle()
    val showFavourites by layoutManager.showFavourites.collectAsStateWithLifecycle()
    // The view options panel (open/closed, animated) and the tab it was opened for.
    val viewOptions = remember { MutableTransitionState(false) }
    var viewSection by remember { mutableStateOf(LibrarySection.ALBUMS) }

    // Each tab loads on its first visit and then keeps its data while on this
    // page, so switching back shows the list at once, right where it was left.
    // A tab counts as visited as soon as a swipe brings it on screen.
    val visited = remember { mutableStateListOf(pagerState.currentPage) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.layoutInfo.visiblePagesInfo.map { it.index } }
            .collect { pages -> pages.forEach { if (it !in visited) visited += it } }
    }
    val repository = LocalAppContainer.current.repository
    // A pull-to-refresh per tab, reloading just that tab while its list stays up.
    val refreshes = remember { List(tabs.size) { ScreenRefresh() } }
    val artists = if (0 in visited) {
        rememberScreenLoad(albumArtistsOnly, errorMessage = stringResource(R.string.library_artists_load_error), refresh = refreshes[0]) {
            if (albumArtistsOnly) repository.getAlbumArtists() else repository.getArtists()
        }
    } else UiState.Loading
    val albums = if (1 in visited) {
        rememberScreenLoad(Unit, errorMessage = stringResource(R.string.library_albums_load_error), refresh = refreshes[1]) {
            repository.getAlbumList("alphabeticalByArtist", 500)
        }
    } else UiState.Loading
    val favouritesName = stringResource(R.string.data_favourites)
    val playlists = if (2 in visited) {
        // Favourites (the liked songs) first, when shown; the rest are the library's own.
        rememberScreenLoad(showFavourites, errorMessage = stringResource(R.string.library_playlists_load_error), refresh = refreshes[2]) {
            coroutineScope {
                val liked = if (showFavourites) async { runCatching { repository.getLikedSongs() }.getOrNull() } else null
                val own = repository.getPlaylists()
                listOfNotNull(liked?.await()?.let { favouritesPlaylist(it, favouritesName) }) + own
            }
        }
    } else UiState.Loading
    val genres = if (3 in visited) {
        rememberScreenLoad(Unit, errorMessage = stringResource(R.string.library_genres_load_error), refresh = refreshes[3]) { repository.getGenres() }
    } else UiState.Loading

    fun onTabSelected(tab: Int) {
        if (tab == pagerState.currentPage && !pagerState.isScrollInProgress) {
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
        if (tab !in visited) visited += tab
        scope.launch { pagerState.animateScrollToPage(tab) }
    }

    // Fixed title, with the tab bar floating as glass right under it.
    TitledPage(
        modifier = modifier,
        title = {
            val currentSection = sectionOf(pagerState.targetPage)
            PageTitle(stringResource(R.string.library_title)) {
                ViewButtonSlot(
                    visible = currentSection != null,
                    mode = layouts.getValue(currentSection ?: LibrarySection.ALBUMS).mode,
                    open = viewOptions.targetState,
                    onClick = {
                        if (!viewOptions.targetState && currentSection != null) viewSection = currentSection
                        viewOptions.targetState = !viewOptions.targetState
                    },
                )
            }
        },
        // The bar slot only reserves the pill's height (content padding and
        // fade); the pill itself is drawn by the overlay, which can grow it
        // into the view options panel without pushing the content down.
        bar = { Spacer(Modifier.height(GlassTabBarSize.Chrome.height)) },
        overlay = { hazeState ->
            LibraryTabsPanel(
                state = viewOptions,
                hazeState = hazeState,
                sectionName = stringResource(tabs[viewSection.ordinal]),
                layout = layouts.getValue(viewSection),
                onLayoutChange = { layoutManager.setLayout(viewSection, it) },
                onDismiss = { viewOptions.targetState = false },
                tabs = {
                    GlassTabBar(
                        labels = tabs.map { stringResource(it) },
                        selectedIndex = pagerState.targetPage,
                        onSelect = ::onTabSelected,
                        hazeState = null,
                        position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                        // Faded out under the open panel: taps there must not switch tabs.
                        enabled = !viewOptions.targetState,
                        glass = false,
                        // Sized like the floating nav bar, so the two pills match.
                        barSize = GlassTabBarSize.Chrome,
                        // Icons like the nav bar: the label shows only on the selected tab.
                        icons = tabIcons,
                        // Swiping along the bar scrolls the pages with the finger, so the
                        // indicator (following the pager) stays under it too.
                        onSwipe = { at ->
                            val page = at.roundToInt()
                            scope.launch { pagerState.scrollToPage(page, at - page) }
                        },
                        onSwipeEnd = { tab ->
                            if (tab !in visited) visited += tab
                            scope.launch { pagerState.animateScrollToPage(tab) }
                        },
                    )
                },
            )
        },
    ) { topPadding ->
        val padding = LibraryPadding(top = topPadding, bottom = contentPaddingBottom)
        // No swiping pages while the view options are open: the panel's scrim
        // covers the content, so they always apply to the tab on screen.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            // Pulling down past the top reloads the tab, as on Home.
            OneUiPullToRefresh(
                isRefreshing = refreshes[page].isRefreshing,
                onRefresh = refreshes[page]::refresh,
                topInset = topPadding,
            ) {
                when (page) {
                    0 -> LibraryCollection(
                        state = artists,
                        gridState = artistsGrid,
                        layout = layouts.getValue(LibrarySection.ARTISTS),
                        padding = padding,
                        key = { it.id },
                        card = { artist, size -> ArtistCard(artist, onClick = { onArtistClick(artist) }, artSize = size) },
                        row = { artist -> ArtistRow(artist, onClick = { onArtistClick(artist) }) },
                    )
                    1 -> LibraryCollection(
                        state = albums,
                        gridState = albumsGrid,
                        layout = layouts.getValue(LibrarySection.ALBUMS),
                        padding = padding,
                        key = { it.id },
                        card = { album, size -> AlbumCard(album, onClick = { onAlbumClick(album) }, artSize = size) },
                        // Sideways swipes here change tabs, so no swipe actions.
                        row = { album -> AlbumRow(album, onClick = { onAlbumClick(album) }, swipeActions = false) },
                    )
                    2 -> LibraryCollection(
                        state = playlists,
                        gridState = playlistsGrid,
                        layout = layouts.getValue(LibrarySection.PLAYLISTS),
                        padding = padding,
                        key = { it.id },
                        card = { playlist, size -> PlaylistCard(playlist, onClick = { onPlaylistClick(playlist) }, artSize = size) },
                        row = { playlist -> PlaylistRow(playlist, onClick = { onPlaylistClick(playlist) }) },
                    )
                    else -> GenreList(genres, genresList, padding, onGenreClick)
                }
            }
        }
    }
}

/** Fixed-size slot, so the title row keeps its height when the button hides on Genres. */
@Composable
private fun ViewButtonSlot(visible: Boolean, mode: LibraryViewMode, open: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.size(48.dp)) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
        ) {
            LibraryViewButton(mode = mode, open = open, onClick = onClick)
        }
    }
}

@Composable
private fun GenreList(
    state: UiState<List<Genre>>,
    listState: LazyListState,
    padding: LibraryPadding,
    onGenreClick: (Genre) -> Unit,
) {
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
                        .oneUiRowClickable { onGenreClick(genre) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = genre.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = songCount(genre.songCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
