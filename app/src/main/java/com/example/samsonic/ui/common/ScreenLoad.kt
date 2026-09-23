package com.example.samsonic.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * Loads a screen's data without fighting the page transition:
 * - navigating away cancels a load that hasn't finished, right away rather than
 *   after the exit animation, so it never lands mid-animation;
 * - a finished load waits for the enter animation to settle before it swaps the
 *   spinner for real content, keeping that heavy first layout off the animation frames;
 * - the fetch and mapping run off the main thread.
 */
@Composable
fun <T> rememberScreenLoad(
    vararg keys: Any?,
    errorMessage: String,
    load: suspend () -> T,
): UiState<T> {
    val phase = LocalScreenPhase.current
    val state by produceState<UiState<T>>(initialValue = UiState.Loading, *keys) {
        snapshotFlow { phase.isActive() }
            .distinctUntilChanged()
            .collectLatest { active ->
                // Leaving (or never arriving): collectLatest has already cancelled any running load.
                if (!active || value !is UiState.Loading) return@collectLatest
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
                value = result
            }
    }
    return state
}
