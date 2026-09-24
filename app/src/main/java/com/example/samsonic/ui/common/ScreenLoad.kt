package com.example.samsonic.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Where a nav screen is in its enter/exit animation. Reads are snapshot-backed. */
class ScreenPhase(
    /** The screen is (or is becoming) the visible destination. False the moment the user navigates away. */
    val isActive: () -> Boolean,
    /** The screen is visible and its enter animation has finished. */
    val isSettled: () -> Boolean,
)

/** Outside a NavHost (previews, tests) a screen is always active and settled. */
val LocalScreenPhase = staticCompositionLocalOf { ScreenPhase({ true }, { true }) }

/** Wraps a nav destination's content so [rememberScreenLoad] can follow its transition. */
@Composable
fun AnimatedVisibilityScope.ScreenHost(content: @Composable () -> Unit) {
    val transition = transition
    val phase = remember(transition) {
        ScreenPhase(
            isActive = { transition.targetState == EnterExitState.Visible },
            isSettled = {
                transition.targetState == EnterExitState.Visible &&
                    transition.currentState == EnterExitState.Visible
            },
        )
    }
    CompositionLocalProvider(LocalScreenPhase provides phase, content = content)
}

/**
 * What a nav destination's screens have loaded, kept for as long as the destination is
 * in the back stack. Its composition goes while a page opened from it shows, and comes
 * back on back; without these its lists would load again from scratch, and a cover
 * shrinking back from that page would find no card to land in.
 */
class ScreenLoadCache : ViewModel() {
    internal val results = HashMap<String, UiState.Success<*>>()
}

/**
 * Loads a screen's data without fighting the page transition:
 * - navigating away cancels a load that hasn't finished, right away rather than
 *   after the exit animation, so it never lands mid-animation;
 * - a finished load waits for the enter animation to settle before it swaps the
 *   spinner for real content, keeping that heavy first layout off the animation frames;
 * - the fetch and mapping run off the main thread;
 * - what loaded is kept with the destination ([ScreenLoadCache]), by its place in the
 *   screen and [keys], and shown at once when the user comes back to it;
 * - [refresh] (for a pull-to-refresh) fetches again while what's shown stays up.
 */
@Composable
fun <T> rememberScreenLoad(
    vararg keys: Any?,
    errorMessage: String,
    refresh: ScreenRefresh? = null,
    load: suspend () -> T,
): UiState<T> {
    val phase = LocalScreenPhase.current
    val cache = LocalViewModelStoreOwner.current?.let { viewModel<ScreenLoadCache>(it) }
    val slot = "$currentCompositeKeyHash:${keys.contentHashCode()}"
    @Suppress("UNCHECKED_CAST")
    val state = remember(slot) { mutableStateOf<UiState<T>>(cache?.results?.get(slot) as UiState.Success<T>? ?: UiState.Loading) }
    LaunchedEffect(slot) {
        snapshotFlow { phase.isActive() }
            .distinctUntilChanged()
            .collectLatest { active ->
                // Leaving (or never arriving): collectLatest has already cancelled any running load.
                if (!active || state.value !is UiState.Loading) return@collectLatest
                val result = withContext(Dispatchers.Default) {
                    runCatching { load() }.fold(
                        onSuccess = { UiState.Success(it) },
                        onFailure = {
                            if (it is CancellationException) throw it
                            UiState.Error(it.message ?: errorMessage)
                        },
                    )
                }
                snapshotFlow { phase.isSettled() }.first { it }
                if (result is UiState.Success) cache?.results?.set(slot, result)
                state.value = result
            }
    }
    if (refresh != null) {
        LaunchedEffect(slot, refresh) {
            // Requests made from here on; one from before this composition was someone else's.
            snapshotFlow { refresh.requests }
                .drop(1)
                .collectLatest {
                    try {
                        // Still on its first load: that one is already fetching.
                        if (state.value is UiState.Loading) return@collectLatest
                        val result = withContext(Dispatchers.Default) { runCatching { load() } }
                        result.onSuccess {
                            val loaded = UiState.Success(it)
                            cache?.results?.set(slot, loaded)
                            state.value = loaded
                        }
                        // A failed refresh keeps what's shown, unless that was an error too.
                        result.onFailure {
                            if (it is CancellationException) throw it
                            if (state.value is UiState.Error) state.value = UiState.Error(it.message ?: errorMessage)
                        }
                    } finally {
                        refresh.isRefreshing = false
                    }
                }
        }
    }
    return state.value
}

/**
 * A pull-to-refresh for [rememberScreenLoad]s: [refresh] has the loads given it fetch
 * again, and [isRefreshing] holds until they're done.
 */
@Stable
class ScreenRefresh {
    internal var requests by mutableIntStateOf(0)
        private set

    var isRefreshing by mutableStateOf(false)
        internal set

    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        requests++
    }
}
