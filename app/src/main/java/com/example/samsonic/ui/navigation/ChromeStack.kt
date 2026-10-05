package com.example.samsonic.ui.navigation

import com.example.samsonic.ui.components.CardPileState
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.components.rememberCardPileState
import com.example.samsonic.ui.player.MiniPlayerCard
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.ui.common.ChromeGuard

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import com.example.samsonic.ui.player.PlayerSheetState

/**
 * How the nav bar and the mini player are stacked: whether they are, how far the stacking
 * has got (0 apart, 1 piled), the pile they make and how the mini player comes into it. Read
 * where each is needed (some in layout and draw only), so they are states, not values.
 */
@Stable
internal class ChromeStack(
    // The setting is on, and the layout has no rail to pile against.
    val stackChrome: State<Boolean>,
    // Changing only as music starts or stops, not with each new song.
    val hasSong: State<Boolean>,
    val stackAmount: State<Float>,
    // 1 while there's a song (the mini player up), 0 without, easing between as music
    // starts or stops: for what floats above the chrome to follow it (ChromeGuard.top).
    val miniPresence: State<Float>,
    // Drawn in the pile while it comes together or apart; swiped only once it has.
    val pileShown: State<Boolean>,
    // The nav bar keeps its place in the pile (its layer, order and pose) for as long as
    // the layout is stacked, with or without a song.
    val navInPile: State<Boolean>,
    val piled: State<Boolean>,
    val chromePile: CardPileState,
    // How far the mini player has come in, stacked, as something starts to play. 1 at rest.
    val stackEntrance: Animatable<Float, AnimationVector1D>,
)

/** The [ChromeStack] for this host, [onRail] being whether the nav bar is a rail at the side. */
@Composable
internal fun rememberChromeStack(onRailState: State<Boolean>, playerSheet: PlayerSheetState): ChromeStack {
    val container = LocalAppContainer.current
    val onRail by onRailState
    // Stacked, the nav bar (card 0) and the mini player share one place, piled; only
    // while there's a song, else the nav bar is on its own. Not beside a rail: there's no
    // nav bar at the foot of the screen to pile onto.
    val stackSetting by container.themeManager.stackChrome.collectAsStateWithLifecycle()
    val stackChromeState = remember { derivedStateOf { stackSetting && !onRail } }
    val stackChrome by stackChromeState
    val player = LocalPlayerState.current
    val hasSongState = remember(player) { derivedStateOf { player.currentSong != null } }
    val hasSong by hasSongState
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
    val stackAmountState = stackAnimation.asState()
    val stackAmount by stackAmountState
    // 1 while there's a song (the mini player up), 0 without, easing between as music
    // starts or stops: for what floats above the chrome to follow it (ChromeGuard.top).
    val miniPresence = animateFloatAsState(
        targetValue = if (hasSong) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 400f),
        label = "miniPresence",
    )
    // Drawn in the pile while it comes together or apart; swiped only once it has.
    val pileShownState = remember { derivedStateOf { hasSong && stackAmount > 0f } }
    val pileShown by pileShownState
    // The nav bar keeps its place in the pile (its layer, order and pose) for as long as
    // the layout is stacked, with or without a song, posed as if alone when there's none:
    // adding them as the first song came in rebuilt its glass, and it blinked.
    val navInPileState = remember { derivedStateOf { stackAmount > 0f } }
    val navInPile by navInPileState
    val piledState = remember { derivedStateOf { hasSong && stackChrome && stackAmount >= 1f } }
    val piled by piledState
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
    return remember(player, chromePile, stackEntrance) {
        ChromeStack(
            stackChrome = stackChromeState,
            hasSong = hasSongState,
            stackAmount = stackAmountState,
            miniPresence = miniPresence,
            pileShown = pileShownState,
            navInPile = navInPileState,
            piled = piledState,
            chromePile = chromePile,
            stackEntrance = stackEntrance,
        )
    }
}
