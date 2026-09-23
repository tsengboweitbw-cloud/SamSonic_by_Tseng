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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface

private val ButtonHeight = 56.dp
private val PillShape = RoundedCornerShape(OneUiRadius.Pill)

/**
 * The Play / Shuffle pair under a detail page's header (album, artist,
 * playlist), One UI style: two equal-width floating pills - Play a solid
 * accent pill with a soft accent-colored shadow, Shuffle a frosted glass one -
 * that sink slightly while pressed instead of showing an M3 ripple.
 */
@Composable
fun PlayShuffleButtons(songs: List<Song>, modifier: Modifier = Modifier) {
    val player = LocalPlayerState.current
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OneUiPillButton(
            label = "Play",
            icon = Icons.Filled.PlayArrow,
            onClick = { if (songs.isNotEmpty()) player.play(songs.first(), songs) },
            contentColor = MaterialTheme.colorScheme.onPrimary,
            surface = Modifier
                .shadow(
                    elevation = 10.dp,
                    shape = PillShape,
                    ambientColor = accent.copy(alpha = 0.5f),
                    spotColor = accent.copy(alpha = 0.5f),
                )
                .background(accent, PillShape),
        )
        OneUiPillButton(
            label = "Shuffle",
            icon = Icons.Filled.Shuffle,
            onClick = {
                val shuffled = songs.shuffled()
                if (shuffled.isNotEmpty()) player.play(shuffled.first(), shuffled)
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            // No hazeState: the row sits inside the page list's own haze source
            // (see GlassBackButton), so this is the flat translucent glass fill.
            surface = Modifier
                .shadow(elevation = 6.dp, shape = PillShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .glassSurface(
                    shape = PillShape,
                    hazeState = null,
                    tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                    alpha = GlassAlpha.Card,
                ),
        )
    }
}

@Composable
private fun RowScope.OneUiPillButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    contentColor: Color,
    surface: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "pillPress",
    )
    Row(
        modifier = Modifier
            .weight(1f)
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
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
