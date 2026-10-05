package com.example.samsonic.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.data.HomeLayoutManager
import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.model.Album
import com.example.samsonic.model.Song
import com.example.samsonic.ui.common.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds Home's shelves for as long as Home is in the back stack, so opening an
 * album or switching tabs and coming back shows the same albums - in particular
 * the same random "Picked For You" set. They reload only when asked ([refresh]).
 */
class HomeViewModel(
    private val repository: MusicLibrary,
    private val layout: HomeLayoutManager,
) : ViewModel() {
    var state by mutableStateOf<UiState<Map<HomeShelf, ShelfItems>>>(UiState.Loading)
        private set

    /** A pull-to-refresh is running; the current shelves stay on screen meanwhile. */
    var isRefreshing by mutableStateOf(false)
        private set

    private var loadJob: Job? = null

    /** How many items each shelf was last asked for. */
    private val requested = java.util.concurrent.ConcurrentHashMap<HomeShelf, Int>()

    init {
        load()
        viewModelScope.launch { layout.sections.collect { topUp() } }
    }

    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        load()
    }

    fun retry() {
        state = UiState.Loading
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                state = UiState.Success(fetchShelves())
                // Lengths set while this was loading.
                topUp()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed refresh keeps the shelves already shown.
                if (state !is UiState.Success) state = UiState.Error(e.message ?: "Couldn't load your library")
            } finally {
                // A load cancelled by a newer one leaves the flag to that one.
                if (loadJob === coroutineContext[Job]) isRefreshing = false
            }
        }
    }

    /** One album shelf's albums, in step with [state]. */
    fun shelfState(shelf: HomeShelf): UiState<List<Album>> = when (val current = state) {
        is UiState.Success -> UiState.Success((current.data[shelf] as? ShelfItems.Albums)?.albums.orEmpty())
        is UiState.Error -> current
        UiState.Loading -> UiState.Loading
    }

    /** One song shelf's songs, in step with [state]. */
    fun songShelfState(shelf: HomeShelf): UiState<List<Song>> = when (val current = state) {
        is UiState.Success -> UiState.Success((current.data[shelf] as? ShelfItems.Songs)?.songs.orEmpty())
        is UiState.Error -> current
        UiState.Loading -> UiState.Loading
    }

    /** Every shown section's items, asking for only as many as the user set it to hold. */
    private suspend fun fetchShelves(): Map<HomeShelf, ShelfItems> = withContext(Dispatchers.Default) {
        val wanted = wantedCounts()
        requested.clear()
        requested.putAll(wanted)
        coroutineScope {
            wanted
                .map { (shelf, count) -> async { shelf to fetch(shelf, count) } }
                .awaitAll()
                .toMap()
        }
    }

    /** The sections the user shows, with how many items each should hold. */
    private fun wantedCounts(): Map<HomeShelf, Int> =
        layout.sections.value.filter { it.visible }.associate { it.shelf to it.count }

    /**
     * Fetches again, for just the sections now showing more than was asked for (a longer
     * length set, or one switched back on). A random shelf keeps what it holds and adds
     * to it, so the picks already seen don't change under the user.
     */
    private fun topUp() {
        val current = (state as? UiState.Success)?.data ?: return
        for ((shelf, count) in wantedCounts()) {
            if (count <= (requested[shelf] ?: 0)) continue
            requested[shelf] = count
            viewModelScope.launch {
                val fetched = try {
                    withContext(Dispatchers.Default) { fetch(shelf, count) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Asked again next time the length changes, or on a refresh.
                    requested.remove(shelf)
                    return@launch
                }
                val latest = (state as? UiState.Success)?.data ?: current
                val held = latest[shelf]
                val items = if (shelf.sharesHomeList && held is ShelfItems.Songs && fetched is ShelfItems.Songs) {
                    val seen = held.songs.mapTo(HashSet()) { it.id }
                    ShelfItems.Songs((held.songs + fetched.songs.filter { it.id !in seen }).take(count))
                } else {
                    fetched
                }
                state = UiState.Success(latest + (shelf to items))
            }
        }
    }

    private suspend fun fetch(shelf: HomeShelf, count: Int): ShelfItems = when (shelf.kind) {
        ShelfKind.Albums -> ShelfItems.Albums(repository.getAlbumList(shelf.type, count))
        // Built from many album requests (see getSongList), so one failing leaves just
        // that shelf out rather than failing all of Home.
        ShelfKind.Songs -> ShelfItems.Songs(
            try {
                repository.getSongList(shelf.type, count)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            },
        )
    }
}

/**
 * Home's [HomeViewModel], kept by [owner]: Home itself by default, or Home's
 * back stack entry when a page opened from Home (a shelf list) needs the same data.
 */
@Composable
fun rememberHomeViewModel(
    owner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner" },
): HomeViewModel {
    val container = LocalAppContainer.current
    val repository = container.repository
    val layout = container.homeLayoutManager
    return viewModel(viewModelStoreOwner = owner, factory = viewModelFactory { initializer { HomeViewModel(repository, layout) } })
}
