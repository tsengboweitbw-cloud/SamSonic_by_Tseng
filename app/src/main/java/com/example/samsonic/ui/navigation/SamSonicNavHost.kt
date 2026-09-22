package com.example.samsonic.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private data class BottomDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

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
    val showBottomBar = currentRoute == null || bottomDestinations.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showChrome && showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    bottomDestinations.forEach { destination ->
                        val selected = backStackEntry?.destination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            val bottomChromeInset = if (showChrome) innerPadding.calculateBottomPadding() else 0.dp
            val miniPlayerReserve = if (showChrome) 74.dp else 0.dp

            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
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
                        contentPaddingBottom = bottomChromeInset + miniPlayerReserve,
                    )
                }
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        onArtistClick = { navController.navigate(Routes.artist(it.id)) },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        onPlaylistClick = { navController.navigate(Routes.playlist(it.id)) },
                        contentPaddingBottom = bottomChromeInset + miniPlayerReserve,
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        onArtistClick = { navController.navigate(Routes.artist(it.id)) },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        contentPaddingBottom = bottomChromeInset + miniPlayerReserve,
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
                    )
                }
                composable(Routes.ARTIST) { entry ->
                    val artistId = entry.arguments?.getString("artistId") ?: return@composable
                    ArtistDetailScreen(
                        artistId = artistId,
                        onBack = { navController.popBackStack() },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                    )
                }
                composable(Routes.ALBUM) { entry ->
                    val albumId = entry.arguments?.getString("albumId") ?: return@composable
                    AlbumDetailScreen(albumId = albumId, onBack = { navController.popBackStack() })
                }
                composable(Routes.PLAYLIST) { entry ->
                    val playlistId = entry.arguments?.getString("playlistId") ?: return@composable
                    PlaylistDetailScreen(playlistId = playlistId, onBack = { navController.popBackStack() })
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
                        .padding(bottom = bottomChromeInset + 8.dp),
                )
            }
        }
    }
}
