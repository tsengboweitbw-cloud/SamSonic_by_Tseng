package com.example.samsonic.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
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
import com.example.samsonic.ui.theme.LocalChromeBlurScale
import com.example.samsonic.ui.theme.ChromeBlurScale
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.lerp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.components.PileStep
import com.example.samsonic.ui.components.pileCard
import com.example.samsonic.ui.components.pileSwipe
import com.example.samsonic.ui.components.rememberCardPileState
import com.example.samsonic.ui.player.MiniPlayerCard
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.ui.auth.LoginScreen
import com.example.samsonic.ui.common.ArtTransitions
import com.example.samsonic.ui.common.ChromeGuard
import com.example.samsonic.ui.common.ChromeGuardLayer
import com.example.samsonic.ui.common.LocalChromeGuard
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

// Adding a server slides in over the tabs as a detail page does.
private val AddServerEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@OptIn(ExperimentalSharedTransitionApi::class)
// After starting, how long before the tabs not yet shown are built, so it's out of the way of the first screen.
private const val TabWarmUpDelayMillis = 1500L

@Composable
fun SamSonicNavHost(lastTab: LastTab) {
    val container = LocalAppContainer.current

    // Read once: the host is rebuilt whenever the music source changes (see MainActivity),
    // so it starts on the tab you were under (Settings, where sources are switched and
    // added), signing in for the first time lands on Home, and leaving the last source
    // lands back on sign in.
    val signingIn = remember { container.sources.active.value == null }
    val tabRoutes = remember { bottomDestinations.map { it.route } }
    val tabs = rememberMainTabs(tabRoutes, initialRoute = lastTab.route)
    // Adding a server (from Settings): sign in, over the tabs.
    var addingServer by rememberSaveable { mutableStateOf(false) }

    // The floating chrome (nav bar, mini player) shows everywhere but sign in.
    val showChrome = !signingIn && !addingServer
    if (!signingIn) SideEffect { lastTab.route = tabs.currentRoute }

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
    val chromeGuard = remember { ChromeGuard() }
    val navBarHeight = OneUiChrome.BarHeight
    val navBarBottomInset = 16.dp
    // Stacked, the nav bar (card 0) and the mini player share one place, piled; only
    // while there's a song, else the nav bar is on its own.
    val stackChrome by container.themeManager.stackChrome.collectAsStateWithLifecycle()
    // Changing only as music starts or stops, not with each new song.
    val player = LocalPlayerState.current
    val hasSong by remember(player) { derivedStateOf { player.currentSong != null } }
    // Switched between the two, the mini player glides down behind the nav bar into the
    // pile, or rises out of it back to its place above: 0 apart, 1 piled.
    val stackAmount by animateFloatAsState(
        targetValue = if (stackChrome) 1f else 0f,
        // No overshoot: past either end it would pile or part for a frame.
        animationSpec = spring(dampingRatio = 1f, stiffness = 300f),
        label = "stackChrome",
    )
    // Drawn in the pile while it comes together or apart; swiped only once it has.
    val pileShown by remember { derivedStateOf { hasSong && stackAmount > 0f } }
    val piled by remember { derivedStateOf { hasSong && stackChrome && stackAmount >= 1f } }
    val chromePile = rememberCardPileState(2)
    // Piling up, the mini player goes behind; with it gone, or apart again, the nav bar is in front.
    LaunchedEffect(stackChrome) { if (stackChrome) chromePile.snapTo(0) }
    LaunchedEffect(pileShown) { if (!pileShown) chromePile.snapTo(0) }
    // How far above the nav bar the mini player's own place is: all the way apart, none piled.
    val apartPx = with(LocalDensity.current) { (navBarHeight + 8.dp).toPx() }
    val miniAboveNavPx = { apartPx * (1f - stackAmount) }
    // Which of the two is drawn over the other, changing only as one goes behind; and
    // the player sheet goes over both as it opens.
    val navBarOnTop by remember(chromePile) { derivedStateOf { chromePile.zIndex(0) > chromePile.zIndex(MiniPlayerCard) } }
    val sheetOpening by remember(playerSheet) { derivedStateOf { playerSheet.progress > 0f } }
    // The chrome's glass blurs a smaller copy while it sits still, full size while it moves.
    val chromeBlurScale = if (sheetOpening) null else ChromeBlurScale

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        CompositionLocalProvider(
            LocalHazeState provides hazeState,
            LocalAddToPlaylist provides addToPlaylist.takeIf { canEditPlaylists },
            LocalChromeGuard provides chromeGuard,
            LocalChromeBlurScale provides chromeBlurScale,
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The chrome keeps its resting spot while it animates out, so these ignore showChrome.
            val chromeBottomInset = innerPadding.calculateBottomPadding()
            val systemBarInset = if (showChrome) chromeBottomInset else 0.dp
            val navBarReserve = if (showChrome) navBarHeight + navBarBottomInset else 0.dp
            // Stacked, only the peeking edge of the card behind rises above the nav bar.
            // Straight to the new layout's, not animated with it: the padding reaches every
            // tab's pages, and changing it each frame would rebuild them all.
            val miniPlayerReserve = when {
                !showChrome -> 0.dp
                stackChrome -> PileStep
                else -> OneUiChrome.BarHeight + 8.dp
            }
            val contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve
            // Content melts into the background near the bottom edge: 1.5 times the
            // space below the nav bar (its bottom gap and the system bar), so the
            // fade reaches a little way up behind the bar. Above that it stays
            // solid behind the glass. Sign in skips it. Animated so it doesn't pop
            // as adding a server comes and goes.
            val bottomFadeHeight by animateDpAsState(
                targetValue = if (showChrome) (systemBarInset + navBarBottomInset) * 1.5f else 0.dp,
                animationSpec = tween(260),
                label = "bottomFade",
            )

            // Back from a tab's first page goes Home, as it did when tabs stacked on
            // Home. Composed ahead of the tabs, so their own back (a page to pop, a
            // panel to close) comes first.
            val atTabRoot = tabs.controllers[tabs.pager.currentPage]
                .currentBackStackEntryAsState().value?.destination?.route == tabs.routes[tabs.pager.currentPage]
            BackHandler(enabled = showChrome && tabs.pager.currentPage != 0 && atTabRoot && !playerSheet.isExpanded) {
                tabs.select(Routes.HOME)
            }

            // graphicsLayer() forces this whole subtree (including any
            // LazyColumn/LazyVerticalGrid screens inside the tabs) to
            // composite into one flattened layer before hazeSource snapshots
            // it - LazyColumn content otherwise has known gaps in Haze's
            // capture (text and item edges stay sharp/unblurred).
            // Beneath the piled chrome, which is ordered by zIndex (see below).
            Box(modifier = Modifier.zIndex(-3f).fillMaxSize().bottomFade(bottomFadeHeight, MaterialTheme.colorScheme.background).graphicsLayer().hazeSource(hazeState)) {
            // Inside the haze source, so the frosted bars pick up its color as they blur it.
            AmbientGlow()
            // Covers and pictures travel from the card tapped to the page it opens (see sharedArt).
            SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this, LocalArtTransitions provides artTransitions) {
                if (signingIn) {
                    LoginScreen()
                } else {
                    // The tabs side by side, slid between by the nav bar as the Library slides
                    // between its own tabs. Only the nav bar moves them: a swipe on the pages
                    // is left to what's on them (Library's tabs, the rows' swipe actions).
                    // A tab is up once shown, and every tab soon after the app starts (see
                    // MainTabs.visited), so a switch slides past pages already built.
                    LaunchedEffect(tabs) {
                        snapshotFlow { tabs.pager.targetPage }.collect { tabs.visited[it] = true }
                    }
                    LaunchedEffect(tabs) {
                        delay(TabWarmUpDelayMillis)
                        tabs.warmUp(gapMillis = 400)
                    }
                    HorizontalPager(
                        state = tabs.pager,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = false,
                        // Every tab stays composed; only what's on screen is drawn.
                        beyondViewportPageCount = tabRoutes.size - 1,
                        key = { tabRoutes[it] },
                    ) { page ->
                        if (!tabs.visited[page]) {
                            Box(Modifier.fillMaxSize())
                            return@HorizontalPager
                        }
                        // Back reaches only the tab on screen, and none while something
                        // covers the tabs (Now Playing, adding a server) and handles it.
                        val back = rememberNavigationEventDispatcherOwner(
                            enabled = page == tabs.pager.currentPage && !playerSheet.isExpanded && !addingServer,
                        )
                        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides back) {
                            TabHost(
                                route = tabRoutes[page],
                                navController = tabs.controllers[page],
                                tabs = tabs,
                                searchSession = searchSession,
                                onAddServer = { addingServer = true },
                                contentPaddingBottom = contentPaddingBottom,
                            )
                        }
                    }
                }
            }
            }

            AnimatedVisibility(
                visible = addingServer,
                enter = slideInHorizontally(tween(340, easing = AddServerEasing)) { it / 5 } +
                    fadeIn(tween(260, delayMillis = 40, easing = AddServerEasing)),
                exit = slideOutHorizontally(tween(340, easing = AddServerEasing)) { it / 5 } +
                    fadeOut(tween(160, easing = AddServerEasing)),
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AmbientGlow()
                    LoginScreen(onBack = { addingServer = false })
                }
            }
            }
            BackHandler(enabled = addingServer) { addingServer = false }

            // The player: the mini player's pill, which drags up into Now Playing.
            // Under the nav bar, so the bar can sink away over it as it grows.
            if (showChrome) {
                // From Now Playing, always under the Library tab: slid to first (with
                // whatever it had open), so the nav bar and back lead through Library.
                val openFromPlayer = remember(tabs) { { route: String -> tabs.open(Routes.LIBRARY, route); Unit } }
                PlayerSheet(
                    sheet = playerSheet,
                    // Stacked, in the nav bar's own place and width.
                    collapsedBottom = chromeBottomInset + navBarBottomInset + lerp(navBarHeight + 8.dp, 0.dp, stackAmount),
                    collapsedMargin = lerp(12.dp, 16.dp, stackAmount),
                    pile = chromePile.takeIf { pileShown },
                    pileWeight = { stackAmount },
                    pileOffset = miniAboveNavPx,
                    modifier = if (pileShown) Modifier.zIndex(if (sheetOpening) -0.5f else if (navBarOnTop) -2f else -1f) else Modifier,
                    onAlbumClick = remember(openFromPlayer) { { openFromPlayer(Routes.album(it)) } },
                    onArtistClick = remember(openFromPlayer) { { openFromPlayer(Routes.artist(it)) } },
                )
            }

            // Sinks below the screen edge in step with the player sheet opening.
            AnimatedVisibility(
                visible = showChrome,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .then(if (pileShown) Modifier.zIndex(if (navBarOnTop) -1f else -2f) else Modifier)
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        val progress = playerSheet.progress
                        translationY = progress * (navBarHeight + navBarBottomInset + chromeBottomInset).toPx()
                        alpha = 1f - progress
                    }
                    .padding(horizontal = 16.dp)
                    .padding(bottom = chromeBottomInset + navBarBottomInset)
                    .fillMaxWidth()
                    .height(navBarHeight)
                    // Piled with the mini player: posed in the pile, and a swipe up sends it
                    // to the back; behind, it takes no touches.
                    .then(
                        if (pileShown) {
                            Modifier
                                .pileCard(
                                    chromePile,
                                    0,
                                    navBarHeight,
                                    ignoreTouchesBehind = true,
                                    weight = { stackAmount },
                                    offsetFromFront = { -miniAboveNavPx() },
                                )
                                .then(if (piled) Modifier.pileSwipe(chromePile, navBarHeight) else Modifier)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                FloatingNavBar(
                    destinations = bottomDestinations,
                    tabs = tabs,
                    hazeState = hazeState,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // While a menu is open over a page, the chrome can't be used: a tap on it closes the menu.
            ChromeGuardLayer(chromeGuard, contentPaddingBottom, Modifier.align(Alignment.BottomCenter))

            // Over everything, the nav bar included; it grows out of the long-pressed song row.
            if (canEditPlaylists) AddToPlaylistMenu(addToPlaylist, hazeState)
        }
        }
    }
}

/**
 * One tab's pages: its first page ([route]) and those opened from it, with their
 * own back stack ([navController]), so each tab keeps what it had open.
 */
@Composable
private fun TabHost(
    route: String,
    navController: NavHostController,
    tabs: MainTabs,
    searchSession: SearchSession,
    onAddServer: () -> Unit,
    contentPaddingBottom: Dp,
) {
    NavHost(
        navController = navController,
        startDestination = route,
        modifier = Modifier.fillMaxSize(),
        enterTransition = NavTransitions.enter,
        exitTransition = NavTransitions.exit,
        popEnterTransition = NavTransitions.popEnter,
        popExitTransition = NavTransitions.popExit,
    ) {
        when (route) {
            Routes.HOME -> {
                screen(Routes.HOME) {
                    HomeScreen(
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                        onShelfClick = { navController.navigate(Routes.shelf(it.key)) },
                        // A refresh starts Search over too: the query, and whatever was
                        // opened from its results.
                        onRefresh = {
                            searchSession.clear()
                            tabs.reset(Routes.SEARCH)
                        },
                        contentPaddingBottom = contentPaddingBottom,
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
            Routes.LIBRARY -> screen(Routes.LIBRARY) {
                LibraryScreen(
                    onArtistClick = { navController.navigate(Routes.artist(it.id)) },
                    onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                    onPlaylistClick = { navController.navigate(Routes.playlist(it.id)) },
                    onGenreClick = { navController.navigate(Routes.genre(it.name)) },
                    contentPaddingBottom = contentPaddingBottom,
                )
            }
            Routes.SEARCH -> screen(Routes.SEARCH) {
                SearchScreen(
                    session = searchSession,
                    onArtistClick = { navController.navigate(Routes.artist(it.id)) },
                    onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                    contentPaddingBottom = contentPaddingBottom,
                )
            }
            Routes.SETTINGS -> screen(Routes.SETTINGS) {
                SettingsScreen(
                    onAddServer = onAddServer,
                    contentPaddingBottom = contentPaddingBottom,
                )
            }
        }
        detailScreens(navController, contentPaddingBottom = contentPaddingBottom)
    }
}
