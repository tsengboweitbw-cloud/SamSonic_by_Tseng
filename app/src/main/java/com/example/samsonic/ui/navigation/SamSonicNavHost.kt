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
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.layout
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberUpdatedState
import com.example.samsonic.ui.theme.LocalChromeBlurScale
import com.example.samsonic.ui.theme.ChromeBlurScale
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.lerp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.ui.components.NavRail
import com.example.samsonic.ui.components.rememberNavRailWidth
import com.example.samsonic.ui.player.MiniPlayerCard
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.ui.auth.LoginScreen
import com.example.samsonic.ui.common.ArtTransitions
import com.example.samsonic.ui.common.ChromeGuard
import com.example.samsonic.ui.common.ChromeGuardArea
import com.example.samsonic.ui.common.ChromeGuardLayer
import com.example.samsonic.ui.common.LocalChromeGuard
import com.example.samsonic.ui.common.LocalWindowLayout
import com.example.samsonic.ui.common.LocalArtTransitions
import com.example.samsonic.ui.common.LocalSharedTransitionScope
import com.example.samsonic.ui.library.AddToPlaylistMenu
import com.example.samsonic.ui.library.LocalAddToPlaylist
import com.example.samsonic.ui.library.rememberAddToPlaylistState
import com.example.samsonic.ui.player.PlayerSheet
import com.example.samsonic.ui.player.RailMiniPlayerHeight
import com.example.samsonic.ui.player.rememberPlayerSheetState
import com.example.samsonic.ui.search.SearchSession
import com.example.samsonic.ui.theme.AmbientGlow
import com.example.samsonic.ui.theme.LocalHazeState
import com.example.samsonic.ui.theme.OneUiChrome
import com.example.samsonic.ui.theme.bottomFade
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

data class BottomDestination(val route: String, @StringRes val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

internal val bottomDestinations = listOf(
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

// Between the nav rail and the mini player under it, at least.
private val RailMiniGap = 12.dp

// How much higher the mini player under the rail sits in DeX.
private val DesktopMiniLift = 24.dp

// The mini player rising into the stacked pile: unhurried, settling without a bounce.
// No overshoot either: coming back under 1 would flash the nav bar whole behind it.
internal val StackEntranceSpring = spring<Float>(dampingRatio = 1f, stiffness = 260f)

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
    val searchSession = rememberSaveable(saver = SearchSession.Saver) { SearchSession() }
    // Offered only where there are playlists to add to; the host is rebuilt with the source.
    val addToPlaylist = rememberAddToPlaylistState()
    val canEditPlaylists = remember { container.repository.canEditPlaylists }
    val chromeGuard = remember { ChromeGuard() }
    val navBarHeight = OneUiChrome.BarHeight
    // A phone on its side is short: the rail's tabs and the mini player under it are shorter,
    // and the gaps around them.
    val phoneLandscape = LocalWindowLayout.current.phoneLandscape
    val navBarBottomInset = if (phoneLandscape) 8.dp else 16.dp
    val railMiniGap = if (phoneLandscape) 8.dp else RailMiniGap
    // In DeX the mini player under the rail sits higher, clear of the taskbar and the window's
    // bottom edge, where a mouse heading for it would otherwise be.
    val desktopChrome = LocalWindowLayout.current.desktop
    val railMiniLift = if (desktopChrome) DesktopMiniLift else 0.dp
    val railTabHeight = when {
        desktopChrome -> NavRail.DesktopTabHeight
        phoneLandscape -> NavRail.ShortTabHeight
        else -> NavRail.TabHeight
    }
    // Wider than a phone, the nav bar is a rail at the side, the mini player on its own at
    // the foot of the screen; tabs fade from one to the next rather than sliding.
    val onRailState = rememberUpdatedState(LocalWindowLayout.current.usesRail)
    val onRail by onRailState
    SideEffect { tabs.fades = onRail }
    val railStaysPut by container.themeManager.railStaysPut.collectAsStateWithLifecycle()
    // Stacked, the nav bar and the mini player share one place, piled (see ChromeStack).
    val stack = rememberChromeStack(onRailState, playerSheet)
    val stackChrome by stack.stackChrome
    val stackAmount by stack.stackAmount
    val pileShown by stack.pileShown
    val miniPresence = stack.miniPresence
    val chromePile = stack.chromePile
    val stackEntrance = stack.stackEntrance
    // How far above the nav bar the mini player's own place is: all the way apart, none piled.
    val apartPx = with(LocalDensity.current) { (navBarHeight + 8.dp).toPx() }
    val navBarHeightPx = with(LocalDensity.current) { navBarHeight.toPx() }
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
            // Beside a rail, nothing is at the foot but its gap: the mini player is under the rail.
            val navBarReserve = when {
                !showChrome -> 0.dp
                onRail -> navBarBottomInset
                else -> navBarHeight + navBarBottomInset
            }
            // Stacked, nothing rises above the nav bar: the card behind peeks out below it,
            // into the gap over the screen's edge. Straight to the new layout's, not animated
            // with it: the padding reaches every tab's pages, and changing it each frame would
            // rebuild them all.
            val miniPlayerReserve = when {
                !showChrome -> 0.dp
                stackChrome || onRail -> 0.dp
                else -> OneUiChrome.BarHeight + 8.dp
            }
            val contentPaddingBottom = systemBarInset + navBarReserve + miniPlayerReserve
            // The rail's room at the side, past any system bar or cutout there: the tabs'
            // pages are laid out beside it, and the mini player sits under it.
            val layoutDirection = LocalLayoutDirection.current
            val railStartInset = innerPadding.calculateStartPadding(layoutDirection)
            // As wide as its longest label needs (larger text in DeX), and the room beside it with it.
            val railLabels = bottomDestinations.map { stringResource(it.label) }
            val railWidth = rememberNavRailWidth(railLabels, if (phoneLandscape) NavRail.ShortWidth else NavRail.Width)
            // On its side, the mini player is a circle as wide as the rail: the cover with the progress round it.
            val railMiniHeight = if (phoneLandscape) railWidth else RailMiniPlayerHeight
            val railMargin =if (phoneLandscape) NavRail.ShortMargin else NavRail.Margin
            val railReserve = if (onRail && !signingIn) railStartInset + railMargin + railWidth else 0.dp
            // The live top of the highest bar, for what floats just above it (a page's docked
            // play buttons): the nav bar's, plus the mini player's rise above it while there's
            // a song, apart or piled, easing as it comes or goes and following a swipe away.
            // Beside a rail, just the foot's gap: the mini player is at the side, under the rail.
            val density = LocalDensity.current
            SideEffect {
                chromeGuard.top = {
                    with(density) {
                        if (!showChrome) {
                            0f
                        } else if (onRail) {
                            (systemBarInset + navBarBottomInset).toPx()
                        } else {
                            val nav = (systemBarInset + navBarBottomInset + navBarHeight).toPx()
                            // Piled, the card behind peeks out below, so the front card's top is the top.
                            val rise = lerp(OneUiChrome.BarHeight + 8.dp, 0.dp, stackAmount).toPx()
                            nav + rise * miniPresence.value * (1f - playerSheet.dismissal.coerceIn(0f, 1f))
                        }
                    }
                }
            }
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = railReserve)
                            // Beside the rail, a switch fades one tab out and the next in.
                            .graphicsLayer { alpha = tabs.fade.value },
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
                    // Stacked, in the nav bar's own place and width. Beside a rail, under the
                    // rail at the foot of the screen, as narrow as the rail and standing on end.
                    collapsedBottom = chromeBottomInset + navBarBottomInset +
                        if (onRail) railMiniLift else lerp(navBarHeight + 8.dp, 0.dp, stackAmount),
                    collapsedMargin = lerp(12.dp, 16.dp, stackAmount),
                    collapsedStart = if (onRail) railStartInset + railMargin else 0.dp,
                    collapsedWidth = railWidth.takeIf { onRail },
                    collapsedHeight = if (onRail) railMiniHeight else OneUiChrome.BarHeight,
                    pile = chromePile.takeIf { pileShown },
                    pileWeight = { stackAmount },
                    entrance = { stackEntrance.value },
                    pileOffset = miniAboveNavPx,
                    modifier = if (pileShown) Modifier.zIndex(if (sheetOpening) -0.5f else if (navBarOnTop) -2f else -1f) else Modifier,
                    onAlbumClick = remember(openFromPlayer) { { openFromPlayer(Routes.album(it)) } },
                    onArtistClick = remember(openFromPlayer) { { openFromPlayer(Routes.artist(it)) } },
                )
            }

            // Sinks below the screen edge in step with the player sheet opening. Beside a
            // rail it isn't there. Composed only for a phone's layout, so switching screens
            // (folding shut, a window narrowing) shows it in place at once, not sliding in:
            // it slides only as signing in comes and goes.
            if (!onRail) {
                NavBarLayer(
                    showChrome = showChrome,
                    stack = stack,
                    playerSheet = playerSheet,
                    tabs = tabs,
                    hazeState = hazeState,
                    navBarOnTop = navBarOnTop,
                    navBarHeight = navBarHeight,
                    navBarBottomInset = navBarBottomInset,
                    chromeBottomInset = chromeBottomInset,
                    miniAboveNavPx = miniAboveNavPx,
                    navBarHeightPx = navBarHeightPx,
                )
            }

            if (onRail) {
                NavRailLayer(
                    showChrome = showChrome,
                    stack = stack,
                    playerSheet = playerSheet,
                    tabs = tabs,
                    hazeState = hazeState,
                    railStaysPut = railStaysPut,
                    railReserve = railReserve,
                    railStartInset = railStartInset,
                    railMargin = railMargin,
                    railWidth = railWidth,
                    railMiniLift = railMiniLift,
                    railMiniHeight = railMiniHeight,
                    railMiniGap = railMiniGap,
                    railTabHeight = railTabHeight,
                    systemBarInset = systemBarInset,
                    navBarBottomInset = navBarBottomInset,
                    topInset = innerPadding.calculateTopPadding(),
                    phoneLandscape = phoneLandscape,
                    desktopChrome = desktopChrome,
                )
            }

            // While a menu is open over a page, the chrome can't be used: a tap on it closes the menu.
            // At least as far up as the bottom fade, which fades a page's own dim out with the
            // page: beside a rail, where nothing is at the foot, the fade reaches past it.
            val guardHeight = maxOf(contentPaddingBottom, bottomFadeHeight)
            ChromeGuardLayer(
                chromeGuard,
                guardHeight,
                Modifier.align(Alignment.BottomCenter),
                alwaysDimWidth = if (onRail && showChrome) railReserve else 0.dp,
            )
            if (onRail && showChrome) {
                // Down to the foot's layer, not over it: two dims there would darken it twice.
                ChromeGuardArea(
                    chromeGuard,
                    Modifier.align(Alignment.TopStart).padding(bottom = guardHeight).fillMaxHeight().width(railReserve),
                )
            }

            // Over everything, the nav bar included; it grows out of the long-pressed song row.
            if (canEditPlaylists) AddToPlaylistMenu(addToPlaylist, hazeState)
        }
        }
    }
}
