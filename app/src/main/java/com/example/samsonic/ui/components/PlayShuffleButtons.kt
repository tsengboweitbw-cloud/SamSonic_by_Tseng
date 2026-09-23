package com.example.samsonic.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.samsonic.model.Song
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.playback.PlayerState
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ButtonHeight = 56.dp
private val PillShape = RoundedCornerShape(OneUiRadius.Pill)
private val QueueConfirmMillis = 1500L

private enum class ListAction { Play, Shuffle, Queue }

/**
 * The Play / Shuffle / Queue row under a detail page's header (album, artist,
 * playlist, Home shelf), One UI style: two equal-width floating pills - Play a
 * solid accent pill with a soft accent-colored shadow, Shuffle a frosted glass
 * one - and a round glass Queue button that appends the list to the play queue.
 * All of them sink slightly while pressed instead of showing an M3 ripple.
 */
@Composable
fun PlayShuffleButtons(songs: List<Song>, modifier: Modifier = Modifier) {
    val player = LocalPlayerState.current
    val queued = rememberQueuedConfirmation(key = songs)
    PlayShuffleRow(
        onAction = { action ->
            player.perform(action, songs)
            if (action == ListAction.Queue) queued.value = true
        },
        modifier = modifier,
        queued = queued.value,
    )
}

/**
 * [PlayShuffleButtons] for a page that lists albums rather than songs: the first tap
 * calls [loadSongs] (showing a spinner in the tapped button) and later taps reuse the
 * result. A new [key] - say, a refreshed album list - drops the cached songs.
 */
@Composable
fun PlayShuffleButtons(key: Any?, loadSongs: suspend () -> List<Song>, modifier: Modifier = Modifier) {
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
    PlayShuffleRow(onAction = ::start, modifier = modifier, loading = loading, queued = queued.value)
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
) {
    val accent = MaterialTheme.colorScheme.primary
    // No hazeState on the glass buttons: the row sits inside the page list's own
    // haze source (see GlassBackButton), so they use the flat translucent glass fill.
    val glass = Modifier
        .shadow(elevation = 6.dp, shape = PillShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
        .glassSurface(
            shape = PillShape,
            hazeState = null,
            tint = MaterialTheme.colorScheme.surfaceContainerHigh,
            alpha = GlassAlpha.Card,
        )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OneUiPillButton(
            onClick = { onAction(ListAction.Play) },
            contentColor = MaterialTheme.colorScheme.onPrimary,
            surface = Modifier
                .shadow(
                    elevation = 10.dp,
                    shape = PillShape,
                    ambientColor = accent.copy(alpha = 0.5f),
                    spotColor = accent.copy(alpha = 0.5f),
                )
                .background(accent, PillShape),
            modifier = Modifier.weight(1f),
        ) { color -> PillIcon(Icons.Filled.PlayArrow, loading == ListAction.Play, color); PillLabel("Play", color) }
        OneUiPillButton(
            onClick = { onAction(ListAction.Shuffle) },
            contentColor = MaterialTheme.colorScheme.onSurface,
            surface = glass,
            modifier = Modifier.weight(1f),
        ) { color -> PillIcon(Icons.Filled.Shuffle, loading == ListAction.Shuffle, color); PillLabel("Shuffle", color) }
        OneUiPillButton(
            onClick = { onAction(ListAction.Queue) },
            contentColor = if (queued) accent else MaterialTheme.colorScheme.onSurface,
            surface = glass,
            modifier = Modifier.width(ButtonHeight),
        ) { color ->
            PillIcon(
                icon = if (queued) Icons.Filled.Check else Icons.AutoMirrored.Filled.PlaylistAdd,
                loading = loading == ListAction.Queue,
                color = color,
                contentDescription = "Add to queue",
            )
        }
    }
}

@Composable
private fun OneUiPillButton(
    onClick: () -> Unit,
    contentColor: Color,
    surface: Modifier,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "pillPress",
    )
    Row(
        modifier = modifier
            .height(ButtonHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content(contentColor)
    }
}

@Composable
private fun PillIcon(icon: ImageVector, loading: Boolean, color: Color, contentDescription: String? = null) {
    if (loading) {
        CircularProgressIndicator(color = color, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
    } else {
        Icon(icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun RowScope.PillLabel(label: String, color: Color) {
    Spacer(Modifier.width(8.dp))
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}
