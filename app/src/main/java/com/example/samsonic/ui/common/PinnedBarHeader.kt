package com.example.samsonic.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * A page title with a floating glass bar under it (the Search page's pattern,
 * for pages whose content is its own scrolling list): scrolling up slides the
 * title away first while the bar rides up with it, then the bar stays pinned
 * [pinnedTop] below the status bar - where the title started - and the content
 * scrolls on beneath it. Both come back once the content is scrolled to its top.
 *
 * [content] runs behind the bar and gets the top padding that keeps its first
 * row clear of it at rest. The bar receives a haze source of just that content,
 * since the whole page already sits inside the NavHost's own source.
 */
@Composable
fun PinnedBarHeader(
    title: @Composable () -> Unit,
    bar: @Composable (HazeState) -> Unit,
    modifier: Modifier = Modifier,
    state: PinnedBarHeaderState = rememberPinnedBarHeaderState(),
    pinnedTop: Dp = 12.dp,
    content: @Composable (contentTopPadding: Dp) -> Unit,
) {
    val contentHaze = rememberHazeState()
    val density = LocalDensity.current
    var barHeight by remember { mutableIntStateOf(0) }

    val connection = remember(state) {
        object : NestedScrollConnection {
            // Scrolling up: the title leaves first, then the content scrolls.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                return moveBy(available.y)
            }

            // Scrolling down: only once the content is back at its top.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                return moveBy(available.y)
            }

            private fun moveBy(dy: Float): Offset {
                val next = (state.offset + dy).coerceIn(-state.range, 0f)
                val consumed = next - state.offset
                state.offset = next
                return Offset(0f, consumed)
            }
        }
    }

    val barHeightDp = with(density) { barHeight.toDp() }
    Layout(
        contents = listOf(
            title,
            { Box(Modifier.onSizeChanged { barHeight = it.height }) { bar(contentHaze) } },
            {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Rows melt away as they slide up past the bar's sides;
                        // outside hazeSource, so the bar still blurs them unfaded.
                        .topFade(barHeight.toFloat())
                        .graphicsLayer()
                        .hazeSource(contentHaze),
                ) {
                    content(barHeightDp + 8.dp)
                }
            },
        ),
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .nestedScroll(connection)
            .clipToBounds(),
    ) { (titleMeasurables, barMeasurables, contentMeasurables), constraints ->
        val loose = constraints.copy(minHeight = 0)
        val titlePlaceable = titleMeasurables.first().measure(loose)
        val barPlaceable = barMeasurables.first().measure(loose)
        val pinned = pinnedTop.roundToPx()
        state.range = (titlePlaceable.height - pinned).coerceAtLeast(0).toFloat()
        // Sized for the pinned state; before that it just runs off the bottom.
        val contentPlaceable = contentMeasurables.first().measure(
            constraints.copy(minHeight = 0, maxHeight = (constraints.maxHeight - pinned).coerceAtLeast(0)),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val y = state.offset.coerceIn(-state.range, 0f)
            val barY = titlePlaceable.height + y.toInt()
            val progress = if (state.range > 0f) -y / state.range else 0f
            titlePlaceable.placeWithLayer(0, y.toInt()) { alpha = 1f - progress }
            contentPlaceable.place(0, barY)
            barPlaceable.place(0, barY)
        }
    }
}

/** Fades the top [heightPx] of the content from transparent up to opaque. */
private fun Modifier.topFade(heightPx: Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (heightPx <= 0f) return@drawWithContent
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = heightPx),
            size = Size(size.width, heightPx),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * How far a [PinnedBarHeader]'s title has scrolled away, hoisted so a page can
 * collapse or restore it itself (e.g. on a tab switch) and so it survives a
 * trip to a detail page and back along with the content's scroll position.
 */
@Stable
class PinnedBarHeaderState(initialOffset: Float = 0f) {
    /** 0 = title fully shown, -[range] = title gone and the bar pinned. */
    internal var offset by mutableFloatStateOf(initialOffset)

    /** How far the title can travel; known once the header has been measured. */
    internal var range = 0f

    /** Glides the title back into view ([expanded]) or away, pinning the bar. */
    suspend fun animateTo(expanded: Boolean) {
        val target = if (expanded) 0f else -range
        if (offset == target) return
        animate(offset, target, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
            offset = value
        }
    }

    companion object {
        val Saver: Saver<PinnedBarHeaderState, Float> = Saver(
            save = { it.offset },
            restore = { PinnedBarHeaderState(it) },
        )
    }
}

@Composable
fun rememberPinnedBarHeaderState(): PinnedBarHeaderState =
    rememberSaveable(saver = PinnedBarHeaderState.Saver) { PinnedBarHeaderState() }
