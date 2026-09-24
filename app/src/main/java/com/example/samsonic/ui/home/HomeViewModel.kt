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
class HomeViewModel(private val repository: MusicLibrary) : ViewModel() {
    var state by mutableStateOf<UiState<Map<HomeShelf, ShelfItems>>>(UiState.Loading)
        private set

    /** A pull-to-refresh is running; the current shelves stay on screen meanwhile. */
    var isRefreshing by mutableStateOf(false)
        private set

    private var loadJob: Job? = null

    init {
        load()
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

    private suspend fun fetchShelves(): Map<HomeShelf, ShelfItems> = withContext(Dispatchers.Default) {
        coroutineScope {
            HomeShelf.entries
                .map { shelf -> async { shelf to fetch(shelf) } }
                .awaitAll()
                .toMap()
        }
    }

    private suspend fun fetch(shelf: HomeShelf): ShelfItems = when (shelf.kind) {
        ShelfKind.Albums -> ShelfItems.Albums(repository.getAlbumList(shelf.type, shelf.previewSize))
        // Built from many album requests (see getSongList), so one failing leaves just
        // that shelf out rather than failing all of Home.
        ShelfKind.Songs -> ShelfItems.Songs(
            try {
                repository.getSongList(shelf.type, shelf.previewSize)
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
    val repository = LocalAppContainer.current.repository
    return viewModel(viewModelStoreOwner = owner, factory = viewModelFactory { initializer { HomeViewModel(repository) } })
}
