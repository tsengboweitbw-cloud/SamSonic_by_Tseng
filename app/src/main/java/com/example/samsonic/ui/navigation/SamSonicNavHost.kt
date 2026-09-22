package com.example.samsonic.ui.navigation

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.ui.auth.LoginScreen
import com.example.samsonic.ui.home.HomeScreen
import com.example.samsonic.ui.library.AlbumDetailScreen
import com.example.samsonic.ui.library.ArtistDetailScreen
import com.example.samsonic.ui.library.LibraryScreen
import com.example.samsonic.ui.library.PlaylistDetailScreen
import com.example.samsonic.ui.player.LyricsScreen
import com.example.samsonic.ui.player.MiniPlayer
import com.example.samsonic.ui.player.NowPlayingScreen
import com.example.samsonic.ui.player.QueueScreen
import com.example.samsonic.ui.search.SearchScreen
import com.example.samsonic.ui.settings.SettingsScreen
import com.example.samsonic.ui.theme.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ARTIST = "artist/{artistId}"
    const val ALBUM = "album/{albumId}"
    const val PLAYLIST = "playlist/{playlistId}"
    const val NOW_PLAYING = "nowPlaying"
    const val LYRICS = "lyrics"
    const val QUEUE = "queue"

    fun artist(id: String) = "artist/$id"
    fun album(id: String) = "album/$id"
    fun playlist(id: String) = "playlist/$id"
}

data class BottomDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "Home", Icons.Filled.Home),
    BottomDestination(Routes.LIBRARY, "Library", Icons.Filled.LibraryMusic),
    BottomDestination(Routes.SEARCH, "Search", Icons.Filled.Search),
)

private val noChromeRoutes = setOf(Routes.LOGIN, Routes.NOW_PLAYING, Routes.LYRICS, Routes.QUEUE)

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
    // The floating nav bar stays up everywhere chrome shows except Settings -
    // only the settings screen and the big player surfaces (now playing,
    // lyrics, queue - already excluded via noChromeRoutes) hide it.
    val showBottomBar = showChrome && currentRoute != Routes.SETTINGS

    val hazeState = rememberHazeState()
    val navBarHeight = 64.dp
    val navBarBottomInset = 16.dp

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(modifier = Modifier.fillMaxSize()) {
            val systemBarInset = if (showChrome) innerPadding.calculateBottomPadding() else 0.dp
            val navBarReserve = if (showChrome && showBottomBar) navBarHeight + navBarBottomInset else 0.dp
            val miniPlayerReserve = if (showChrome) 80.dp else 0.dp

            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(180)) },
            ) {
                composable(Routes.LOGIN) {
                    LoginScreen(
                        onConnected = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        onArtistClick = { navController.navigate(Routes.artist(it.id)) },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        onPlaylistClick = { navController.navigate(Routes.playlist(it.id)) },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        onArtistClick = { navController.navigate(Routes.artist(it.id)) },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onSignedOut = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            }
                        },
                        contentPaddingBottom = systemBarInset + miniPlayerReserve,
                    )
                }
                composable(Routes.ARTIST) { entry ->
                    val artistId = entry.arguments?.getString("artistId") ?: return@composable
                    ArtistDetailScreen(
                        artistId = artistId,
                        onBack = { navController.popBackStack() },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                composable(Routes.ALBUM) { entry ->
                    val albumId = entry.arguments?.getString("albumId") ?: return@composable
                    AlbumDetailScreen(
                        albumId = albumId,
                        onBack = { navController.popBackStack() },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                composable(Routes.PLAYLIST) { entry ->
                    val playlistId = entry.arguments?.getString("playlistId") ?: return@composable
                    PlaylistDetailScreen(
                        playlistId = playlistId,
                        onBack = { navController.popBackStack() },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                composable(
                    Routes.NOW_PLAYING,
                    enterTransition = { slideInVertically(tween(260)) { it } + fadeIn(tween(260)) },
                    exitTransition = { slideOutVertically(tween(220)) { it } + fadeOut(tween(220)) },
                ) {
                    NowPlayingScreen(
                        onCollapse = { navController.popBackStack() },
                        onShowQueue = { navController.navigate(Routes.QUEUE) },
                        onShowLyrics = { navController.navigate(Routes.LYRICS) },
                    )
                }
                composable(
                    Routes.LYRICS,
                    enterTransition = { slideInVertically(tween(260)) { it } + fadeIn(tween(260)) },
                    exitTransition = { slideOutVertically(tween(220)) { it } + fadeOut(tween(220)) },
                ) {
                    LyricsScreen(onCollapse = { navController.popBackStack() })
                }
                composable(
                    Routes.QUEUE,
                    enterTransition = { slideInVertically(tween(260)) { it } + fadeIn(tween(260)) },
                    exitTransition = { slideOutVertically(tween(220)) { it } + fadeOut(tween(220)) },
                ) {
                    QueueScreen(onCollapse = { navController.popBackStack() })
                }
            }

            if (showChrome) {
                MiniPlayer(
                    onExpand = { navController.navigate(Routes.NOW_PLAYING) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = systemBarInset + navBarReserve + 8.dp),
                )
            }

            if (showChrome && showBottomBar) {
                val selectedRoutes = remember(backStackEntry) {
                    backStackEntry?.destination?.hierarchy?.mapNotNull { it.route }?.toSet() ?: emptySet()
                }
                FloatingNavBar(
                    destinations = bottomDestinations,
                    selectedRoutes = selectedRoutes,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    hazeState = hazeState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = systemBarInset + navBarBottomInset)
                        .fillMaxWidth()
                        .height(navBarHeight),
                )
            }
        }
        }
    }
}
