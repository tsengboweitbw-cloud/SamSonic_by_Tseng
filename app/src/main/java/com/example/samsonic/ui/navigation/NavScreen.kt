package com.example.samsonic.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.samsonic.ui.common.ScreenHost

private typealias Enter = (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)
private typealias Exit = (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)

/**
 * [composable] that also exposes the destination's transition phase to
 * [com.example.samsonic.ui.common.rememberScreenLoad], so loads stop when the
 * user leaves and results wait for the enter animation to finish.
 */
internal fun NavGraphBuilder.screen(
    route: String,
    enterTransition: Enter? = null,
    exitTransition: Exit? = null,
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(
    route,
    enterTransition = enterTransition,
    exitTransition = exitTransition,
    // A destination with its own motion (the slide-up player sheets) reverses it on
    // back; without these it would fall through to the host's sideways pop.
    popEnterTransition = enterTransition,
    popExitTransition = exitTransition,
) { entry ->
    ScreenHost { content(entry) }
}
