package com.example.samsonic.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.dp
import com.example.samsonic.data.LibraryLayout
import com.example.samsonic.data.LibraryViewMode
import com.example.samsonic.ui.components.GlassTabBar
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.GlassRimWidth
import com.example.samsonic.ui.theme.LocalGlassSettings
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassRimBrush
import com.example.samsonic.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

private val columnChoices = (LibraryLayout.MIN_COLUMNS..LibraryLayout.MAX_COLUMNS).toList()

// Soft, barely underdamped growth, like the tab indicator's glide.
private val MorphSpring = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

/**
 * Frosted glass circle next to the Library title: shows the current tab's view,
 * and turns into a close button while the view options are [open].
 */
@Composable
internal fun LibraryViewButton(mode: LibraryViewMode, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .glassSurface(
                shape = CircleShape,
                hazeState = null,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = GlassAlpha.Nav,
            ),
    ) {
        Crossfade(targetState = open, label = "viewButtonIcon") { isOpen ->
            Icon(
                imageVector = when {
                    isOpen -> Icons.Filled.Close
                    mode == LibraryViewMode.GRID -> Icons.Filled.GridView
                    else -> Icons.AutoMirrored.Filled.ViewList
                },
                contentDescription = if (isOpen) "Close view options" else "View options",
            )
        }
    }
}

/**
 * The Library's tab pill, which can grow into the tab's view options panel (in
 * the style of the accent color picker): the pill's glass stretches down into
 * the panel while the tabs cross-fade into its settings, and shrinks back into
 * the pill on close. While open, the content behind dims and a tap there closes it.
 *
 * It is the one and only tab pill, always on screen - never a copy swapped in
 * for the open animation - so the panel grows from the pill's real measured size
 * and nothing flickers at the hand-off. It sits in
 * [com.example.samsonic.ui.common.TitledPage]'s overlay (so growing doesn't push
 * the content down), with the page's bar slot only reserving the pill's height.
 * Open and close it through [state].
 */
@Composable
internal fun LibraryTabsPanel(
    state: MutableTransitionState<Boolean>,
    hazeState: HazeState,
    sectionName: String,
    layout: LibraryLayout,
    onLayoutChange: (LibraryLayout) -> Unit,
    onDismiss: () -> Unit,
    tabs: @Composable () -> Unit,
) {
    // Here rather than in the screen, so opening/closing recomposes only this panel.
    BackHandler(enabled = state.targetState, onBack = onDismiss)
    val transition = rememberTransition(state, label = "viewOptions")
    // One progress (0 = pill, 1 = panel) drives the height, both fades and the
    // glass, so every part of the morph moves together.
    val progress by transition.animateFloat(transitionSpec = { MorphSpring }, label = "morph") { if (it) 1f else 0f }
    val scrimAlpha by transition.animateFloat(label = "scrim") { if (it) 0.32f else 0f }
    // The glass is the tab pill's (user opacity included); an extra wash of the
    // same tint, faded with the morph, thickens it into a readable panel.
    // Animating the glass's own alpha instead restyled the blur and recomposed
    // the whole panel every frame, which stuttered.
    val tint = MaterialTheme.colorScheme.surfaceContainerHigh
    val pillAlpha = GlassAlpha.Nav * LocalGlassSettings.current.opacityScale
    // The glass's tint runs thin at the top to dense at the bottom of the full
    // panel, so the pill (just its top strip) gets a little wash to match the
    // standalone pill's average density; the open panel gets enough to reach
    // GlassAlpha.Panel.
    val closedWash = 0.2f * pillAlpha / GlassAlpha.Nav
    val openWash = (1f - (1f - GlassAlpha.Panel) / (1f - pillAlpha)).coerceIn(0f, 1f)
    val rimBrush = glassRimBrush()
    val cornerRadius = OneUiRadius.Card
    // The pill's measured height, handed from layout to draw (both per frame).
    val pillHeight = remember { IntArray(1) }
    Box(modifier = Modifier.fillMaxSize()) {
        // Only while open or animating: otherwise the content under it must
        // keep getting its scrolls and taps.
        if (state.isShown) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color.Black, alpha = scrimAlpha) }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        // The glass is always laid out at the full panel size and never resizes:
        // the morph only moves a rounded clip ("window") down and up over it.
        // Resizing the blurred surface every frame made the blur and its tint
        // gradient rebuild each frame, and the background flickered.
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .drawWithContent {
                    val radius = cornerRadius.toPx()
                    val windowHeight = lerp(pillHeight[0].toFloat(), size.height, progress)
                        .coerceIn(0f, size.height)
                    val window = RoundRect(0f, 0f, size.width, windowHeight, CornerRadius(radius))
                    val path = Path().apply { addRoundRect(window) }
                    clipPath(path) { this@drawWithContent.drawContent() }
                    // The rim follows the window, not the full glass bounds.
                    val inset = GlassRimWidth.toPx() / 2
                    drawRoundRect(
                        brush = rimBrush,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, windowHeight - inset * 2),
                        cornerRadius = CornerRadius(radius - inset),
                        style = Stroke(GlassRimWidth.toPx()),
                    )
                }
                .glassSurface(
                    shape = RoundedCornerShape(cornerRadius),
                    hazeState = hazeState,
                    tint = tint,
                    alpha = pillAlpha,
                    // Already scaled above; the panel's wash must not be thinned again.
                    scaleOpacity = false,
                    rim = false,
                )
                // Read at draw time, so the morph doesn't recompose.
                .drawBehind { drawRect(tint, alpha = lerp(closedWash, openWash, progress.coerceIn(0f, 1f))) },
            // No pointer modifier on the glass: closed, taps below the pill
            // (where the glass is laid out but clipped away) reach the content.
        ) {
            // A hand-rolled morph rather than AnimatedContent, which dropped the
            // outgoing tabs for a frame (a visible blink) and wouldn't draw a
            // glass modifier set on it. Tabs and settings both stay composed
            // (composing the settings at the tap cost a ~40ms frame), and
            // progress is only read in draw/layout, so opening, closing and
            // every frame between recompose nothing.
            Layout(
                modifier = Modifier.fillMaxWidth(),
                content = {
                    // Out over the first 40% ...
                    Box(Modifier.graphicsLayer { alpha = (1f - progress / 0.4f).coerceIn(0f, 1f) }) { tabs() }
                    // Swallows taps on the panel's empty space so they don't reach
                    // the scrim (which would close it).
                    Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {})
                    // ... while the settings come in from 25%: overlapping, no empty frame.
                    Box(Modifier.graphicsLayer { alpha = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f) }) {
                        ViewOptionsPanel(sectionName = sectionName, layout = layout, onLayoutChange = onLayoutChange)
                    }
                },
            ) { measurables, constraints ->
                val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                val pill = measurables[0].measure(loose)
                val panel = measurables[2].measure(loose)
                val blocker = measurables[1].measure(Constraints.fixed(constraints.maxWidth, panel.height))
                pillHeight[0] = pill.height
                layout(constraints.maxWidth, panel.height) {
                    pill.place(0, 0)
                    // Closed, the blocker and settings stay composed but unplaced:
                    // not drawn and not hit, so taps go to the tabs and content.
                    if (progress > 0.001f) {
                        blocker.place(0, 0)
                        panel.place(0, 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewOptionsPanel(sectionName: String, layout: LibraryLayout, onLayoutChange: (LibraryLayout) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(text = "$sectionName view", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))

        PanelLabel("Layout")
        GlassTabBar(
            labels = listOf("List", "Grid"),
            selectedIndex = if (layout.mode == LibraryViewMode.GRID) 1 else 0,
            onSelect = { index ->
                onLayoutChange(layout.copy(mode = if (index == 1) LibraryViewMode.GRID else LibraryViewMode.LIST))
            },
            hazeState = null,
        )
        Spacer(Modifier.height(20.dp))

        // Frozen in list view: the saved count still shows, dimmed, for when grid comes back.
        val grid = layout.mode == LibraryViewMode.GRID
        val frozenAlpha by animateFloatAsState(if (grid) 1f else 0.38f, label = "columnsAlpha")
        Column(Modifier.alpha(frozenAlpha)) {
            PanelLabel("Grid columns")
            GlassTabBar(
                labels = columnChoices.map { it.toString() },
                selectedIndex = columnChoices.indexOf(layout.columns).coerceAtLeast(0),
                onSelect = { index -> onLayoutChange(layout.copy(columns = columnChoices[index])) },
                hazeState = null,
                enabled = grid,
            )
        }
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/** True while the view options panel is on screen: open, or still animating open or shut. */
internal val MutableTransitionState<Boolean>.isShown: Boolean get() = currentState || targetState
