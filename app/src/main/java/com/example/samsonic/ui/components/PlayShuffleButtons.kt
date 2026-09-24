package com.example.samsonic.ui.components

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.model.Song
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.playback.PlayerState
import com.example.samsonic.ui.library.LocalAddToPlaylist
import com.example.samsonic.ui.library.PlaylistItems
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.glassSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ButtonSize = 56.dp
private val ButtonGap = 20.dp
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
        addToPlaylist = playlistTitle?.let { title -> PlaylistItems(title) { songs.map { it.id } } },
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
            PlaylistItems(title) { (songs ?: loadSongs().also { songs = it }).map { it.id } }
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
        .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
        .glassSurface(
            shape = CircleShape,
            hazeState = null,
            tint = MaterialTheme.colorScheme.surfaceContainerHigh,
            alpha = GlassAlpha.Card,
        )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGap, Alignment.CenterHorizontally),
    ) {
        RoundButton(
            onClick = { onAction(ListAction.Play) },
            contentColor = MaterialTheme.colorScheme.onPrimary,
            surface = Modifier.accentGlass(accent),
        ) { color, pop -> ButtonIcon(Icons.Filled.PlayArrow, loading == ListAction.Play, color, "Play", pop, size = 30.dp) }
        RoundButton(onClick = { onAction(ListAction.Shuffle) }, contentColor = MaterialTheme.colorScheme.onSurface, surface = glass) { color, pop ->
            ButtonIcon(Icons.Filled.Shuffle, loading == ListAction.Shuffle, color, "Shuffle", pop)
        }
        RoundButton(
            onClick = { onAction(ListAction.Queue) },
            contentColor = if (queued) accent else MaterialTheme.colorScheme.onSurface,
            surface = glass,
        ) { color, pop ->
            ButtonIcon(
                icon = if (queued) Icons.Filled.Check else Icons.AutoMirrored.Filled.PlaylistAdd,
                loading = loading == ListAction.Queue,
                color = color,
                contentDescription = "Add to queue",
                pop = pop,
            )
        }
        if (playlistMenu != null && addToPlaylist != null) {
            // The card grows out of this button, a circle, and folds back into it.
            RoundButton(
                onClick = { playlistMenu.open(addToPlaylist, playlistButton[0], originRadius = ButtonSize / 2) },
                contentColor = MaterialTheme.colorScheme.onSurface,
                surface = glass,
                modifier = Modifier.onGloballyPositioned { playlistButton[0] = it.boundsInRoot() },
            ) { color, pop ->
                ButtonIcon(Icons.Filled.LibraryAdd, loading = false, color = color, contentDescription = "Add to playlist", pop = pop)
            }
        }
    }
}

/**
 * Play's surface: the same frosted glass as the buttons beside it, tinted with the
 * [accent] instead of the surface color, and lifted by a soft accent glow.
 */
@Composable
private fun Modifier.accentGlass(accent: Color): Modifier = this
    .shadow(elevation = 8.dp, shape = CircleShape, ambientColor = accent.copy(alpha = 0.45f), spotColor = accent.copy(alpha = 0.45f))
    .glassSurface(
        shape = CircleShape,
        hazeState = null,
        tint = accent,
        alpha = AccentGlassAlpha,
    )

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
            .size(ButtonSize)
            .graphicsLayer {
                val s = scale * bounce.value
                scaleX = s
                scaleY = s
            }
            .then(surface)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch { bounce.animateTo(1f, BounceSpring, initialVelocity = -TapKick) }
                scope.launch { iconBounce.animateTo(0f, BounceSpring, initialVelocity = IconKick) }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
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
