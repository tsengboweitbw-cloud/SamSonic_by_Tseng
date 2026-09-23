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
 * Page transitions for the main nav graph. Tab switches drift a short way in
 * the direction of the tapped tab while crossfading, so they read as moving
 * along the nav bar; pushes to detail pages slide in from the side and pops
 * reverse it. Everything uses one decelerating curve so pages settle softly.
 */
internal class NavTransitions(private val tabRoutes: List<String>) {

    private val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private val enterMs = 340
    private val exitMs = 160

    private fun tabIndex(entry: NavBackStackEntry): Int = tabRoutes.indexOf(entry.destination.route)

    /** +1 moving right along the nav bar, -1 moving left, 0 if either side isn't a tab. */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): Int {
        val from = tabIndex(initialState)
        val to = tabIndex(targetState)
        return if (from < 0 || to < 0) 0 else to.compareTo(from)
    }

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        val dir = tabDirection()
        if (dir != 0) {
            slideInHorizontally(tween(enterMs, easing = easing)) { it * dir / 8 } +
                fadeIn(tween(enterMs - 80, delayMillis = 60, easing = easing))
        } else {
            slideInHorizontally(tween(enterMs, easing = easing)) { it / 5 } +
                fadeIn(tween(enterMs - 80, delayMillis = 40, easing = easing))
        }
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        val dir = tabDirection()
        if (dir != 0) {
            slideOutHorizontally(tween(enterMs, easing = easing)) { -it * dir / 8 } +
                fadeOut(tween(exitMs, easing = easing))
        } else {
            scaleOut(tween(enterMs, easing = easing), targetScale = 0.96f) +
                fadeOut(tween(exitMs, easing = easing))
        }
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (tabDirection() != 0) {
            enter()
        } else {
            scaleIn(tween(enterMs, easing = easing), initialScale = 0.96f) +
                fadeIn(tween(enterMs - 80, delayMillis = 40, easing = easing))
        }
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (tabDirection() != 0) {
            exit()
        } else {
            slideOutHorizontally(tween(enterMs, easing = easing)) { it / 5 } +
                fadeOut(tween(exitMs, easing = easing))
        }
    }
}
