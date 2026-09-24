package com.example.samsonic.ui.navigation

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.ui.auth.LoginScreen
import com.example.samsonic.ui.home.AlbumShelf
import com.example.samsonic.ui.home.AlbumShelfScreen
import com.example.samsonic.ui.home.HomeScreen
import com.example.samsonic.ui.home.rememberHomeViewModel
import com.example.samsonic.ui.library.AlbumDetailScreen
import com.example.samsonic.ui.library.ArtistAlbumsScreen
import com.example.samsonic.ui.library.ArtistDetailScreen
import com.example.samsonic.ui.library.ArtistSongsScreen
import com.example.samsonic.ui.library.LibraryScreen
import com.example.samsonic.ui.library.PlaylistDetailScreen
import com.example.samsonic.ui.player.PlayerSheet
import com.example.samsonic.ui.player.rememberPlayerSheetState
import com.example.samsonic.ui.search.SearchScreen
import com.example.samsonic.ui.settings.SettingsScreen
import com.example.samsonic.ui.theme.LocalHazeState
import com.example.samsonic.ui.theme.OneUiChrome
import com.example.samsonic.ui.theme.bottomFade
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ARTIST = "artist/{artistId}"
    const val ARTIST_ALBUMS = "artist/{artistId}/albums"
    const val ARTIST_SONGS = "artist/{artistId}/songs"
    const val ALBUM = "album/{albumId}"
    const val PLAYLIST = "playlist/{playlistId}"
    const val SHELF = "shelf/{shelfType}"

    fun artist(id: String) = "artist/$id"
    fun artistAlbums(id: String) = "artist/$id/albums"
    fun artistSongs(id: String) = "artist/$id/songs"
    fun album(id: String) = "album/$id"
    fun playlist(id: String) = "playlist/$id"
    fun shelf(type: String) = "shelf/$type"
}

data class BottomDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "Home", Icons.Filled.Home),
    BottomDestination(Routes.LIBRARY, "Library", Icons.Filled.LibraryMusic),
    BottomDestination(Routes.SEARCH, "Search", Icons.Filled.Search),
    BottomDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

private val noChromeRoutes = setOf(Routes.LOGIN)

@Composable
fun SamSonicNavHost() {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Read once: EncryptedSharedPreferences loads synchronously, so this already reflects
    // whether a server session is saved by the time the nav graph is first built.
    val startDestination = remember {
        if (container.sessionManager.credentials.value != null) Routes.HOME else Routes.LOGIN
    }

    val showChrome = currentRoute == null || currentRoute !in noChromeRoutes
    // The floating nav bar shows everywhere but login; it sinks away as the
    // player sheet opens over it (see PlayerSheet below).
    val showBottomBar = showChrome

    val tabRoutes = remember { bottomDestinations.map { it.route } }
    val currentTab = navController.currentTab(tabRoutes)
    val navTransitions = remember { NavTransitions(tabRoutes) }
    val hazeState = rememberHazeState()
    val playerSheet = rememberPlayerSheetState()
    val navBarHeight = OneUiChrome.BarHeight
    val navBarBottomInset = 16.dp

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The chrome keeps its resting spot while it animates out, so these ignore showChrome.
            val chromeBottomInset = innerPadding.calculateBottomPadding()
            val systemBarInset = if (showChrome) chromeBottomInset else 0.dp
            val navBarReserve = if (showChrome && showBottomBar) navBarHeight + navBarBottomInset else 0.dp
            val miniPlayerReserve = if (showChrome) OneUiChrome.BarHeight + 8.dp else 0.dp
            // Content melts into the background across the whole floating-chrome
            // zone. Login skips it. Animated so route changes don't pop the mask.
            val bottomFadeHeight by animateDpAsState(
                targetValue = if (showChrome) systemBarInset + navBarReserve + miniPlayerReserve else 0.dp,
                animationSpec = tween(260),
                label = "bottomFade",
            )

            // graphicsLayer() forces this whole subtree (including any
            // LazyColumn/LazyVerticalGrid screens inside NavHost) to
            // composite into one flattened layer before hazeSource snapshots
            // it - LazyColumn content otherwise has known gaps in Haze's
            // capture (text and item edges stay sharp/unblurred).
            Box(modifier = Modifier.fillMaxSize().bottomFade(bottomFadeHeight).graphicsLayer().hazeSource(hazeState)) {
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
                    LoginScreen(
                        onConnected = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        },
                    )
                }
                screen(Routes.HOME) {
                    HomeScreen(
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        onShelfClick = { navController.navigate(Routes.shelf(it.type)) },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                screen(Routes.LIBRARY) {
                    LibraryScreen(
                        onArtistClick = { navController.navigate(Routes.artist(it.id)) },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        onPlaylistClick = { navController.navigate(Routes.playlist(it.id)) },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                screen(Routes.SEARCH) {
                    SearchScreen(
                        onArtistClick = { navController.navigate(Routes.artist(it.id)) },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                screen(Routes.SETTINGS) {
                    SettingsScreen(
                        onSignedOut = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            }
                        },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                screen(Routes.SHELF) { entry ->
                    val shelf = entry.arguments?.getString("shelfType")?.let(AlbumShelf::fromType) ?: return@screen
                    // A shelf page is only opened from Home, so Home's entry is below it in the stack.
                    val homeAlbums = if (shelf.sharesHomeList) {
                        val homeEntry = remember(entry) { navController.getBackStackEntry(Routes.HOME) }
                        rememberHomeViewModel(homeEntry).shelfState(shelf)
                    } else null
                    AlbumShelfScreen(
                        shelf = shelf,
                        homeAlbums = homeAlbums,
                        onBack = { navController.popBackStack() },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                screen(Routes.ARTIST) { entry ->
                    val artistId = entry.arguments?.getString("artistId") ?: return@screen
                    ArtistDetailScreen(
                        artistId = artistId,
                        onBack = { navController.popBackStack() },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        onAllAlbumsClick = { navController.navigate(Routes.artistAlbums(artistId)) },
                        onAllSongsClick = { navController.navigate(Routes.artistSongs(artistId)) },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                screen(Routes.ARTIST_ALBUMS) { entry ->
                    val artistId = entry.arguments?.getString("artistId") ?: return@screen
                    ArtistAlbumsScreen(
                        artistId = artistId,
                        onBack = { navController.popBackStack() },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                screen(Routes.ARTIST_SONGS) { entry ->
                    val artistId = entry.arguments?.getString("artistId") ?: return@screen
                    ArtistSongsScreen(
                        artistId = artistId,
                        onBack = { navController.popBackStack() },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                screen(Routes.ALBUM) { entry ->
                    val albumId = entry.arguments?.getString("albumId") ?: return@screen
                    AlbumDetailScreen(
                        albumId = albumId,
                        onBack = { navController.popBackStack() },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                screen(Routes.PLAYLIST) { entry ->
                    val playlistId = entry.arguments?.getString("playlistId") ?: return@screen
                    PlaylistDetailScreen(
                        playlistId = playlistId,
                        onBack = { navController.popBackStack() },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
            }
            }

            // The player: the mini player's pill, which drags up into Now Playing.
            // Under the nav bar, so the bar can sink away over it as it grows.
            if (showChrome) {
                // From Now Playing; a page already on top is just revealed, not stacked again.
                val openFromPlayer = remember(navController) {
                    { route: String -> if (navController.currentBackStackEntry?.routeWithArgs() != route) navController.navigate(route) }
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
        }
        }
    }
}

/** This entry's route with its arguments filled in, e.g. "album/42", to compare against a built route. */
private fun NavBackStackEntry.routeWithArgs(): String? {
    val pattern = destination.route ?: return null
    return Regex("""\{(\w+)\}""").replace(pattern) { arguments?.getString(it.groupValues[1]).orEmpty() }
}
