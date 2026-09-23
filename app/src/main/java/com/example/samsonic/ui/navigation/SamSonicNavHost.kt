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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
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
import com.example.samsonic.ui.player.DrivePlayerMorph
import com.example.samsonic.ui.player.LocalPlayerMorph
import com.example.samsonic.ui.player.MiniPlayer
import com.example.samsonic.ui.player.NowPlayingScreen
import com.example.samsonic.ui.player.PlayerMorphEasing
import com.example.samsonic.ui.player.PlayerMorphMs
import com.example.samsonic.ui.player.PlayerMorphOverlay
import com.example.samsonic.ui.player.PlayerMorphState
import com.example.samsonic.ui.player.QueueScreen
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
    BottomDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
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
    // The floating nav bar stays up everywhere chrome shows - Settings is now
    // just another tab in it. Only the big player surfaces (now playing,
    // lyrics, queue - excluded via noChromeRoutes) hide it.
    val showBottomBar = showChrome

    val navTransitions = remember { NavTransitions(bottomDestinations.map { it.route }) }
    val hazeState = rememberHazeState()
    val playerMorph = remember { PlayerMorphState() }
    val navBarHeight = OneUiChrome.BarHeight
    val navBarBottomInset = 16.dp

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        CompositionLocalProvider(LocalHazeState provides hazeState, LocalPlayerMorph provides playerMorph) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The chrome keeps its resting spot while it animates out, so these ignore showChrome.
            val chromeBottomInset = innerPadding.calculateBottomPadding()
            val systemBarInset = if (showChrome) chromeBottomInset else 0.dp
            val navBarReserve = if (showChrome && showBottomBar) navBarHeight + navBarBottomInset else 0.dp
            val miniPlayerReserve = if (showChrome) OneUiChrome.BarHeight + 8.dp else 0.dp
            // Content melts into the background across the whole floating-chrome
            // zone. Full-screen player surfaces skip it - their controls live
            // at the bottom. Animated so route changes don't pop the mask.
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
                        onBack = { navController.popBackStack() },
                        onSignedOut = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            }
                        },
                        contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve,
                    )
                }
                screen(Routes.ARTIST) { entry ->
                    val artistId = entry.arguments?.getString("artistId") ?: return@screen
                    ArtistDetailScreen(
                        artistId = artistId,
                        onBack = { navController.popBackStack() },
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
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
                screen(
                    Routes.NOW_PLAYING,
                    // Only a short rise: the cover and seek bar fly in from the mini
                    // player as shared elements, the rest fades in around them.
                    enterTransition = {
                        slideInVertically(tween(PlayerMorphMs, easing = PlayerMorphEasing)) { it / 10 } +
                            fadeIn(tween(PlayerMorphMs - 120, easing = PlayerMorphEasing))
                    },
                    exitTransition = {
                        slideOutVertically(tween(PlayerMorphMs, easing = PlayerMorphEasing)) { it / 10 } +
                            fadeOut(tween(PlayerMorphMs - 160, easing = PlayerMorphEasing))
                    },
                ) {
                    NowPlayingScreen(
                        onCollapse = { navController.popBackStack() },
                        onShowQueue = { navController.navigate(Routes.QUEUE) },
                        onShowLyrics = { navController.navigate(Routes.LYRICS) },
                    )
                }
                screen(
                    Routes.LYRICS,
                    enterTransition = { slideInVertically(tween(260)) { it } + fadeIn(tween(260)) },
                    exitTransition = { slideOutVertically(tween(220)) { it } + fadeOut(tween(220)) },
                ) {
                    LyricsScreen(onCollapse = { navController.popBackStack() })
                }
                screen(
                    Routes.QUEUE,
                    enterTransition = { slideInVertically(tween(260)) { it } + fadeIn(tween(260)) },
                    exitTransition = { slideOutVertically(tween(220)) { it } + fadeOut(tween(220)) },
                ) {
                    QueueScreen(onCollapse = { navController.popBackStack() })
                }
            }
            }

            // The pill fades while its art and progress line morph into Now
            // Playing; its enter/exit is the clock for that morph.
            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(tween(PlayerMorphMs - 120, delayMillis = 120)),
                exit = fadeOut(tween(160)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = chromeBottomInset + navBarHeight + navBarBottomInset + 8.dp),
            ) {
                DrivePlayerMorph(playerMorph)
                MiniPlayer(onExpand = { navController.navigate(Routes.NOW_PLAYING) })
            }

            // Sinks below the screen edge while fading when a player surface opens.
            AnimatedVisibility(
                visible = showChrome && showBottomBar,
                enter = slideInVertically(tween(PlayerMorphMs, easing = PlayerMorphEasing)) { it } +
                    fadeIn(tween(PlayerMorphMs - 120, easing = PlayerMorphEasing)),
                exit = slideOutVertically(tween(PlayerMorphMs, easing = PlayerMorphEasing)) { it } +
                    fadeOut(tween(PlayerMorphMs - 160, easing = PlayerMorphEasing)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = chromeBottomInset + navBarBottomInset)
                    .fillMaxWidth()
                    .height(navBarHeight),
            ) {
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
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Above all chrome: the cover and progress line in flight between the two players.
            PlayerMorphOverlay(playerMorph, Modifier.fillMaxSize())
        }
        }
    }
}
