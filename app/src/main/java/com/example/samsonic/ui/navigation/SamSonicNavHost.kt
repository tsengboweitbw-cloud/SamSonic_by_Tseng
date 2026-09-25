package com.example.samsonic.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.ui.auth.LoginScreen
import com.example.samsonic.ui.common.ArtTransitions
import com.example.samsonic.ui.common.LocalArtTransitions
import com.example.samsonic.ui.common.LocalSharedTransitionScope
import com.example.samsonic.ui.home.HomeShelf
import com.example.samsonic.ui.home.ShelfKind
import com.example.samsonic.ui.home.SongShelfScreen
import com.example.samsonic.ui.home.AlbumShelfScreen
import com.example.samsonic.ui.home.HomeScreen
import com.example.samsonic.ui.home.rememberHomeViewModel
import com.example.samsonic.ui.library.AddToPlaylistMenu
import com.example.samsonic.ui.library.LibraryScreen
import com.example.samsonic.ui.library.LocalAddToPlaylist
import com.example.samsonic.ui.library.rememberAddToPlaylistState
import com.example.samsonic.ui.player.PlayerSheet
import com.example.samsonic.ui.player.rememberPlayerSheetState
import com.example.samsonic.ui.search.SearchScreen
import com.example.samsonic.ui.search.SearchSession
import com.example.samsonic.ui.settings.SettingsScreen
import com.example.samsonic.ui.theme.AmbientGlow
import com.example.samsonic.ui.theme.LocalHazeState
import com.example.samsonic.ui.theme.OneUiChrome
import com.example.samsonic.ui.theme.bottomFade
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

data class BottomDestination(val route: String, @StringRes val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    BottomDestination(Routes.LIBRARY, R.string.nav_library, Icons.Filled.LibraryMusic),
    BottomDestination(Routes.SEARCH, R.string.nav_search, Icons.Filled.Search),
    BottomDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

private val noChromeRoutes = setOf(Routes.LOGIN, Routes.ADD_SERVER)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SamSonicNavHost(lastTab: LastTab) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Read once: the host is rebuilt whenever the music source changes (see MainActivity),
    // so it starts on the tab you were under (Settings, where sources are switched and
    // added), signing in for the first time lands on Home, and leaving the last source
    // lands back on sign in.
    val startDestination = remember {
        if (container.sources.active.value == null) Routes.LOGIN else lastTab.route ?: Routes.HOME
    }

    val showChrome = currentRoute == null || currentRoute !in noChromeRoutes
    // The floating nav bar shows everywhere but login; it sinks away as the
    // player sheet opens over it (see PlayerSheet below).
    val showBottomBar = showChrome

    val tabRoutes = remember { bottomDestinations.map { it.route } }
    val currentTab = navController.currentTab(tabRoutes)
    if (backStackEntry != null) SideEffect { lastTab.route = currentTab }
    val navTransitions = remember { NavTransitions(tabRoutes) }
    val hazeState = rememberHazeState()
    val playerSheet = rememberPlayerSheetState()
    val artTransitions = remember { ArtTransitions() }
    // The query and results stay through tab switches (which also keep Search's scroll and
    // any page opened from it) until the app closes, Home is pulled to refresh, or the
    // source changes and rebuilds this.
    val searchSession = remember { SearchSession() }
    // Offered only where there are playlists to add to; the host is rebuilt with the source.
    val addToPlaylist = rememberAddToPlaylistState()
    val canEditPlaylists = remember { container.repository.canEditPlaylists }
    val navBarHeight = OneUiChrome.BarHeight
    val navBarBottomInset = 16.dp

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        CompositionLocalProvider(
            LocalHazeState provides hazeState,
            LocalAddToPlaylist provides addToPlaylist.takeIf { canEditPlaylists },
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The chrome keeps its resting spot while it animates out, so these ignore showChrome.
            val chromeBottomInset = innerPadding.calculateBottomPadding()
            val systemBarInset = if (showChrome) chromeBottomInset else 0.dp
            val navBarReserve = if (showChrome && showBottomBar) navBarHeight + navBarBottomInset else 0.dp
            val miniPlayerReserve = if (showChrome) OneUiChrome.BarHeight + 8.dp else 0.dp
            // Content melts into the background near the bottom edge: 1.5 times the
            // space below the nav bar (its bottom gap and the system bar), so the
            // fade reaches a little way up behind the bar. Above that it stays
            // solid behind the glass. Login skips it. Animated so route changes
            // don't pop the mask.
            val bottomFadeHeight by animateDpAsState(
                targetValue = if (showChrome) (systemBarInset + navBarBottomInset) * 1.5f else 0.dp,
                animationSpec = tween(260),
                label = "bottomFade",
            )

            // graphicsLayer() forces this whole subtree (including any
            // LazyColumn/LazyVerticalGrid screens inside NavHost) to
            // composite into one flattened layer before hazeSource snapshots
            // it - LazyColumn content otherwise has known gaps in Haze's
            // capture (text and item edges stay sharp/unblurred).
            Box(modifier = Modifier.fillMaxSize().bottomFade(bottomFadeHeight).graphicsLayer().hazeSource(hazeState)) {
            // Inside the haze source, so the frosted bars pick up its color as they blur it.
            AmbientGlow()
            // Covers and pictures travel from the card tapped to the page it opens (see sharedArt).
            SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this, LocalArtTransitions provides artTransitions) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
                enterTransition = navTransitions.enter,
                exitTransition = navTransitions.exit,
                popEnterTransition = navTransitions.popEnter,
                popExitTransition = navTransitions.popExit,
            ) {
                screen(Routes.LOGIN) {
                    LoginScreen()
                }
                screen(Routes.ADD_SERVER) {
                    LoginScreen(onBack = { navController.popBackStack() })
                }
                screen(Routes.HOME) {
                    HomeScreen(
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        onShelfClick = { navController.navigate(Routes.shelf(it.key)) },
                        // A refresh starts Search over too: the query, and whatever was
                        // opened from its results (Search's saved tab state).
                        onRefresh = {
                            searchSession.clear()
                            navController.clearBackStack(Routes.SEARCH)
                        },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                screen(Routes.LIBRARY) {
                    LibraryScreen(
                        onArtistClick = { navController.navigate(Routes.artist(it.id)) },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        onPlaylistClick = { navController.navigate(Routes.playlist(it.id)) },
                        onGenreClick = { navController.navigate(Routes.genre(it.name)) },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                screen(Routes.SEARCH) {
                    SearchScreen(
                        session = searchSession,
                        onArtistClick = { navController.navigate(Routes.artist(it.id)) },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                screen(Routes.SETTINGS) {
                    SettingsScreen(
                        onAddServer = { navController.navigate(Routes.ADD_SERVER) },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
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
                            contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                        )
                        return@screen
                    }
                    val homeAlbums = home?.shelfState(shelf)
                    AlbumShelfScreen(
                        shelf = shelf,
                        homeAlbums = homeAlbums,
                        onBack = { navController.popBackStack() },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                detailScreens(navController, contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve)
            }
            }
            }
            }

            // The player: the mini player's pill, which drags up into Now Playing.
            // Under the nav bar, so the bar can sink away over it as it grows.
            if (showChrome) {
                // From Now Playing, always under the Library tab: switched to first (with
                // whatever it had open), so the nav bar and back lead through Library. A
                // page already on top there is just revealed, not stacked again.
                val openFromPlayer = remember(navController, currentTab) {
                    { route: String ->
                        if (currentTab != Routes.LIBRARY) navController.selectTab(Routes.LIBRARY, currentTab)
                        if (navController.currentBackStackEntry?.routeWithArgs() != route) navController.navigate(route)
                    }
                }
                PlayerSheet(
                    sheet = playerSheet,
                    collapsedBottom = chromeBottomInset + navBarHeight + navBarBottomInset + 8.dp,
                    onAlbumClick = remember(openFromPlayer) { { openFromPlayer(Routes.album(it)) } },
                    onArtistClick = remember(openFromPlayer) { { openFromPlayer(Routes.artist(it)) } },
                )
            }

            // Sinks below the screen edge in step with the player sheet opening.
            AnimatedVisibility(
                visible = showChrome && showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        val progress = playerSheet.progress
                        translationY = progress * (navBarHeight + navBarBottomInset + chromeBottomInset).toPx()
                        alpha = 1f - progress
                    }
                    .padding(horizontal = 16.dp)
                    .padding(bottom = chromeBottomInset + navBarBottomInset)
                    .fillMaxWidth()
                    .height(navBarHeight),
            ) {
                FloatingNavBar(
                    destinations = bottomDestinations,
                    selectedRoutes = setOfNotNull(currentTab),
                    onSelect = { navController.selectTab(it, currentTab) },
                    hazeState = hazeState,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Over everything, the nav bar included; it grows out of the long-pressed song row.
            if (canEditPlaylists) AddToPlaylistMenu(addToPlaylist, hazeState)
        }
        }
    }
}
