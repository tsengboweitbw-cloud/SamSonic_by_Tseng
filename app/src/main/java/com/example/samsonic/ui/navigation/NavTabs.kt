package com.example.samsonic.ui.navigation

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The main tabs (Home, Library...), side by side as the pages of one [pager], as
 * the Library's own tabs are, so a swipe along the nav bar slides them under the
 * finger. Each tab keeps its own back stack ([controllers]): a page opened under
 * Library is still there on coming back to Library.
 */
@Stable
internal class MainTabs(
    val routes: List<String>,
    val pager: PagerState,
    val controllers: List<NavHostController>,
    private val scope: CoroutineScope,
) {
    /**
     * The tabs whose pages are up: those shown so far, and after the app first settles,
     * all of them ([warmUp]). The pager keeps every tab composed but only draws what's on
     * screen; a tab not up yet is an empty page. A switch from Home to Settings slides past
     * Library and Search, and building those in the middle of the slide dropped frames.
     */
    val visited = mutableStateListOf<Boolean>().apply { repeat(routes.size) { add(it == pager.currentPage) } }

    /**
     * Brings every tab's pages up, for when the app has settled after starting: one at a
     * time, [gapMillis] apart, so building them never lands in one long frame.
     */
    suspend fun warmUp(gapMillis: Long) {
        for (i in visited.indices) {
            if (visited[i]) continue
            visited[i] = true
            delay(gapMillis)
        }
    }

    /** The tab showing, or the one a switch is heading for. */
    val current: Int get() = pager.targetPage

    val currentRoute: String get() = routes[current]

    fun controller(route: String): NavHostController = controllers[routes.indexOf(route)]

    /**
     * Tapping another tab slides over to it, as it was left. Tapping the tab you're
     * already on returns it to its first page: sliding would do nothing there.
     */
    fun select(index: Int) {
        if (index == pager.currentPage && !pager.isScrollInProgress) {
            controllers[index].popBackStack(routes[index], inclusive = false)
        } else {
            scope.launch { pager.animateScrollToPage(index) }
        }
    }

    /** Opens the tab at [route], sliding over to it. */
    fun select(route: String) = scope.launch { pager.animateScrollToPage(routes.indexOf(route)) }

    /**
     * Slides over to [tab] and opens [page] there, once its pages are up (a tab not
     * yet visited has nothing to open a page on until then). A page already on top
     * there is just revealed, not stacked again.
     */
    fun open(tab: String, page: String) = scope.launch {
        pager.animateScrollToPage(routes.indexOf(tab))
        val controller = controller(tab)
        if (controller.currentBackStackEntry?.routeWithArgs() != page) controller.navigate(page)
    }

    /** Takes [tab] back to its first page; nothing to do if it hasn't been visited. */
    fun reset(tab: String) {
        val controller = controller(tab)
        if (controller.currentBackStackEntry != null) controller.popBackStack(tab, inclusive = false)
    }

    /** Holds the pages at [position] (in tabs), for a finger sliding along the nav bar. */
    fun swipeTo(position: Float) {
        val page = position.roundToInt().coerceIn(routes.indices)
        scope.launch { pager.scrollToPage(page, (position - page).coerceIn(-0.5f, 0.5f)) }
    }

    /** Settles the pages on [index] after a swipe along the nav bar lets go. */
    fun settle(index: Int) {
        scope.launch { pager.animateScrollToPage(index) }
    }
}

@Composable
internal fun rememberMainTabs(routes: List<String>, initialRoute: String?): MainTabs {
    val pager = rememberPagerState(initialPage = routes.indexOf(initialRoute).coerceAtLeast(0)) { routes.size }
    val controllers = routes.map { rememberNavController() }
    val scope = rememberCoroutineScope()
    return remember(pager, scope) { MainTabs(routes, pager, controllers, scope) }
}

/**
 * The tab last shown, kept above the nav host (in MainActivity): the host is rebuilt when
 * the music source changes, and starts again on this tab, so switching sources in Settings
 * stays in Settings. Null when no tab was showing (sign in), which starts on Home.
 */
class LastTab {
    var route: String? = null
}
