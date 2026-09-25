package com.example.samsonic.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * Page transitions inside a tab: pushes to detail pages slide in from the side
 * while the page under them sinks back, and pops reverse it. (The tabs themselves
 * slide as pages of a pager; see [MainTabs].) Everything moves on one decelerating
 * curve so pages settle softly.
 */
internal object NavTransitions {

    private val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private const val enterMs = 340
    private const val exitMs = 160

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(tween(enterMs, easing = easing)) { it / 5 } +
            fadeIn(tween(enterMs - 80, delayMillis = 40, easing = easing))
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        scaleOut(tween(enterMs, easing = easing), targetScale = 0.96f) +
            fadeOut(tween(exitMs, easing = easing))
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        scaleIn(tween(enterMs, easing = easing), initialScale = 0.96f) +
            fadeIn(tween(enterMs - 80, delayMillis = 40, easing = easing))
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(tween(enterMs, easing = easing)) { it / 5 } +
            fadeOut(tween(exitMs, easing = easing))
    }
}
