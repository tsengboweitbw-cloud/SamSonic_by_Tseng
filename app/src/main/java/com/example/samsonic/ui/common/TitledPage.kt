package com.example.samsonic.ui.common

import com.example.samsonic.ui.theme.drawWithVerticalFades
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animate
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// A page's share of the window height left empty above its title, One UI's way of
// bringing the content within a thumb's reach, and the most it takes.
private const val LoweringFraction = 0.3f
private val MaxLowering = 280.dp

/**
 * How far a tab page's content is lowered from the top: empty space above its title
 * that collapses as it scrolls. Only for a phone held upright, where a thumb can't reach the top; none in
 * landscape, on a tablet or in a desktop window.
 */
@Composable
fun rememberPageLowering(): Dp {
    val layout = LocalWindowLayout.current
    if (layout.layoutClass != LayoutClass.Phone || layout.desktop || layout.width >= layout.height) return 0.dp
    return (layout.height * LoweringFraction).coerceAtMost(MaxLowering)
}

// How far content fades out below the title, on every page alike (Library's).
private val FadeHeight = 16.dp

// As much as the fade: pages open on a section label whose own top padding is
// empty space, so only that space sits in the fade at rest.
private val ContentTopPadding = 16.dp

/**
 * A nav tab page (Home, Library, Search, Settings): the large [title] stays
 * fixed under the status bar, and the page's scrolling [content] melts away
 * just below it instead of scrolling the title off screen.
 *
 * An optional [bar] (the search pill, the Library tabs) floats fixed at the
 * top of the content, right against the title, as frosted glass: content scrolls
 * on beneath it, fading out under it. The bar gets a
 * haze source of just that content, since the whole page already sits inside
 * the NavHost's own source.
 *
 * [content] gets the top padding that keeps its first row clear of the fade
 * (and the bar) at rest.
 *
 * An optional [overlay] fills the content area above both, without pushing the
 * content down: for a panel that grows out of the bar or a row (it gets the
 * same haze, which the page then sets up even without a bar).
 */
@Composable
fun TitledPage(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    bar: (@Composable (HazeState) -> Unit)? = null,
    overlay: (@Composable (HazeState) -> Unit)? = null,
    // Becoming true closes the lowered header (the page's normal style), e.g. when its search field is tapped.
    collapseHeader: Boolean = false,
    // Each change closes the header again, e.g. a tap on a search field that already has focus.
    collapseRequest: Int = 0,
    content: @Composable (contentTopPadding: Dp) -> Unit,
) {
    val density = LocalDensity.current
    var barHeight by remember { mutableIntStateOf(0) }
    val barHeightDp = with(density) { barHeight.toDp() }
    val contentHaze = if (bar != null || overlay != null) rememberHazeState() else null
    val fade = FadeHeight
    var titleHeight by remember { mutableIntStateOf(0) }
    // One UI's open header is a fixed share of the window tall, status bar and title included,
    // with the title near its bottom; the lowering is what that leaves above the title.
    val lowered = rememberPageLowering() > 0.dp
    val windowHeightPx = with(density) { LocalWindowLayout.current.height.toPx() }
    val loweringPx = if (lowered) {
        (windowHeightPx * OpenHeaderFraction - WindowInsets.statusBars.getTop(density) - titleHeight).coerceAtLeast(0f)
    } else 0f
    // How much of the lowering has scrolled away; read only at layout, so a scroll doesn't recompose the page.
    var collapsed by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(collapseHeader, collapseRequest, loweringPx) {
        if (collapseHeader && collapsed < loweringPx) animate(collapsed, loweringPx) { value, _ -> collapsed = value }
    }
    val connection = remember(loweringPx) {
        object : NestedScrollConnection {
            // Scrolling up takes the empty space away first, before the content moves.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || collapsed >= loweringPx) return Offset.Zero
                val next = (collapsed - available.y).coerceAtMost(loweringPx)
                val used = next - collapsed
                collapsed = next
                return Offset(0f, -used)
            }

            // Scrolling down gives it back only once the content is at its top, and only to a
            // finger: a fling that runs into the top stops there, with the title in its corner.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f || collapsed <= 0f) return Offset.Zero
                val next = (collapsed - available.y).coerceAtLeast(0f)
                val used = collapsed - next
                collapsed = next
                return Offset(0f, used)
            }

            // Let go half way, and it settles to whichever end is nearer.
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (collapsed > 0f && collapsed < loweringPx) {
                    val target = if (collapsed > loweringPx / 2) loweringPx else 0f
                    scope.launch { animate(collapsed, target) { value, _ -> collapsed = value } }
                }
                return Velocity.Zero
            }
        }
    }
    // 0 with the header open, 1 once collapsed; the title reads it as it is laid out.
    val collapseProgress = remember(loweringPx) {
        { if (loweringPx > 0f) (collapsed / loweringPx).coerceIn(0f, 1f) else 1f }
    }
    val headerOpen = remember(loweringPx) { { collapsed <= 0f } }
    // The most the header is ever high: the status bar, the lowering and the title.
    val aboveContent = WindowInsets.statusBars.getTop(density) + titleHeight + loweringPx.roundToInt()

    CompositionLocalProvider(LocalPageHeaderOpen provides headerOpen) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding().nestedScroll(connection)) {
        Box(
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val extra = (loweringPx - collapsed).roundToInt().coerceAtLeast(0)
                layout(placeable.width, placeable.height + extra) { placeable.place(0, extra) }
            },
        ) {
            Box(modifier = Modifier.onSizeChanged { titleHeight = it.height }) {
                CompositionLocalProvider(LocalTitleCollapse provides collapseProgress) { title() }
            }
            // Dimmed with the page under an open menu: a tap on it closes the menu too.
            GuardedArea(Modifier.matchParentSize())
        }
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
            if (contentHaze != null) {
                if (bar != null) {
                    Box(modifier = Modifier.onSizeChanged { barHeight = it.height }) { bar(contentHaze) }
                }
                if (overlay != null) {
                    CompositionLocalProvider(LocalAboveContent provides aboveContent) { overlay(contentHaze) }
                }
            }
        }
    }
    }
}

/**
 * Whether the [TitledPage] header is fully open (its lowered One UI layout), read inside a
 * snapshot. A pull past the top refreshes only then; before that it opens the header.
 * Always true on a page without lowering.
 */
val LocalPageHeaderOpen = compositionLocalOf<() -> Boolean> { { true } }

/**
 * A [TitledPage]'s large title, the same size on every page (room kept for a
 * [trailing] button such as Library's view button, there or not), so each
 * page's content, and its fade, starts at the same height.
 */
@Composable
fun PageTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    // The trailing button floats at the end, outside the title's own space, so the open
    // title is centred on the whole screen width whether or not a page has a button.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val collapse = LocalTitleCollapse.current
        // The text sits in the middle of its space with the header open, and slides to the
        // start as it collapses; read at layout, so the slide doesn't recompose.
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = text,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0))
                    val x = ((constraints.maxWidth - placeable.width) / 2f * (1f - collapse())).roundToInt()
                    layout(constraints.maxWidth, placeable.height) {
                        // Larger and higher with the header open, settling to its corner size as it collapses.
                        placeable.placeWithLayer(x, 0) {
                            val open = 1f - collapse()
                            scaleX = 1f + (OpenTitleScale - 1f) * open
                            scaleY = scaleX
                            translationY = -OpenTitleLift.toPx() * open
                        }
                    }
                },
            )
        }
        if (trailing != null) Box(Modifier.align(Alignment.CenterEnd).offset(x = 4.dp)) { trailing() }
    }
}

// A title in the open header: this much larger than in its corner, and raised this far.
private const val OpenTitleScale = 1.2f
private val OpenTitleLift = 160.dp

// One UI's expanded app bar is about a third of the screen tall (its own proportion is 0.34).
private const val OpenHeaderFraction = 0.4f

/** How far the [TitledPage] header has collapsed, 0 to 1; a page title with none is in its corner. */
private val LocalTitleCollapse = compositionLocalOf<() -> Float> { { 1f } }

/** Pixels of the page above an [TitledPage] overlay: the status bar and the title. */
private val LocalAboveContent = compositionLocalOf { 0 }

/**
 * Dims the page behind a [TitledPage] overlay's menu by [alpha] (read at draw
 * time): its own area, and up past its top over the title and status bar, so
 * the whole page darkens together rather than leaving the title lit. It stops
 * [clearBottom] short of the bottom, for what dims the floating chrome there with
 * the page under it ([com.example.samsonic.ui.common.ChromeGuardLayer]).
 */
@Composable
fun Modifier.pageScrim(clearBottom: Dp = 0.dp, alpha: () -> Float): Modifier {
    val above = LocalAboveContent.current.toFloat()
    val guard = LocalChromeGuard.current
    val span = remember { floatArrayOf(0f, 0f) }
    return onGloballyPositioned { span[0] = it.boundsInWindow().left; span[1] = it.boundsInWindow().right }.drawBehind {
        val a = alpha().coerceIn(0f, 1f)
        // Tells the chrome's dim where this page is: it may be one of two panes.
        if (a > 0f) guard?.dimSpan = span[0]..span[1]
        drawRect(
            color = Color.Black,
            topLeft = Offset(0f, -above),
            size = Size(size.width, (size.height + above - clearBottom.toPx()).coerceAtLeast(0f)),
            alpha = a,
        )
    }
}

/** Fades the top [height] of the content from transparent up to opaque, only that strip drawn offscreen. */
private fun Modifier.topFade(height: Dp): Modifier = drawWithContent { drawWithVerticalFades(top = height.toPx(), bottom = 0f) }
