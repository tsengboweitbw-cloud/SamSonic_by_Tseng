package com.example.samsonic.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import androidx.compose.animation.core.Spring
import kotlin.math.roundToInt
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.lerp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.ui.common.LocalWindowLayout
import com.example.samsonic.ui.home.HomeShelf
import com.example.samsonic.ui.home.ShelfKind
import com.example.samsonic.ui.home.SongShelfScreen
import com.example.samsonic.ui.home.AlbumShelfScreen
import com.example.samsonic.ui.home.HomeScreen
import com.example.samsonic.ui.home.rememberHomeViewModel
import com.example.samsonic.ui.library.LibraryScreen
import com.example.samsonic.ui.search.SearchScreen
import com.example.samsonic.ui.search.SearchSession
import com.example.samsonic.ui.settings.SettingsScreen

/**
 * One tab's pages: its first page ([route]) and those opened from it, with their
 * own back stack ([navController]), so each tab keeps what it had open.
 *
 * In landscape on a tablet or an open foldable (LocalWindowLayout.twoPane), Home, Library
 * and Search keep their first page at the side and open pages beside it, where the back stack's pages
 * show in turn; picking another item on the first page replaces what's open beside it.
 * The back stack is the same either way, so turning or folding the screen keeps what's
 * open, and the first page keeps its own state (how far it's scrolled) moving between
 * its place at the side and the whole screen. Settings has two columns of its own.
 */
@Composable
internal fun TabHost(
    route: String,
    navController: NavHostController,
    tabs: MainTabs,
    searchSession: SearchSession,
    onAddServer: () -> Unit,
    contentPaddingBottom: Dp,
) {
    val desktop = LocalWindowLayout.current.desktop
    val twoPane = LocalWindowLayout.current.twoPane && route != Routes.SETTINGS
    // Opens a page from the tab's first page: beside it in two panes, in place of whatever
    // was open there, else over it.
    val openFromRoot: (String) -> Unit = remember(navController, twoPane) {
        { page ->
            if (twoPane) {
                navController.navigate(page) { popUpTo(route) }
            } else {
                navController.navigate(page)
            }
        }
    }
    // The first page's own state, whichever place it's shown in (see ListPane).
    val rootState = rememberSaveableStateHolder()
    val root: @Composable () -> Unit = {
        rootState.SaveableStateProvider(route) {
            RootPage(route, openFromRoot, tabs, searchSession, onAddServer, contentPaddingBottom)
        }
    }
    // In two panes, how far a page is open beside the first: 0 with nothing open (the first
    // page has the whole width), 1 open (the first page at the side). Easing between as a
    // page opens or the last one closes; straight to where it should be as the layout
    // switches (turning, folding), so what was open simply shows in its new place.
    val current by navController.currentBackStackEntryAsState()
    val detailOpen = twoPane && current?.destination?.route.let { it != null && it != route }
    val layoutManager = LocalAppContainer.current.libraryLayoutManager
    // The Library keeps its own columns as the side pane. Only the Library's own host says so:
    // every tab stays composed, and another tab's would overwrite it.
    if (route == Routes.LIBRARY) SideEffect { layoutManager.setDetailOpen(detailOpen) }
    val split = remember { Animatable(if (detailOpen) 1f else 0f) }
    val twoPaneWas = remember { booleanArrayOf(twoPane) }
    LaunchedEffect(detailOpen, twoPane) {
        val target = if (detailOpen) 1f else 0f
        if (twoPaneWas[0] != twoPane) {
            twoPaneWas[0] = twoPane
            split.snapTo(target)
        } else {
            split.animateTo(target, SplitSpring)
        }
    }
    // Clipped: with nothing open, the pages beside the first wait just past its far edge.
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val fullPx = constraints.maxWidth
        // In DeX a window may be very wide: the two panes as even as a page beside a list
        // reads well there, rather than a narrow list against a very wide page.
        val sideWidth = if (desktop) {
            (maxWidth * DesktopListPaneShare).coerceAtLeast(ListPaneMinWidth)
        } else {
            (maxWidth * ListPaneShare).coerceIn(ListPaneMinWidth, ListPaneMaxWidth)
        }
        val sidePx = with(LocalDensity.current) { sideWidth.roundToPx() }
        // The first page's width now: all of it, narrowing to its side as a page opens.
        val listPx = { lerp(fullPx.toFloat(), sidePx.toFloat(), split.value).roundToInt() }
        NavHost(
            navController = navController,
            startDestination = route,
            modifier = if (twoPane) {
                // Its own width throughout (not squeezed as it comes), sliding in from the
                // far side beside the first page as that narrows.
                Modifier
                    .fillMaxHeight()
                    .width(maxWidth - sideWidth)
                    .offset { IntOffset(listPx(), 0) }
            } else {
                Modifier.fillMaxSize()
            },
            enterTransition = NavTransitions.enter,
            exitTransition = NavTransitions.exit,
            popEnterTransition = NavTransitions.popEnter,
            popExitTransition = NavTransitions.popExit,
        ) {
            // In two panes the first page is drawn at the side (ListPane); here, nothing.
            screen(route) { if (!twoPane) root() }
            if (route == Routes.HOME) {
                screen(Routes.SHELF) { entry ->
                    val shelf = entry.arguments?.getString("shelfType")?.let(HomeShelf::fromKey) ?: return@screen
                    // A shelf page is only opened from Home, so Home's entry is below it in the stack.
                    val home = if (shelf.sharesHomeList) {
                        val homeEntry = remember(entry) { navController.getBackStackEntry(Routes.HOME) }
                        rememberHomeViewModel(homeEntry)
                    } else null
                    if (shelf.kind == ShelfKind.Songs) {
                        SongShelfScreen(
                            shelf = shelf,
                            homeSongs = home?.songShelfState(shelf),
                            onBack = { navController.popBackStack() },
                            contentPaddingBottom = contentPaddingBottom,
                        )
                        return@screen
                    }
                    val homeAlbums = home?.shelfState(shelf)
                    AlbumShelfScreen(
                        shelf = shelf,
                        homeAlbums = homeAlbums,
                        onBack = { navController.popBackStack() },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        contentPaddingBottom = contentPaddingBottom,
                    )
                }
            }
            detailScreens(navController, contentPaddingBottom = contentPaddingBottom)
        }
        if (twoPane) {
            ListPane(
                navController,
                route,
                Modifier
                    .fillMaxHeight()
                    .layout { measurable, constraints ->
                        val width = listPx()
                        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                        layout(width, placeable.height) { placeable.place(0, 0) }
                    },
                root,
            )
        }
    }
}

// In two panes, the first page takes this share of the width, within these.
private const val ListPaneShare = 0.42f
private val ListPaneMinWidth = 340.dp
private val ListPaneMaxWidth = 480.dp
private const val DesktopListPaneShare = 0.5f

/**
 * A tab's first page, and what opens pages from it ([open]): at the side in two panes,
 * else as the first page of the tab's own pages.
 */
@Composable
private fun RootPage(
    route: String,
    open: (String) -> Unit,
    tabs: MainTabs,
    searchSession: SearchSession,
    onAddServer: () -> Unit,
    contentPaddingBottom: Dp,
) {
    when (route) {
        Routes.HOME -> HomeScreen(
            onAlbumClick = { open(Routes.album(it.id)) },
            onShelfClick = { open(Routes.shelf(it.key)) },
            // A refresh starts Search over too: the query, and whatever was
            // opened from its results.
            onRefresh = {
                searchSession.clear()
                tabs.reset(Routes.SEARCH)
            },
            contentPaddingBottom = contentPaddingBottom,
        )
        Routes.LIBRARY -> LibraryScreen(
            onArtistClick = { open(Routes.artist(it.id)) },
            onAlbumClick = { open(Routes.album(it.id)) },
            onPlaylistClick = { open(Routes.playlist(it.id)) },
            onGenreClick = { open(Routes.genre(it.name)) },
            contentPaddingBottom = contentPaddingBottom,
        )
        Routes.SEARCH -> SearchScreen(
            session = searchSession,
            onArtistClick = { open(Routes.artist(it.id)) },
            onAlbumClick = { open(Routes.album(it.id)) },
            contentPaddingBottom = contentPaddingBottom,
        )
        Routes.SETTINGS -> SettingsScreen(
            onAddServer = onAddServer,
            contentPaddingBottom = contentPaddingBottom,
        )
    }
}

/**
 * The tab's first page at the side, in two panes: drawn apart from the tab's pages, but as
 * their first page ([route]'s back stack entry), so it keeps its view models and what it
 * loaded. Nothing until that entry is there, as the pages are first set up.
 */
@Composable
private fun ListPane(navController: NavHostController, route: String, modifier: Modifier, content: @Composable () -> Unit) {
    val stack by navController.currentBackStack.collectAsStateWithLifecycle()
    val entry = stack.firstOrNull { it.destination.route == route } ?: return
    // The entry's own view models, but the tab's lifecycle: with a page open beside it the
    // entry is stopped, though the first page is still on screen, and what it collects
    // (its column counts among them) would stop coming.
    val lifecycle = LocalLifecycleOwner.current
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides entry,
        LocalLifecycleOwner provides lifecycle,
    ) {
        Box(modifier) { content() }
    }
}


// A page opening beside the first, or the last closing: unhurried, without a bounce.
private val SplitSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
