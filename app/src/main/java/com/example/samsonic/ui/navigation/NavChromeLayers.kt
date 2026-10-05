package com.example.samsonic.ui.navigation

import androidx.compose.ui.unit.Dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.zIndex
import com.example.samsonic.ui.components.NavRail
import com.example.samsonic.ui.components.pileCard
import com.example.samsonic.ui.components.pileSwipe
import com.example.samsonic.ui.player.MiniPillMotion
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import com.example.samsonic.ui.components.SelectedTabExtraWeight

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.getValue
import com.example.samsonic.ui.player.PlayerSheetState
import dev.chrisbanes.haze.HazeState

/**
 * The nav bar at the foot of a phone's screen. It sinks below the screen edge in step with
 * the player sheet opening. Composed only for a phone's layout, so switching screens
 * (folding shut, a window narrowing) shows it in place at once, not sliding in: it slides
 * only as signing in comes and goes. Stacked, it is a card of the pile with the mini player.
 */
@Composable
internal fun BoxScope.NavBarLayer(
    showChrome: Boolean,
    stack: ChromeStack,
    playerSheet: PlayerSheetState,
    tabs: MainTabs,
    hazeState: HazeState,
    navBarOnTop: Boolean,
    navBarHeight: Dp,
    navBarBottomInset: Dp,
    chromeBottomInset: Dp,
    miniAboveNavPx: () -> Float,
    navBarHeightPx: Float,
) {
    val navInPile by stack.navInPile
    val piled by stack.piled
    val hasSong by stack.hasSong
    val stackAmount by stack.stackAmount
    val chromePile = stack.chromePile
    val stackEntrance = stack.stackEntrance
    AnimatedVisibility(
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
}

/**
 * On wider screens, the nav bar as a rail at the side; it slides out past the screen's side
 * in step with the player sheet opening, as the nav bar sinks. Composed only beside a rail,
 * as the nav bar only without, so it too is simply there as the screen switches.
 */
@Composable
internal fun BoxScope.NavRailLayer(
    showChrome: Boolean,
    stack: ChromeStack,
    playerSheet: PlayerSheetState,
    tabs: MainTabs,
    hazeState: HazeState,
    railStaysPut: Boolean,
    railReserve: Dp,
    railStartInset: Dp,
    railMargin: Dp,
    railWidth: Dp,
    railMiniLift: Dp,
    railMiniHeight: Dp,
    railMiniGap: Dp,
    railTabHeight: Dp,
    systemBarInset: Dp,
    navBarBottomInset: Dp,
    topInset: Dp,
    phoneLandscape: Boolean,
    desktopChrome: Boolean,
) {
    val miniPresence = stack.miniPresence
    AnimatedVisibility(
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
                val miniRoom = (systemBarInset + navBarBottomInset + railMiniLift + railMiniHeight + railMiniGap).toPx()
                val lift = if (railStaysPut) 1f else miniPresence.value * (1f - playerSheet.dismissal.coerceIn(0f, 1f))
                // Centred between the status bar and the mini player, not the window's middle:
                // a phone on its side has little height, and it reached under the status bar.
                val topInsetPx = topInset.toPx()
                translationY = (topInsetPx * lift - miniRoom * lift) / 2
                alpha = 1f - progress
            }
            .padding(start = railStartInset + railMargin),
    ) {
        // On a phone on its side, the tabs take the height the mini player leaves
        // (the rail's length is tabHeight * (count + extra) + inset * 2).
        val tabHeight = if (phoneLandscape) {
            val room = LocalConfiguration.current.screenHeightDp.dp - topInset -
                systemBarInset - navBarBottomInset - railMiniLift - railMiniHeight - railMiniGap - 8.dp
            ((room - NavRail.Inset * 2) / (bottomDestinations.size + SelectedTabExtraWeight))
                .coerceIn(NavRail.ShortTabHeight, NavRail.TabHeight)
        } else {
            railTabHeight
        }
        FloatingNavRail(
            destinations = bottomDestinations,
            tabs = tabs,
            hazeState = hazeState,
            width = railWidth,
            // Taller in DeX, where a window has height to spare.
            tabHeight = tabHeight,
            iconSize = if (desktopChrome) NavRail.DesktopIconSize else NavRail.IconSize,
        )
    }
}
