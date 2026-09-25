package com.example.samsonic.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.data.AudioFormatDisplay
import com.example.samsonic.model.Song
import com.example.samsonic.ui.theme.accentPalette
import com.example.samsonic.util.formatCodecSampling
import com.example.samsonic.util.formatDuration
import com.example.samsonic.util.isHiRes

/** How song rows show the audio format, as picked in Settings. */
@Composable
fun audioFormatDisplay(): AudioFormatDisplay {
    val display by LocalAppContainer.current.themeManager.audioFormatDisplay.collectAsStateWithLifecycle()
    return display
}

/**
 * A song row's second line: [base] (the artist, say), followed by the format
 * ("QU4RTZ · FLAC 96/24") when [display] puts it there.
 */
fun songSubtitle(base: String, song: Song, display: AudioFormatDisplay): String {
    if (display != AudioFormatDisplay.UNDER_ARTIST) return base
    return listOfNotNull(base.takeIf { it.isNotBlank() }, formatCodecSampling(song)).joinToString(" · ")
}

/**
 * Everything a song row has after its title, laid out for [display]: a Hi-Res or
 * codec badge, the [heartButton] (null where the row has none), and the duration,
 * with the format stacked over it in [AudioFormatDisplay.QUIET_HEART]. That layout
 * drops the heart button and marks a [liked] song with a small heart instead, so
 * only rows that have a heart ([likesEnabled]) show it.
 */
@Composable
fun RowScope.SongRowEnd(
    song: Song,
    display: AudioFormatDisplay,
    liked: Boolean,
    likesEnabled: Boolean,
    heartButton: (@Composable () -> Unit)?,
) {
    if (display == AudioFormatDisplay.HI_RES_BADGE && isHiRes(song)) {
        FormatBadge("Hi-Res", color = MaterialTheme.accentPalette.tertiary)
        Spacer(Modifier.width(if (heartButton != null) 4.dp else 8.dp))
    }
    if (display != AudioFormatDisplay.QUIET_HEART) heartButton?.invoke()
    if (display == AudioFormatDisplay.CODEC_BADGE) {
        song.suffix?.uppercase()?.let {
            FormatBadge(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
    }
    if (display != AudioFormatDisplay.QUIET_HEART) {
        Duration(song)
        return
    }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        formatCodecSampling(song)?.let {
            Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (liked && likesEnabled) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Liked",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Duration(song)
        }
    }
}

@Composable
private fun Duration(song: Song) {
    Text(
        text = formatDuration(song.durationSeconds),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A small outlined tag, such as "FLAC" or "Hi-Res". */
@Composable
private fun FormatBadge(text: String, color: Color) {
    val shape = RoundedCornerShape(4.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
