package com.example.samsonic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import com.example.samsonic.R
import com.example.samsonic.model.Song
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.playback.PlayerState
import com.example.samsonic.ui.library.LocalAddToPlaylist
import com.example.samsonic.ui.library.PlaylistItems
import com.example.samsonic.ui.theme.AccentSheen
import com.example.samsonic.ui.theme.AccentPalette
import com.example.samsonic.ui.theme.LocalChromeBlurScale
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.GlassRimWidth
import com.example.samsonic.ui.theme.accentPalette
import com.example.samsonic.ui.theme.glassRimBrush
import com.example.samsonic.ui.theme.glassSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val ListActionButtonSize = 56.dp
internal val ListActionButtonGap = 20.dp
private val QueueConfirmMillis = 1500L

private enum class ListAction { Play, Shuffle, Queue }

/**
 * The Play / Shuffle / Queue row under a detail page's header (album, artist,
 * playlist, Home shelf): round frosted-glass icon buttons, centered - Play tinted
 * with the accent ([accentGlass]), the rest neutral: Shuffle, Queue (which
 * appends the list to the play queue) and, given a [playlistTitle], Add to playlist,
 * which opens the card for all of them. Each answers a tap with a haptic
 * tick and a springy bounce ([RoundButton]) instead of an M3 ripple.
 */
@Composable
fun PlayShuffleButtons(songs: List<Song>, modifier: Modifier = Modifier, playlistTitle: String? = null) {
    val player = LocalPlayerState.current
    val queued = rememberQueuedConfirmation(key = songs)
    PlayShuffleRow(
        onAction = { action ->
            player.perform(action, songs)
            if (action == ListAction.Queue) queued.value = true
        },
        modifier = modifier,
        queued = queued.value,
        addToPlaylist = playlistTitle?.let { title -> PlaylistItems(title) { songs } },
    )
}

/**
 * [PlayShuffleButtons] for a page that lists albums rather than songs: the first tap
 * calls [loadSongs] (showing a spinner in the tapped button) and later taps reuse the
 * result. A new [key] - say, a refreshed album list - drops the cached songs.
 */
@Composable
fun PlayShuffleButtons(
    key: Any?,
    loadSongs: suspend () -> List<Song>,
    modifier: Modifier = Modifier,
    playlistTitle: String? = null,
) {
    val player = LocalPlayerState.current
    val scope = rememberCoroutineScope()
    var songs by remember(key) { mutableStateOf<List<Song>?>(null) }
    var loading by remember(key) { mutableStateOf<ListAction?>(null) }
    val queued = rememberQueuedConfirmation(key)
    fun start(action: ListAction) {
        songs?.let {
            player.perform(action, it)
            if (action == ListAction.Queue) queued.value = true
            return
        }
        if (loading != null) return
        loading = action
        scope.launch {
            try {
                val loaded = loadSongs()
                songs = loaded
                player.perform(action, loaded)
                if (action == ListAction.Queue) queued.value = true
            } finally {
                loading = null
            }
        }
    }
    PlayShuffleRow(
        onAction = ::start,
        modifier = modifier,
        loading = loading,
        queued = queued.value,
        // The songs load with the card's first pick, and stay for the other buttons.
        addToPlaylist = playlistTitle?.let { title ->
            PlaylistItems(title) { songs ?: loadSongs().also { songs = it } }
        },
    )
}

/** Set to true after a Queue tap; flips back by itself so the check mark is brief. */
@Composable
private fun rememberQueuedConfirmation(key: Any?): MutableState<Boolean> {
    val queued = remember(key) { mutableStateOf(false) }
    LaunchedEffect(queued.value) {
        if (queued.value) {
            delay(QueueConfirmMillis)
            queued.value = false
        }
    }
    return queued
}

private fun PlayerState.perform(action: ListAction, songs: List<Song>) {
    if (action == ListAction.Queue) return addToQueue(songs)
    val order = if (action == ListAction.Shuffle) songs.shuffled() else songs
    if (order.isNotEmpty()) play(order.first(), order)
}

@Composable
private fun PlayShuffleRow(
    onAction: (ListAction) -> Unit,
    modifier: Modifier = Modifier,
    loading: ListAction? = null,
    queued: Boolean = false,
    // What an Add to playlist button adds; null (or nowhere to add to) leaves the button out.
    addToPlaylist: PlaylistItems? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val playlistMenu = LocalAddToPlaylist.current.takeIf { addToPlaylist != null }
    val playlistButton = remember { arrayOf(Rect.Zero) }
    // No hazeState on the glass buttons: the row sits inside the page list's own
    // haze source (see GlassBackButton), so they use the flat translucent glass fill.
    val glass = Modifier
        .listActionShadow()
        .glassSurface(
            shape = CircleShape,
            hazeState = null,
            tint = MaterialTheme.colorScheme.surfaceContainerHigh,
            alpha = GlassAlpha.Nav,
            // The nav bar's accent sheen, as on the other chrome glass.
            sheen = AccentSheen.Chrome,
        )
    val merge = LocalListActionsMerge.current
    // A tap on any of them also tells a corner capsule holding them to fold back up.
    val afterAction = LocalListActionsAfterAction.current
    // Neutral buttons lose their own glass as the capsule comes in; Play keeps its accent.
    val neutralAlpha = { 1f - ramp(merge(), 0.15f, 0.7f) }
    MergingRow(merge = merge, modifier = modifier) {
        RoundButton(
            onClick = { onAction(ListAction.Play); afterAction() },
            contentColor = MaterialTheme.colorScheme.onPrimary,
            surface = Modifier.accentGlass(MaterialTheme.accentPalette),
        ) { color, pop -> ButtonIcon(Icons.Filled.PlayArrow, loading == ListAction.Play, color, stringResource(R.string.components_play), pop, size = 30.dp) }
        RoundButton(onClick = { onAction(ListAction.Shuffle); afterAction() }, contentColor = MaterialTheme.colorScheme.onSurface, surface = glass, surfaceAlpha = neutralAlpha) { color, pop ->
            ButtonIcon(Icons.Filled.Shuffle, loading == ListAction.Shuffle, color, stringResource(R.string.components_shuffle), pop)
        }
        RoundButton(
            onClick = { onAction(ListAction.Queue); afterAction() },
            contentColor = if (queued) accent else MaterialTheme.colorScheme.onSurface,
            surface = glass,
            surfaceAlpha = neutralAlpha,
        ) { color, pop ->
            ButtonIcon(
                icon = if (queued) Icons.Filled.Check else Icons.AutoMirrored.Filled.PlaylistAdd,
                loading = loading == ListAction.Queue,
                color = color,
                contentDescription = stringResource(R.string.components_add_to_queue),
                pop = pop,
            )
        }
        if (playlistMenu != null && addToPlaylist != null) {
            // The card grows out of this button, a circle, and folds back into it.
            RoundButton(
                // Its row has its own Add to queue button.
                onClick = {
                    playlistMenu.open(addToPlaylist, playlistButton[0], originRadius = ListActionButtonSize / 2, offersQueue = false)
                    afterAction()
                },
                contentColor = MaterialTheme.colorScheme.onSurface,
                surface = glass,
                surfaceAlpha = neutralAlpha,
                modifier = Modifier.onGloballyPositioned { playlistButton[0] = it.boundsInRoot() },
            ) { color, pop ->
                ButtonIcon(Icons.Filled.LibraryAdd, loading = false, color = color, contentDescription = stringResource(R.string.components_add_to_playlist), pop = pop)
            }
        }
    }
}

// Merged into the capsule, each button shrinks to this, this far from the next, and the
// capsule wraps them with this much padding.
private val MergedButtonSize = 44.dp
private val MergedGap = 2.dp
private val CapsulePadding = 4.dp

// Pinned at the top, the capsule's left edge: past the back button (16dp in, 48dp wide) and a gap.
private val PinnedCapsuleStart = 16.dp + ChromeButtonSize + 8.dp

/**
 * The buttons in a centred row that, as [merge] goes from 0 to 1 (a floating row
 * pinning, see [FloatingListActions]), slide together and shrink into one glass
 * capsule drawn in behind them: the chrome's blurred glass where the row has a haze
 * to blur ([LocalListActionsHaze]), else a flat fill. [merge] is read only in layout
 * and draw.
 */
@Composable
private fun MergingRow(merge: () -> Float, modifier: Modifier, content: @Composable () -> Unit) {
    val tint = MaterialTheme.colorScheme.surfaceContainerHigh
    val rimBrush = glassRimBrush()
    // Docked on its own (standing at the side, or in the corner capsule), merged and sized
    // to fit; the row's own modifier (its side margins in a page) doesn't apply there.
    when (LocalListActionsDock.current) {
        DockShape.ROW -> return MergedCapsule(vertical = false, tint, content)
        DockShape.COLUMN -> return MergedCapsule(vertical = true, tint, content)
        DockShape.NONE -> {}
    }
    val haze = LocalListActionsHaze.current
    // How many buttons and where the glass sits in the row, handed from layout to draw.
    val count = remember { IntArray(1) }
    val glassLeft = remember { FloatArray(1) }
    val window = remember { Path() }
    Layout(
        content = {
            if (haze != null) {
                // Laid out once at the widest the capsule shows (as it starts to fade in) and
                // never resized, so its blur isn't rebuilt every frame; the merge only moves a
                // rounded window over it, and the rim along that window.
                Box(
                    Modifier
                        .drawWithContent {
                            val m = merge()
                            val (left, width) = rowSpan(count[0], size.width + glassLeft[0] * 2, m)
                            val pad = CapsulePadding.toPx()
                            val x = left - pad - glassLeft[0]
                            val w = width + pad * 2
                            val radius = CornerRadius(size.height / 2)
                            window.rewind()
                            window.addRoundRect(RoundRect(x, 0f, x + w, size.height, radius))
                            clipPath(window) { this@drawWithContent.drawContent() }
                            val inset = GlassRimWidth.toPx() / 2
                            drawRoundRect(
                                brush = rimBrush,
                                topLeft = Offset(x + inset, inset),
                                size = Size(w - inset * 2, size.height - inset * 2),
                                cornerRadius = CornerRadius(size.height / 2 - inset),
                                style = Stroke(GlassRimWidth.toPx()),
                            )
                        }
                        .glassSurface(
                            shape = RoundedCornerShape(percent = 50),
                            hazeState = haze,
                            tint = tint,
                            alpha = GlassAlpha.Nav,
                            rim = false,
                            sheen = AccentSheen.Chrome,
                            inputScale = LocalChromeBlurScale.current,
                        ),
                )
            }
            content()
        },
        modifier = Modifier
            // Pinning, the capsule moves from the middle to stand beside the back button.
            .graphicsLayer {
                val m = merge()
                if (m > 0f && count[0] > 0) {
                    val span = count[0] * MergedButtonSize.toPx() + (count[0] - 1) * MergedGap.toPx()
                    val capsuleLeft = (size.width - span) / 2 - CapsulePadding.toPx()
                    translationX = m * (PinnedCapsuleStart.toPx() - capsuleLeft)
                }
            }
            .then(modifier)
            .fillMaxWidth()
            .then(
                if (haze != null) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        val m = merge()
                        val alpha = ramp(m, CapsuleFadeStart, 1f)
                        if (alpha <= 0f || count[0] == 0) return@drawBehind
                        val (left, width) = rowSpan(count[0], size.width, m)
                        val pad = CapsulePadding.toPx()
                        val height = MergedButtonSize.toPx() + pad * 2
                        val topLeft = Offset(left - pad, (size.height - height) / 2)
                        val capsule = Size(width + pad * 2, height)
                        drawRoundRect(tint, topLeft, capsule, CornerRadius(height / 2), alpha = GlassAlpha.Nav * alpha)
                        val inset = GlassRimWidth.toPx() / 2
                        drawRoundRect(
                            brush = rimBrush,
                            topLeft = topLeft + Offset(inset, inset),
                            size = Size(capsule.width - inset * 2, capsule.height - inset * 2),
                            cornerRadius = CornerRadius(height / 2 - inset),
                            alpha = alpha,
                            style = Stroke(GlassRimWidth.toPx()),
                        )
                    }
                },
            ),
    ) { measurables, constraints ->
        val size = ListActionButtonSize.roundToPx()
        val glass = if (haze != null) measurables.first() else null
        val buttons = measurables.drop(if (glass != null) 1 else 0).map { it.measure(Constraints.fixed(size, size)) }
        count[0] = buttons.size
        val rowWidth = constraints.maxWidth.toFloat()
        val glassPlaceable = glass?.let {
            val (left, width) = rowSpan(buttons.size, rowWidth, CapsuleFadeStart)
            val pad = CapsulePadding.toPx()
            glassLeft[0] = left - pad
            it.measure(Constraints.fixed((width + pad * 2).roundToInt(), (MergedButtonSize.toPx() + pad * 2).roundToInt()))
        }
        layout(constraints.maxWidth, size) {
            val m = merge()
            // Only once it starts to show: its blur costs a frame's work even unseen.
            if (glassPlaceable != null && m > CapsuleFadeStart) {
                glassPlaceable.placeWithLayer(glassLeft[0].roundToInt(), (size - glassPlaceable.height) / 2) {
                    alpha = ramp(m, CapsuleFadeStart, 1f)
                }
            }
            val scale = lerp(1f, MergedButtonSize / ListActionButtonSize, m)
            val (left, _) = rowSpan(buttons.size, rowWidth, m)
            val width = size * scale
            val gap = lerp(ListActionButtonGap.toPx(), MergedGap.toPx(), m)
            buttons.forEachIndexed { i, button ->
                // Scaled about its centre, so it's placed where its centre goes.
                val centre = left + i * (width + gap) + width / 2
                button.placeWithLayer((centre - size / 2f).roundToInt(), 0) {
                    scaleX = scale
                    scaleY = scale
                }
            }
        }
    }
}

// How far into the merge the capsule starts to fade in behind the buttons.
private const val CapsuleFadeStart = 0.3f

/**
 * The buttons merged into a glass capsule sized to fit them: standing, top to bottom,
 * if [vertical], else lying, left to right.
 */
@Composable
private fun MergedCapsule(vertical: Boolean, tint: Color, content: @Composable () -> Unit) {
    Layout(
        content = content,
        // The chrome's glass (the nav bar's and mini player's): its blur of the page, its
        // opacity and its accent sheen.
        modifier = Modifier.glassSurface(
            shape = RoundedCornerShape(percent = 50),
            hazeState = LocalListActionsHaze.current,
            tint = tint,
            alpha = GlassAlpha.Nav,
            sheen = AccentSheen.Chrome,
            inputScale = LocalChromeBlurScale.current,
        ),
    ) { measurables, _ ->
        val size = ListActionButtonSize.roundToPx()
        val buttons = measurables.map { it.measure(Constraints.fixed(size, size)) }
        val pad = CapsulePadding.toPx()
        val button = MergedButtonSize.toPx()
        val gap = MergedGap.toPx()
        val thickness = (button + pad * 2).roundToInt()
        val length = (buttons.size * button + (buttons.size - 1).coerceAtLeast(0) * gap + pad * 2).roundToInt()
        layout(if (vertical) thickness else length, if (vertical) length else thickness) {
            val scale = MergedButtonSize / ListActionButtonSize
            buttons.forEachIndexed { i, b ->
                // Scaled about its centre, so it's placed where its centre goes.
                val along = (pad + i * (button + gap) + button / 2 - size / 2f).roundToInt()
                val across = ((thickness - size) / 2f).roundToInt()
                b.placeWithLayer(if (vertical) across else along, if (vertical) along else across) {
                    scaleX = scale
                    scaleY = scale
                }
            }
        }
    }
}

/** How long a capsule of [count] merged buttons is ([MergedCapsule]): its height standing. */
internal fun mergedCapsuleLength(count: Int): Dp = MergedButtonSize * count + MergedGap * (count - 1) + CapsulePadding * 2

/** How thick a capsule of merged buttons is ([MergedCapsule]): its height lying. */
internal val MergedCapsuleThickness = MergedButtonSize + CapsulePadding * 2

/** Where [count] buttons, [merge] of the way to merged, start in a [rowWidth] row, and how wide they span. */
private fun Density.rowSpan(count: Int, rowWidth: Float, merge: Float): Pair<Float, Float> {
    val width = lerp(ListActionButtonSize.toPx(), MergedButtonSize.toPx(), merge)
    val gap = lerp(ListActionButtonGap.toPx(), MergedGap.toPx(), merge)
    val span = count * width + (count - 1) * gap
    return (rowWidth - span) / 2 to span
}

/** 0 up to [from], 1 from [to], and linear between. */
private fun ramp(value: Float, from: Float, to: Float): Float = ((value - from) / (to - from)).coerceIn(0f, 1f)

/**
 * Play's surface: the same frosted glass as the buttons beside it, tinted with the
 * accent instead of the surface color, sheened into its neighbour color toward the
 * bottom-right, and lifted by the same soft shadow ([listActionShadow]).
 */
@Composable
private fun Modifier.accentGlass(palette: AccentPalette): Modifier = this
    .listActionShadow()
    .glassSurface(
        shape = CircleShape,
        hazeState = null,
        tint = palette.primary,
        alpha = AccentGlassAlpha,
        rim = false,
    )
    // Already clipped to the circle by the glass.
    .background(Brush.linearGradient(listOf(Color.Transparent, palette.secondary.copy(alpha = 0.6f))))
    .border(GlassRimWidth, glassRimBrush(), CircleShape)

/**
 * The soft black shadow under every button of the row, Play and the neutral ones alike.
 * Not Modifier.shadow, whose polygon core shows through the see-through glass (see circleGlow).
 */
private fun Modifier.listActionShadow(): Modifier = circleGlow(Color.Black.copy(alpha = 0.25f), width = 8.dp, offsetY = 2.dp)

// Thinner than the neutral buttons' glass: enough accent to mark Play out, still see-through.
private const val AccentGlassAlpha = 0.72f

// Pressed, a button sinks this far, and springs back past its size on release.
private const val PressedScale = 0.8f
// Loose springs, so both the button and its icon wobble a few times before they settle.
private val PressSpring = spring<Float>(dampingRatio = 0.3f, stiffness = 450f)
private val BounceSpring = spring<Float>(dampingRatio = 0.25f, stiffness = 380f)
// How hard a tap kicks the button in (scale per second) and its icon out: even a quick
// tap, which barely presses the button before it's let go, gets a full bounce.
private const val TapKick = 4f
private const val IconKick = 6f

/**
 * A round button that answers a tap with some life: it sinks deep while held, a tap
 * kicks it into a springy bounce that overshoots and wobbles back, a haptic tick
 * confirms it, and its icon bounces the other way ([content] gets the icon's extra
 * scale, around 0, to apply).
 */
@Composable
private fun RoundButton(
    onClick: () -> Unit,
    contentColor: Color,
    surface: Modifier,
    modifier: Modifier = Modifier,
    // Given, the surface is drawn on its own layer at this opacity (read in draw), the
    // icon staying put: for a neutral button melting into the merged capsule.
    surfaceAlpha: (() -> Float)? = null,
    content: @Composable (color: Color, pop: () -> Float) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressedScale else 1f,
        animationSpec = PressSpring,
        label = "roundButtonPress",
    )
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // The tap's bounce, on top of the press: 1 at rest, swinging either side after a tap.
    val bounce = remember { Animatable(1f) }
    // The icon's, 0 at rest: it swells as the button squeezes in.
    val iconBounce = remember { Animatable(0f) }
    Box(
        modifier = modifier
            .size(ListActionButtonSize)
            .graphicsLayer {
                val s = scale * bounce.value
                scaleX = s
                scaleY = s
            }
            .then(if (surfaceAlpha == null) surface else Modifier)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch { bounce.animateTo(1f, BounceSpring, initialVelocity = -TapKick) }
                scope.launch { iconBounce.animateTo(0f, BounceSpring, initialVelocity = IconKick) }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (surfaceAlpha != null) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = surfaceAlpha() }.then(surface))
        }
        content(contentColor) { iconBounce.value }
    }
}

@Composable
private fun ButtonIcon(
    icon: ImageVector,
    loading: Boolean,
    color: Color,
    contentDescription: String,
    pop: () -> Float,
    size: Dp = 24.dp,
) {
    if (loading) {
        CircularProgressIndicator(color = color, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
    } else {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val s = (1f + 0.3f * pop()).coerceAtLeast(0.6f)
                    scaleX = s
                    scaleY = s
                },
        )
    }
}
