package com.example.samsonic.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.horizontalScrollFade

/** Fade for list items appearing or leaving when their data set changes. */
val ListItemFade: FiniteAnimationSpec<Float> = tween(durationMillis = 220)

/** Slide for list items that survive a data change but move to a new spot. */
val ListItemMove: FiniteAnimationSpec<IntOffset> =
    spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)

/**
 * A horizontally scrolling row of cards (albums, artists...). Every card is
 * keyed by [key], so when [items] changes new cards fade in, removed ones fade
 * out, and cards in both sets slide to their new spot.
 */
@Composable
fun <T> HorizontalCarousel(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    // Lazy keys must be unique; a repeated item (e.g. the same album listed
    // twice) gets its occurrence number appended instead of crashing the row.
    val keys = remember(items) {
        val seen = HashMap<Any, Int>()
        items.map { item ->
            val base = key(item)
            val count = seen.merge(base, 1, Int::plus)!!
            if (count == 1) base else "$base#$count"
        }
    }

    val rowState = rememberLazyListState()
    // A lazy list anchors on its first visible key, so when a new data set
    // keeps an old card, the row would jump sideways to keep that card in
    // place and push the new cards off screen. Snap back to the start in the
    // same frame instead, so the new cards fade in right where the old ones fade out.
    remember(keys) { rowState.requestScrollToItem(0) }

    LazyRow(
        modifier = modifier.horizontalScrollFade(rowState),
        state = rowState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items.size, key = { keys[it] }) { index ->
            Box(modifier = Modifier.animateItem(ListItemFade, ListItemMove, ListItemFade)) {
                itemContent(items[index])
            }
        }
    }
}
