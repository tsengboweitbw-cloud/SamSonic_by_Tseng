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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import androidx.compose.animation.core.Spring
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.lerp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.components.NavRail
import com.example.samsonic.ui.components.rememberNavRailWidth
import com.example.samsonic.ui.components.pileCard
import com.example.samsonic.ui.components.pileSwipe
import com.example.samsonic.ui.components.rememberCardPileState
import com.example.samsonic.ui.player.MiniPillMotion
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
import com.example.samsonic.ui.common.ChromeGuardArea
import com.example.samsonic.ui.common.ChromeGuardLayer
import com.example.samsonic.ui.common.LocalChromeGuard
import com.example.samsonic.ui.common.LocalWindowLayout
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
import com.example.samsonic.ui.player.RailMiniPlayerHeight
import com.example.samsonic.ui.player.ShortRailMiniPlayerHeight
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

// Between the nav rail and the mini player under it, at least.
private val RailMiniGap = 12.dp

// How much higher the mini player under the rail sits in DeX.
private val DesktopMiniLift = 24.dp

// The mini player rising into the stacked pile: unhurried, settling without a bounce.
// No overshoot either: coming back under 1 would flash the nav bar whole behind it.
private val StackEntranceSpring = spring<Float>(dampingRatio = 1f, stiffness = 260f)

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
    val navBarBottomInset = 16.dp
    // In DeX the mini player under the rail sits higher, clear of the taskbar and the window's
    // bottom edge, where a mouse heading for it would otherwise be.
    val desktopChrome = LocalWindowLayout.current.desktop
    val railMiniLift = if (desktopChrome) DesktopMiniLift else 0.dp
    // A phone on its side is short: the rail's tabs and the mini player under it are shorter.
    val phoneLandscape = LocalWindowLayout.current.phoneLandscape
    val railMiniHeight = if (phoneLandscape) ShortRailMiniPlayerHeight else RailMiniPlayerHeight
    val railTabHeight = when {
        desktopChrome -> NavRail.DesktopTabHeight
        phoneLandscape -> NavRail.ShortTabHeight
        else -> NavRail.TabHeight
    }
    // Wider than a phone, the nav bar is a rail at the side, the mini player on its own at
    // the foot of the screen; tabs fade from one to the next rather than sliding.
    val onRail by rememberUpdatedState(LocalWindowLayout.current.usesRail)
    SideEffect { tabs.fades = onRail }
    // Stacked, the nav bar (card 0) and the mini player share one place, piled; only
    // while there's a song, else the nav bar is on its own. Not beside a rail: there's no
    // nav bar at the foot of the screen to pile onto.
    val stackSetting by container.themeManager.stackChrome.collectAsStateWithLifecycle()
    val stackChrome by remember { derivedStateOf { stackSetting && !onRail } }
    val railStaysPut by container.themeManager.railStaysPut.collectAsStateWithLifecycle()
    // Changing only as music starts or stops, not with each new song.
    val player = LocalPlayerState.current
    val hasSong by remember(player) { derivedStateOf { player.currentSong != null } }
    // Switched between the two, the mini player glides down behind the nav bar into the
    // pile, or rises out of it back to its place above: 0 apart, 1 piled. Switching screens
    // (the rail coming or going as a foldable folds or opens) it's simply in its place.
    val stackTarget = if (stackChrome) 1f else 0f
    val stackAnimation = remember { Animatable(stackTarget) }
    val railWas = remember { booleanArrayOf(onRail) }
    LaunchedEffect(stackTarget, onRail) {
        if (railWas[0] != onRail) {
            railWas[0] = onRail
            stackAnimation.snapTo(stackTarget)
        } else {
            // No overshoot: past either end it would pile or part for a frame.
            stackAnimation.animateTo(stackTarget, spring(dampingRatio = 1f, stiffness = 300f))
        }
    }
    val stackAmount by stackAnimation.asState()
    // 1 while there's a song (the mini player up), 0 without, easing between as music
    // starts or stops: for what floats above the chrome to follow it (ChromeGuard.top).
    val miniPresence = animateFloatAsState(
        targetValue = if (hasSong) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 400f),
        label = "miniPresence",
    )
    // Drawn in the pile while it comes together or apart; swiped only once it has.
    val pileShown by remember { derivedStateOf { hasSong && stackAmount > 0f } }
    // The nav bar keeps its place in the pile (its layer, order and pose) for as long as
    // the layout is stacked, with or without a song, posed as if alone when there's none:
    // adding them as the first song came in rebuilt its glass, and it blinked.
    val navInPile by remember { derivedStateOf { stackAmount > 0f } }
    val piled by remember { derivedStateOf { hasSong && stackChrome && stackAmount >= 1f } }
    val chromePile = rememberCardPileState(2)
    // Piling up, the mini player goes behind; with it gone, or apart again, the nav bar is in front.
    LaunchedEffect(stackChrome) { if (stackChrome) chromePile.snapTo(0) }
    LaunchedEffect(pileShown) { if (!pileShown) chromePile.snapTo(0) }
    // How far the mini player has come in, stacked, as something starts to play: it fades
    // in from below into the front of the pile, over the nav bar. 1 at rest.
    val stackEntrance = remember { Animatable(1f) }
    // Something starting to play puts the mini player in front, fading in from the bottom
    // (from nothing, or from behind the nav bar alike), not swapping places with it.
    LaunchedEffect(player, chromePile) {
        snapshotFlow { player.playStarts }.drop(1).collectLatest {
            if (!stackChrome) return@collectLatest
            // Already in front: it stays, with only its song changing.
            if (hasSong && chromePile.front == MiniPlayerCard) return@collectLatest
            stackEntrance.snapTo(0f)
            snapshotFlow { pileShown }.first { it }
            chromePile.snapTo(MiniPlayerCard)
            stackEntrance.animateTo(1f, StackEntranceSpring)
        }
    }
    // Opening, Now Playing grows out of the mini player, so it's the card in front (and
    // takes touches) whichever way it was opened; it's what closing folds back into too.
    LaunchedEffect(chromePile, playerSheet) {
        snapshotFlow { playerSheet.progress > 0f }.collect { opening ->
            if (opening && pileShown && chromePile.front != MiniPlayerCard) chromePile.snapTo(MiniPlayerCard)
        }
    }
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
            val railWidth = rememberNavRailWidth(railLabels)
            val railReserve = if (onRail && !signingIn) railStartInset + NavRail.Margin + railWidth else 0.dp
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
                    collapsedStart = if (onRail) railStartInset + NavRail.Margin else 0.dp,
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
            if (!onRail) AnimatedVisibility(
                visible = showChrome,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .then(if (navInPile) Modifier.zIndex(if (navBarOnTop) -1f else -2f) else Modifier)
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
                        if (navInPile) {
                            Modifier
                                .pileCard(
                                    chromePile,
                                    0,
                                    navBarHeight,
                                    ignoreTouchesBehind = true,
                                    // As the mini player in front is swiped away, the nav bar
                                    // comes forward out of its place behind, whole, in step,
                                    // so it's already in place when the music stops; and goes
                                    // back as the mini player comes in over it, not all at once.
                                    weight = {
                                        if (!hasSong) {
                                            0f
                                        } else {
                                            stackAmount * (1f - playerSheet.dismissal.coerceIn(0f, 1f)) *
                                                stackEntrance.value.coerceIn(0f, 1f)
                                        }
                                    },
                                    // Cut just where the mini player is drawn as it comes in or
                                    // goes (MiniPillMotion), as clearly as it's drawn: so the nav
                                    // bar dissolves under it, never cut where it isn't yet.
                                    cut = { MiniPillMotion.alpha(1f - stackEntrance.value.coerceIn(0f, 1f), playerSheet.dismissal.coerceIn(0f, 1f)) },
                                    offsetFromFront = {
                                        -miniAboveNavPx() + MiniPillMotion.shift(
                                            1f - stackEntrance.value.coerceIn(0f, 1f),
                                            playerSheet.dismissal.coerceIn(0f, 1f),
                                            navBarHeightPx,
                                            playerSheet.dismissTravelPx,
                                        )
                                    },
                                    frontScale = {
                                        MiniPillMotion.scale(1f - stackEntrance.value.coerceIn(0f, 1f), playerSheet.dismissal.coerceIn(0f, 1f))
                                    },
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

            // On wider screens, the nav bar as a rail at the side; it slides out past the
            // screen's side in step with the player sheet opening, as the nav bar sinks.
            // Composed only beside a rail, as the nav bar only without, so it too is simply
            // there as the screen switches.
            if (onRail) AnimatedVisibility(
                visible = showChrome,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        val progress = playerSheet.progress
                        translationX = -progress * railReserve.toPx()
                        // Centred in the room above the mini player's place: whether or not
                        // there's a song (it stays put as music starts and stops), or, as set
                        // in Settings, only while there's one, rising as it comes and settling
                        // back as it goes.
                        val miniRoom = (systemBarInset + navBarBottomInset + railMiniLift + railMiniHeight + RailMiniGap).toPx()
                        val lift = if (railStaysPut) 1f else miniPresence.value * (1f - playerSheet.dismissal.coerceIn(0f, 1f))
                        // Centred between the status bar and the mini player, not the window's middle:
                        // a phone on its side has little height, and it reached under the status bar.
                        val topInset = innerPadding.calculateTopPadding().toPx()
                        translationY = (topInset * lift - miniRoom * lift) / 2
                        alpha = 1f - progress
                    }
                    .padding(start = railStartInset + NavRail.Margin),
            ) {
                FloatingNavRail(
                    destinations = bottomDestinations,
                    tabs = tabs,
                    hazeState = hazeState,
                    width = railWidth,
                    // Taller in DeX, where a window has height to spare.
                    tabHeight = railTabHeight,
                    iconSize = if (desktopChrome) NavRail.DesktopIconSize else NavRail.IconSize,
                )
            }

            // While a menu is open over a page, the chrome can't be used: a tap on it closes the menu.
            // At least as far up as the bottom fade, which fades a page's own dim out with the
            // page: beside a rail, where nothing is at the foot, the fade reaches past it.
            val guardHeight = maxOf(contentPaddingBottom, bottomFadeHeight)
            ChromeGuardLayer(chromeGuard, guardHeight, Modifier.align(Alignment.BottomCenter))
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
private fun TabHost(
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
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides entry,
        LocalLifecycleOwner provides entry,
    ) {
        Box(modifier) { content() }
    }
}


// A page opening beside the first, or the last closing: unhurried, without a bounce.
private val SplitSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
