package com.example.samsonic.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import com.example.samsonic.R
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.glassSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.example.samsonic.data.ListActionsPin
import com.example.samsonic.ui.common.LocalChromeGuard
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer

private const val SlotKey = "floatingActions"

// Pinned, the row's capsule sits level with the back button (their centres line up),
// and beside it (see PinnedCapsuleStart).
private val PinnedTop = 8.dp + (ChromeButtonSize - ListActionButtonSize) / 2

// Scrolling away (not pinned), it fades out over the list's own top fade, as rows do.
private val FadeDistance = 32.dp

/**
 * Room in a list for its [FloatingListActions], where the row sits at rest (right
 * under the header), with [bottomSpacing] under it before the next item.
 */
fun LazyListScope.floatingActionsSlot(bottomSpacing: Dp = 0.dp) {
    item(key = SlotKey) { Spacer(Modifier.fillMaxWidth().height(ListActionButtonSize + bottomSpacing)) }
}

/** [floatingActionsSlot] for a grid, across its full width. */
fun LazyGridScope.floatingActionsSlot(bottomSpacing: Dp = 0.dp) {
    item(key = SlotKey, span = { GridItemSpan(maxLineSpan) }) {
        Spacer(Modifier.fillMaxWidth().height(ListActionButtonSize + bottomSpacing))
    }
}

/**
 * A page's Play / Shuffle / Queue row ([content], its [PlayShuffleButtons]) floating
 * over the list [listState] scrolls: at rest it sits in the list's
 * [floatingActionsSlot] and scrolls up with the header; then, as the Play buttons
 * setting has it ([ListActionsPin]), it merges into one glass capsule and stays pinned
 * just under the back button as the rest scrolls on beneath it, or scrolls away and
 * fades out as a row would (docking at the bottom, if so set). Pinned, it's one row
 * throughout, not a copy swapped in, so a button's state (a spinner, Queue's tick)
 * carries on as it pins. Placed in the page over the list, with the same top. Its
 * position is read as it's placed, never in composition, so scrolling recomposes nothing.
 */
@Composable
fun BoxScope.FloatingListActions(listState: LazyListState, overscroll: PullOverscrollEffect? = null, cornerEndOffset: Dp = 0.dp, haze: HazeState? = null, content: @Composable () -> Unit) {
    FloatingActions(
        restTop = {
            val info = listState.layoutInfo
            info.visibleItemsInfo.firstOrNull { it.key == SlotKey }?.let { it.offset - info.viewportStartOffset }
        },
        // The slot is always just under the header, the list's first item.
        scrolledPast = { listState.firstVisibleItemIndex > 0 },
        overscroll = overscroll,
        cornerEndOffset = cornerEndOffset,
        haze = haze,
        content = content,
    )
}

/** [FloatingListActions] over a grid. */
@Composable
fun BoxScope.FloatingListActions(gridState: LazyGridState, overscroll: PullOverscrollEffect? = null, cornerEndOffset: Dp = 0.dp, haze: HazeState? = null, content: @Composable () -> Unit) {
    FloatingActions(
        restTop = {
            val info = gridState.layoutInfo
            info.visibleItemsInfo.firstOrNull { it.key == SlotKey }?.let { it.offset.y - info.viewportStartOffset }
        },
        scrolledPast = { gridState.firstVisibleItemIndex > 0 },
        overscroll = overscroll,
        cornerEndOffset = cornerEndOffset,
        haze = haze,
        content = content,
    )
}

/**
 * Room at the end of a list for its docked play buttons ([ListActionsPin.BOTTOM],
 * [ListActionsPin.SIDE]), so
 * they never cover its last row; nothing otherwise.
 */
fun LazyListScope.floatingActionsEnd() {
    item(key = "$SlotKey:end") { FloatingActionsEndSpace() }
}

/** [floatingActionsEnd] for a grid, across its full width. */
fun LazyGridScope.floatingActionsEnd() {
    item(key = "$SlotKey:end", span = { GridItemSpan(maxLineSpan) }) { FloatingActionsEndSpace() }
}

@Composable
private fun FloatingActionsEndSpace() {
    val pin by LocalAppContainer.current.libraryLayoutManager.listActionsPin.collectAsStateWithLifecycle()
    when (pin) {
        ListActionsPin.BOTTOM -> Spacer(Modifier.fillMaxWidth().height(ListActionButtonSize + DockGap))
        // The last row can rise clear of the standing capsule, which covers its end.
        ListActionsPin.SIDE -> Spacer(Modifier.fillMaxWidth().height(mergedCapsuleLength(4) + DockGap))
        else -> {}
    }
}

/**
 * How far a page's play buttons have merged into their capsule: 0 apart, 1 merged.
 * A floating row ([FloatingListActions]) provides it; anywhere else the buttons stay
 * apart. Read it only in layout or draw.
 */
internal val LocalListActionsMerge = staticCompositionLocalOf<() -> Float> { { 0f } }

/** A page's play buttons merged on their own, sized to fit: in a dock or the corner capsule. */
internal enum class DockShape { NONE, ROW, COLUMN }

/** How a page's play buttons stand merged on their own, if they do ([DockShape]). */
internal val LocalListActionsDock = staticCompositionLocalOf { DockShape.NONE }

/** Called after a tap on any of a page's play buttons: the corner capsule folds up on it. */
internal val LocalListActionsAfterAction = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * What a merged capsule on its own ([LocalListActionsDock]) blurs, as the chrome's glass
 * does: the page's list, its own haze source. Null draws it flat.
 */
internal val LocalListActionsHaze = staticCompositionLocalOf<HazeState?> { null }

// Over this last stretch before it pins, the row merges into its capsule.
private val MergeDistance = 48.dp

// Docked, the capsule sits this far above the floating chrome (the mini player).
private val DockGap = 8.dp

// The dock's arrival: quick and settled, with only a touch of overshoot, like the chrome's.
private val DockSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 500f)

// How far below its spot the dock starts as it slides up into it.
private val DockRise = 24.dp

// The corner capsule opening out of its button and folding back into it.
private val CornerSpring = spring<Float>(dampingRatio = 0.75f, stiffness = 420f)

// After a tap on one of its buttons, the corner capsule stays open this long, so the
// button's bounce (and Queue's tick) shows before it folds up.
private const val CornerLingerMillis = 450L

/**
 * Places [content] at [restTop] (the slot's top in the list, px; null while it isn't
 * laid out), and once it's reached the top or [scrolledPast] it, as the Settings
 * choice ([ListActionsPin]) has it: scrolls on and away; pinned at [PinnedTop],
 * merging into its capsule on the way ([LocalListActionsMerge]); or scrolls away
 * while a capsule of the same buttons docks above the chrome, or a button that opens
 * into one takes the top right corner, [cornerEndOffset] in from the usual spot.
 */
@Composable
private fun BoxScope.FloatingActions(
    restTop: () -> Int?,
    scrolledPast: () -> Boolean,
    overscroll: PullOverscrollEffect?,
    cornerEndOffset: Dp,
    haze: HazeState?,
    content: @Composable () -> Unit,
) {
    val pin = LocalAppContainer.current.libraryLayoutManager.listActionsPin.collectAsStateWithLifecycle()
    // Set as the row is placed, and read as its buttons are, in the same pass.
    val merge = remember { mutableFloatStateOf(0f) }
    val readMerge = remember(merge) { { merge.floatValue } }
    // Clipped to the page, so a row scrolling away doesn't draw up over the status bar.
    Box(Modifier.matchParentSize().clipToBounds()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val row = measurable.measure(constraints.copy(minHeight = 0))
                    layout(row.width, row.height) { placeRow(row, restTop(), scrolledPast(), pin.value == ListActionsPin.TOP, merge, pull = { overscroll?.pull ?: 0f }) }
                },
        ) {
            CompositionLocalProvider(
                LocalListActionsMerge provides readMerge,
                // For its capsule, merged pinned at the top: the chrome's glass, blurring the list.
                LocalListActionsHaze provides haze,
                content = content,
            )
    }
        when (pin.value) {
            ListActionsPin.BOTTOM, ListActionsPin.SIDE ->
                Dock(rememberRowGone(restTop, scrolledPast), side = pin.value == ListActionsPin.SIDE, haze, content)
            ListActionsPin.CORNER -> Corner(rememberRowGone(restTop, scrolledPast), cornerEndOffset, haze, content)
            else -> {}
        }
    }
}

/**
 * Whether the row in the list has scrolled up into the top fade (or past it), for what
 * stands in for it then. Changes only as the row goes or comes back, so scrolling
 * recomposes nothing.
 */
@Composable
private fun rememberRowGone(restTop: () -> Int?, scrolledPast: () -> Boolean): State<Boolean> {
    val density = LocalDensity.current
    return remember {
        derivedStateOf {
            val rest = restTop()
            if (rest == null) scrolledPast() else rest + with(density) { (ListActionButtonSize / 2).toPx() } < with(density) { FadeDistance.toPx() }
        }
    }
}

/**
 * The docked capsule: the page's buttons again, merged, sliding up into place just above
 * the floating chrome once the row in the list is [gone], and back down out of the way
 * when it comes back; or with [side], standing at the right edge, sliding in from it.
 */
@Composable
private fun BoxScope.Dock(gone: State<Boolean>, side: Boolean, haze: HazeState?, content: @Composable () -> Unit) {
    val progress = animateFloatAsState(if (gone.value) 1f else 0f, DockSpring, label = "dock")
    val showing by remember { derivedStateOf { progress.value > 0.01f } }
    if (!showing) return
    val chromeTop = LocalChromeGuard.current?.top ?: { 0f }
    Box(
        modifier = Modifier
            .align(if (side) Alignment.BottomEnd else Alignment.BottomCenter)
            // At the side, in from the edge as far as the chrome is.
            .padding(end = if (side) 16.dp else 0.dp)
            // Just above the highest bar, wherever it is right now: the nav bar alone, or the
            // mini player over it, moving with it as it comes and goes.
            .offset { IntOffset(0, -(chromeTop() + DockGap.toPx()).roundToInt()) }
            .graphicsLayer {
                val p = progress.value
                alpha = p.coerceIn(0f, 1f)
                // In from the edge beside it, or up from below.
                if (side) translationX = (1f - p) * DockRise.toPx() else translationY = (1f - p) * DockRise.toPx()
            },
    ) {
        CompositionLocalProvider(
            LocalListActionsMerge provides { 1f },
            LocalListActionsDock provides if (side) DockShape.COLUMN else DockShape.ROW,
            LocalListActionsHaze provides haze,
            content = content,
        )
    }
}

/**
 * The corner button: once the row in the list is [gone], a round glass button like the
 * back button fades in at the top right, [endOffset] in from the edge's usual spot
 * (clear of a page's own button there). A tap opens it out, leftwards, into a capsule of
 * the page's buttons, merged; a tap on one of them, outside it, or back folds it up.
 */
@Composable
private fun BoxScope.Corner(gone: State<Boolean>, endOffset: Dp, haze: HazeState?, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    // The row coming back takes its place, so the capsule folds up with the button going.
    LaunchedEffect(gone.value) { if (!gone.value) open = false }
    val shown = animateFloatAsState(if (gone.value) 1f else 0f, DockSpring, label = "cornerShown")
    val opened = animateFloatAsState(if (open) 1f else 0f, CornerSpring, label = "cornerOpen")
    val showing by remember { derivedStateOf { shown.value > 0.01f } }
    val capsuleShowing by remember { derivedStateOf { opened.value > 0.01f } }
    // Gone once the capsule is well out, so it can't catch a tap meant for the capsule's end.
    val buttonShowing by remember { derivedStateOf { opened.value < 0.4f } }
    if (!showing) return
    BackHandler(enabled = open) { open = false }
    val scope = rememberCoroutineScope()
    // A tap anywhere else closes it, and goes no further.
    if (open) {
        Box(
            Modifier
                .matchParentSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = false },
        )
    }
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            // Centred on the back button's height, level with it: always the capsule's height, open or
            // not, so the button alone sits centred in it just as it does beside the capsule.
            .padding(end = 16.dp + endOffset - (MergedCapsuleThickness - ChromeButtonSize) / 2, top = 8.dp + ChromeButtonSize / 2 - MergedCapsuleThickness / 2)
            .height(MergedCapsuleThickness)
            .graphicsLayer {
                val p = shown.value
                alpha = p.coerceIn(0f, 1f)
                scaleX = lerp(0.8f, 1f, p)
                scaleY = lerp(0.8f, 1f, p)
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (capsuleShowing) {
            val density = LocalDensity.current
            Box(
                Modifier.graphicsLayer {
                    // Opens out of the button: a capsule window widening leftwards from its end.
                    clip = true
                    shape = RevealShape(opened.value.coerceIn(0f, 1f), with(density) { MergedCapsuleThickness.toPx() })
                    alpha = ramp(opened.value, 0f, 0.3f)
                },
            ) {
                CompositionLocalProvider(
                    LocalListActionsMerge provides { 1f },
                    LocalListActionsDock provides DockShape.ROW,
                    LocalListActionsHaze provides haze,
                    LocalListActionsAfterAction provides {
                        scope.launch {
                            delay(CornerLingerMillis)
                            open = false
                        }
                    },
                    content = content,
                )
            }
        }
        // Its glass is the capsule's while that's out, so there's just the one.
        if (buttonShowing) {
            PressIconButton(
                onClick = { open = true },
                size = ChromeButtonSize,
                modifier = Modifier
                    .padding(end = (MergedCapsuleThickness - ChromeButtonSize) / 2)
                    .graphicsLayer { alpha = 1f - ramp(opened.value, 0f, 0.4f) }
                    .glassSurface(
                        shape = CircleShape,
                        hazeState = haze,
                        tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                        alpha = GlassAlpha.Nav,
                    ),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = stringResource(R.string.components_play_options),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** A capsule [progress] of the way from [start] px wide to its full width, flush with the right end. */
private class RevealShape(private val progress: Float, private val start: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val width = lerp(minOf(start, size.width), size.width, progress)
        return Outline.Rounded(RoundRect(size.width - width, 0f, size.width, size.height, CornerRadius(size.height / 2)))
    }
}

/** 0 up to [from], 1 from [to], and linear between. */
private fun ramp(value: Float, from: Float, to: Float): Float = ((value - from) / (to - from)).coerceIn(0f, 1f)

private fun Placeable.PlacementScope.placeRow(
    row: Placeable,
    rest: Int?,
    past: Boolean,
    pinned: Boolean,
    merge: MutableFloatState,
    pull: () -> Float,
) {
    val pinnedTop = PinnedTop.roundToPx()
    val top = when {
        pinned && (rest == null && past) -> pinnedTop
        pinned && rest != null -> maxOf(rest, pinnedTop)
        rest != null -> rest
        // Scrolled away, or not laid out yet.
        else -> return
    }
    merge.floatValue = if (!pinned) 0f else if (rest == null) 1f else
        ((pinnedTop + MergeDistance.toPx() - rest) / MergeDistance.toPx()).coerceIn(0f, 1f)
    row.placeWithLayer(0, top) {
        // Riding with the list, it follows a pull past the list's end (a move, see PullOverscrollEffect).
        if (rest != null && rest >= pinnedTop) translationY = pull()
        if (!pinned) alpha = ((top + row.height / 2f) / FadeDistance.toPx()).coerceIn(0f, 1f)
    }
}
