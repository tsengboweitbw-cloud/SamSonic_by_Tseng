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
    var state by mutableStateOf<UiState<Map<AlbumShelf, List<Album>>>>(UiState.Loading)
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

    /** One shelf's albums, in step with [state]. */
    fun shelfState(shelf: AlbumShelf): UiState<List<Album>> = when (val current = state) {
        is UiState.Success -> UiState.Success(current.data[shelf].orEmpty())
        is UiState.Error -> current
        UiState.Loading -> UiState.Loading
    }

    private suspend fun fetchShelves(): Map<AlbumShelf, List<Album>> = withContext(Dispatchers.Default) {
        coroutineScope {
            AlbumShelf.entries
                .map { shelf -> async { shelf to repository.getAlbumList(shelf.type, shelf.previewSize) } }
                .awaitAll()
                .toMap()
        }
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
