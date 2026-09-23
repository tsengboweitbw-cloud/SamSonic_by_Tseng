package com.example.samsonic.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * The tab the visible page belongs to: a detail page (album, artist...) counts
 * under the tab it was opened from, so the nav bar keeps that tab selected
 * instead of dropping its pill and label. Null when no tab is in the back stack
 * (e.g. the login screen).
 */
@Composable
internal fun NavController.currentTab(tabRoutes: List<String>): String? {
    val backStack by currentBackStack.collectAsState()
    return backStack.lastOrNull { it.destination.route in tabRoutes }?.destination?.route
}

/**
 * Tapping another tab switches to it, restoring whatever it had open. Tapping
 * the tab you're already under returns to its first page: restoring would just
 * bring back the page you're on, so the tap looked like it did nothing.
 */
internal fun NavController.selectTab(route: String, currentTab: String?) {
    if (route == currentTab) {
        popBackStack(route, inclusive = false)
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
