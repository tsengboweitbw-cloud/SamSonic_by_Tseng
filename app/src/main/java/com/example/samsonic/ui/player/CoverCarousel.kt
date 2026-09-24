package com.example.samsonic.ui.player

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.samsonic.model.artSeed
import com.example.samsonic.playback.PlayerState
import com.example.samsonic.ui.components.MediaArtFill
import kotlinx.coroutines.flow.filter
import kotlin.math.abs

private val PageSpacing = 12.dp
// Beyond this many pages away, jump instead of animating through every cover.
private const val MaxAnimatedJump = 2

/**
 * Symfonium-style cover carousel for Now Playing: the current cover in the
 * middle, the previous/next ones peeking in (smaller and dimmed) at the sides.
 * Swiping settles on a neighbour and plays it; track changes from anywhere
 * else (buttons, auto-advance, queue) slide the carousel to follow.
 *
 * Pages follow [PlayerState.playOrder], so with shuffle on the peeking cover
 * is still the song that will actually play next.
 *
 * [bleed] is the parent's horizontal padding: the carousel reaches past it to
 * the screen edges so the neighbours aren't clipped at the content margin.
 */
@Composable
fun CoverCarousel(
    player: PlayerState,
    cornerRadius: Dp,
    bleed: Dp,
    modifier: Modifier = Modifier,
) {
    // The timeline can lag a fresh queue by a moment; show the current cover alone meanwhile.
    val order = player.playOrder.ifEmpty { listOf(player.currentIndex) }
    val targetPage = order.indexOf(player.currentIndex).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = targetPage) { order.size }

    FollowPlayer(pagerState, targetPage)
    PlayOnSwipe(pagerState) { page ->
        val index = order.getOrNull(page)
        if (index != null && index != player.currentIndex) player.playQueueIndex(index)
    }

    // The cover is the largest square that fits: up to the full content width,
    // or shorter when the height given is tighter; then the side padding widens
    // so it stays centred and the neighbours peek into the freed margin. Sits at
    // the bottom of its space, next to the song info.
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val side = minOf(maxWidth, maxHeight).coerceAtLeast(0.dp)
        val sidePadding = bleed + (maxWidth - side) / 2
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = sidePadding),
            pageSpacing = PageSpacing,
            beyondViewportPageCount = 1,
            key = { order.getOrElse(it) { -1 - it } },
            modifier = Modifier
                .fillMaxWidth()
                .bleedHorizontally(bleed),
        ) { page ->
            val song = order.getOrNull(page)?.let { player.queue.getOrNull(it) }
            // 0 when centred, 1 when a full page away.
            val distance = abs(pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, 1f)
            val shape = RoundedCornerShape(cornerRadius)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    // Only the playing cover morphs to/from the mini player's art.
                    .then(
                        if (page == targetPage) {
                            Modifier.playerMorphAnchor(PlayerElement.Art, PlayerSurface.Full)
                        } else {
                            Modifier
                        },
                    )
                    .graphicsLayer {
                        val scale = lerp(1f, 0.86f, distance)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(1f, 0.55f, distance)
                    },
            ) {
                // Corners and shadow go on the art itself, which keeps its own proportions
                // inside the square page.
                if (song != null) {
                    MediaArtFill(
                        coverArt = song.coverArt,
                        colorSeed = song.id.artSeed(),
                        fit = true,
                        shape = shape,
                        shadowElevation = 16.dp,
                    )
                }
            }
        }
    }
}

/** Slides the carousel to [targetPage] whenever the player moves on its own. */
@Composable
private fun FollowPlayer(pagerState: PagerState, targetPage: Int) {
    LaunchedEffect(targetPage) {
        if (pagerState.currentPage == targetPage || pagerState.isScrollInProgress) return@LaunchedEffect
        if (abs(pagerState.currentPage - targetPage) <= MaxAnimatedJump) {
            pagerState.animateScrollToPage(targetPage)
        } else {
            pagerState.scrollToPage(targetPage)
        }
    }
}

/**
 * Calls [onSettled] with the page a user swipe came to rest on. Only swipes
 * count: programmatic scrolls (following the player, or re-ordering pages when
 * shuffle toggles) must never start a different song.
 */
@Composable
private fun PlayOnSwipe(pagerState: PagerState, onSettled: (Int) -> Unit) {
    val currentOnSettled by rememberUpdatedState(onSettled)
    var userSwiped by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) userSwiped = true
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .filter { !it && userSwiped }
            .collect {
                userSwiped = false
                currentOnSettled(pagerState.currentPage)
            }
    }
}

/** Measures [amount] wider on each side than the parent allows and re-centres. */
private fun Modifier.bleedHorizontally(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
        ),
    )
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-extra / 2, 0)
    }
}
