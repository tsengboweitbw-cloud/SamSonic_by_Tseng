package com.example.samsonic.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private val FadeHeight = 24.dp

// A touch less than the fade: pages open on a section label whose own top
// padding is empty space, so only that space sits in the fade at rest.
private val ContentTopPadding = 16.dp

/**
 * A nav tab page (Home, Library, Search, Settings): the large [title] stays
 * fixed under the status bar, and the page's scrolling [content] melts away
 * just below it instead of scrolling the title off screen.
 *
 * An optional [bar] (the search pill, the Library tabs) floats fixed at the
 * top of the content, right under the title, as frosted glass: content scrolls
 * on beneath it and fades out as it slides up past the bar. The bar gets a
 * haze source of just that content, since the whole page already sits inside
 * the NavHost's own source.
 *
 * [content] gets the top padding that keeps its first row clear of the fade
 * (and the bar) at rest.
 */
@Composable
fun TitledPage(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    bar: (@Composable (HazeState) -> Unit)? = null,
    content: @Composable (contentTopPadding: Dp) -> Unit,
) {
    val density = LocalDensity.current
    var barHeight by remember { mutableIntStateOf(0) }
    val barHeightDp = with(density) { barHeight.toDp() }
    val contentHaze = if (bar != null) rememberHazeState() else null
    val fade = if (bar != null) barHeightDp else FadeHeight

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        title()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Outside hazeSource, so the bar still blurs the content unfaded.
                    .topFade(fade)
                    .then(if (contentHaze != null) Modifier.graphicsLayer().hazeSource(contentHaze) else Modifier),
            ) {
                content(if (bar != null) barHeightDp + 8.dp else ContentTopPadding)
            }
            if (bar != null && contentHaze != null) {
                Box(modifier = Modifier.onSizeChanged { barHeight = it.height }) { bar(contentHaze) }
            }
        }
    }
}

/** Fades the top [height] of the content from transparent up to opaque. */
private fun Modifier.topFade(height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val heightPx = height.toPx()
        if (heightPx <= 0f) return@drawWithContent
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = heightPx),
            size = Size(size.width, heightPx),
            blendMode = BlendMode.DstIn,
        )
    }
