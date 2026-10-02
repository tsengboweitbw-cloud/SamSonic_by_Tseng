package com.example.samsonic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.common.HoverGlowAlpha
import com.example.samsonic.ui.common.hovered
import com.example.samsonic.ui.theme.OneUiRadius
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The nav rail's size, and its place off the screen's side. */
object NavRail {
    val Width = 88.dp

    /** Between the rail and the side of the screen (past any system bar there). */
    val Margin = 16.dp

    // On a phone on its side, where the rail's room comes out of a short, not-so-wide screen.
    val ShortWidth = 68.dp
    val ShortMargin = 8.dp

    // The indicator's inset from the rail's edges, as the nav bar's.
    internal val Inset = 8.dp

    // A tab away from the indicator; the one under it is taller by SelectedTabExtraWeight.
    // Roomier than the nav bar's tabs are wide, so the rail stands tall enough on a big screen.
    val TabHeight = 68.dp

    // In DeX, where a window has height to spare, the rail stands taller still.
    val DesktopTabHeight = 92.dp

    // On a phone on its side, where the rail and the mini player under it share little height.
    val ShortTabHeight = 32.dp

    // The tabs' icons, and in DeX, where the rail is taller, larger ones.
    val IconSize = 24.dp
    val DesktopIconSize = 32.dp

    /** How long the rail is with [count] tabs. */
    fun length(count: Int, tabHeight: Dp = TabHeight): Dp = tabHeight * (count + SelectedTabExtraWeight) + Inset * 2

    // Either side of a label, within its tab (RailTab's padding) and the rail's inset.
    internal val LabelSides = (Inset + 4.dp) * 2 + 4.dp
}

/**
 * How wide the rail is with [labels]: [NavRail.Width], or wider where the longest label
 * needs it to show whole under its icon, as with larger text (DeX, a large font size).
 * The nav host lays out beside it with the same width.
 */
@Composable
fun rememberNavRailWidth(labels: List<String>, minWidth: Dp = NavRail.Width): Dp {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current
    return remember(labels, style, density, minWidth) {
        val widest = labels.maxOfOrNull { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width } ?: 0
        maxOf(minWidth,with(density) { widest.toDp() } + NavRail.LabelSides)
    }
}

/**
 * The nav bar stood on end for wider screens: a frosted capsule at the side of the
 * screen, of the nav bar's glass and with its gliding indicator. Each tab is just its
 * icon until the indicator comes over it, when the tab grows taller and its label slides
 * out below the icon (the nav bar's tabs grow wider, with the label beside), folding back
 * as the indicator leaves. A finger sliding along the rail carries the indicator with it,
 * and letting go opens the tab it's on ([onSelect]).
 */
@Composable
fun GlassNavRail(
    labels: List<String>,
    icons: List<ImageVector>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    // See rememberNavRailWidth.
    width: Dp = NavRail.Width,
    // A tab a tab away from the indicator; the one under it is taller (see NavRail).
    tabHeight: Dp = NavRail.TabHeight,
    // The tabs' icons: larger in DeX.
    iconSize: Dp = NavRail.IconSize,
    // The finger's position (in tabs) as it slides along the rail, for a caller whose
    // pages follow it; the rail still carries its own indicator under the finger.
    onSwipe: ((Float) -> Unit)? = null,
    // The tab a swipe let go on. Without it, a new tab goes to [onSelect].
    onSwipeEnd: ((Int) -> Unit)? = null,
) {
    val animated = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        animated.animateTo(selectedIndex.toFloat(), TabIndicatorSpring)
    }
    val scope = rememberCoroutineScope()
    // Where the finger has the indicator, in tabs; read every frame while it follows.
    val finger = remember { FloatArray(1) }
    var follow by remember { mutableStateOf<Job?>(null) }
    val count = labels.size
    // Where the indicator is, in tabs. Read only in layout and draw.
    val p: () -> Float = remember(count, animated) {
        { animated.value.coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat()) }
    }
    val indicatorColors = tabIndicatorColors()

    Layout(
        modifier = modifier
            .width(width)
            .height(NavRail.length(count, tabHeight))
            .glassTabPill(hazeState)
            .drawBehind {
                val at = p()
                drawTabIndicator(tabWeights(count, at), at, NavRail.Inset.toPx(), indicatorColors, vertical = true)
            }
            .tabBarSwipe(
                count = count,
                inset = NavRail.Inset,
                extraWeight = { SelectedTabExtraWeight },
                enabled = true,
                vertical = true,
                onSwipe = { at ->
                    finger[0] = at
                    onSwipe?.invoke(at)
                    if (follow == null) follow = scope.launch { animated.followSwipe(target = { finger[0] }) }
                },
                onSwipeEnd = { index ->
                    follow?.cancel()
                    follow = null
                    if (onSwipeEnd != null) onSwipeEnd(index) else if (index != selectedIndex) onSelect(index)
                    scope.launch { animated.animateTo(index.toFloat(), TabIndicatorSpring) }
                },
            )
            .padding(vertical = NavRail.Inset),
        content = {
            labels.forEachIndexed { index, label ->
                val emphasis = remember(index, p) { { tabProximity(index, p()) } }
                RailTab(
                    icon = icons[index],
                    label = label,
                    selected = index == selectedIndex,
                    emphasis = emphasis,
                    onClick = { onSelect(index) },
                    iconSize = iconSize,
                )
            }
        },
    ) { measurables, constraints ->
        // Each tab its share of the length by its weight (the one under the indicator
        // taller), worked out here rather than in composition, so a slide only lays out.
        val weights = tabWeights(count, p())
        val total = weights.sum().takeIf { it > 0f } ?: 1f
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        var sum = 0f
        val edges = IntArray(measurables.size + 1)
        for (i in measurables.indices) {
            sum += weights.getOrElse(i) { 0f }
            edges[i + 1] = (height * sum / total).roundToInt()
        }
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(Constraints.fixed(width, (edges[i + 1] - edges[i]).coerceAtLeast(0)))
        }
        layout(width, height) {
            placeables.forEachIndexed { i, placeable -> placeable.placeRelative(0, edges[i]) }
        }
    }
}

/**
 * One tab of the rail: [IconLabelTab] standing on end, the label opening below the
 * icon as the indicator comes over it ([emphasis], 1 under it, 0 a tab away).
 */
@Composable
private fun RailTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    emphasis: () -> Float,
    onClick: () -> Unit,
    iconSize: Dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "tabPressScale",
    )
    val pressGlow by animateFloatAsState(
        targetValue = if (pressed) 0.08f else if (hovered(interactionSource)) HoverGlowAlpha else 0f,
        animationSpec = tween(if (pressed) 90 else 260),
        label = "tabPressGlow",
    )
    val glowColor = MaterialTheme.colorScheme.onSurface
    val restTint = MaterialTheme.colorScheme.onSurfaceVariant
    val onTint = MaterialTheme.colorScheme.primary
    val painter = rememberVectorPainter(icon)
    val showLabel by remember(emphasis) { derivedStateOf { emphasis() > 0f } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NavRail.Inset)
            .clip(RoundedCornerShape(OneUiRadius.Pill))
            .drawBehind { if (pressGlow > 0f) drawRect(glowColor, alpha = pressGlow) }
            // No ripple: One UI answers a tap with a soft shrink-and-glow.
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Tinted as it's drawn, following the indicator without recomposing.
        Box(
            Modifier
                .size(iconSize)
                .semantics { contentDescription = label }
                .drawBehind {
                    with(painter) { draw(size, colorFilter = ColorFilter.tint(lerp(restTint, onTint, emphasis()))) }
                },
        )
        // Opens with the indicator's nearness, as the tab grows taller with it (see
        // IconLabelTab), so it keeps pace however fast the indicator moves.
        if (showLabel) {
            Text(
                text = label,
                modifier = Modifier
                    .revealHeight(emphasis, edgeFade = 12.dp)
                    .graphicsLayer {
                        val t = ((emphasis() - 0.1f) / 0.9f).coerceIn(0f, 1f)
                        alpha = t * t * (3 - 2 * t)
                    }
                    .padding(top = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Lays the content out at its full height but takes up only [fraction] of it, so it
 * opens and closes like a sliding reveal without reflowing; where it's cut short, the
 * last [edgeFade] of what shows fades out (as IconLabelTab's label does across).
 */
private fun Modifier.revealHeight(fraction: () -> Float, edgeFade: Dp): Modifier {
    // Whether the last layout cut the content short; drawing (after it) fades the edge then.
    val cut = BooleanArray(1)
    return clipToBounds()
        // Offscreen, so the fade masks the text alone, not what's behind it.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (cut[0]) {
                val fade = edgeFade.toPx().coerceAtMost(size.height)
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val height = (placeable.height * fraction()).roundToInt().coerceAtMost(constraints.maxHeight)
            cut[0] = height < placeable.height
            layout(placeable.width, height) { placeable.placeRelative(0, 0) }
        }
}
