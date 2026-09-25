package com.example.samsonic.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.samsonic.ui.components.GlassTabBar
import com.example.samsonic.ui.components.GlassTabBarSize
import dev.chrisbanes.haze.HazeState

/**
 * One UI 9.0 floating pill bottom navigation: a frosted-glass bar over the
 * scrolling content, in place of Material's edge-to-edge opaque NavigationBar.
 * It's the Library's tab bar at chrome size ([GlassTabBar]), so the two behave
 * alike: one indicator pill glides between tabs, the selected tab widens to show
 * its label, taps give a soft press-scale instead of a ripple, and a finger
 * sliding along the bar slides the tabs' pages ([tabs]) along under it.
 */
@Composable
internal fun FloatingNavBar(
    destinations: List<BottomDestination>,
    tabs: MainTabs,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    GlassTabBar(
        labels = destinations.map { stringResource(it.label) },
        selectedIndex = tabs.current,
        onSelect = tabs::select,
        hazeState = hazeState,
        modifier = modifier,
        position = tabs.pager.currentPage + tabs.pager.currentPageOffsetFraction,
        barSize = GlassTabBarSize.Chrome,
        icons = destinations.map { it.icon },
        onSwipe = tabs::swipeTo,
        onSwipeEnd = tabs::settle,
    )
}
