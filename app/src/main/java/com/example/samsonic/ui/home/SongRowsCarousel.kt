package com.example.samsonic.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Song
import com.example.samsonic.model.artSeed
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.components.MediaArt
import com.example.samsonic.ui.theme.OneUiRow
import com.example.samsonic.ui.theme.horizontalScrollFade

// Songs stacked in each column.
private const val ROWS_PER_COLUMN = 3

// A column's share of the row's width: the rest shows the next column's covers, so
// it reads as a row to swipe.
private const val COLUMN_WIDTH = 0.86f

/**
 * A Home song shelf: columns of [ROWS_PER_COLUMN] song rows that swipe sideways a
 * column at a time, the next column's covers peeking in at the edge. A tap plays
 * the song, with the rest of the shelf after it.
 */
@Composable
fun SongRowsCarousel(songs: List<Song>, modifier: Modifier = Modifier) {
    val player = LocalPlayerState.current
    val columns = remember(songs) { songs.chunked(ROWS_PER_COLUMN) }
    val rowState = rememberLazyListState()
    // A new set of songs starts back at its first column.
    remember(songs) { rowState.requestScrollToItem(0) }

    LazyRow(
        modifier = modifier.horizontalScrollFade(rowState),
        state = rowState,
        contentPadding = PaddingValues(horizontal = 8.dp),
        flingBehavior = rememberSnapFlingBehavior(rowState, SnapPosition.Start),
    ) {
        itemsIndexed(columns, key = { index, _ -> index }) { _, column ->
            Column(Modifier.fillParentMaxWidth(COLUMN_WIDTH)) {
                column.forEach { song ->
                    ShelfSongRow(
                        song = song,
                        isCurrent = player.currentSong?.id == song.id,
                        onClick = { player.play(song, songs) },
                    )
                }
            }
        }
    }
}

/** Cover, title and "artist • year", with the audio format over the duration at the end. */
@Composable
private fun ShelfSongRow(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(OneUiRow.Inset)
            .clip(OneUiRow.Shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaArt(
            coverArt = song.coverArt,
            colorSeed = song.id.artSeed(),
            size = 56.dp,
            cornerRadius = cornerRadius,
            shadowElevation = 0.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(song.artistName, song.year?.takeIf { it > 0 }?.toString()).joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            shortAudioFormat(song)?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "%02d:%02d".format(song.durationSeconds / 60, song.durationSeconds % 60),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The format in short: "FLAC • 16/44" (bit depth / sample rate in kHz) for lossless
 * files that report both, else the bit rate ("MP3 • 320k"), else just the format.
 */
private fun shortAudioFormat(song: Song): String? {
    val format = song.suffix?.uppercase() ?: return null
    val depth = song.bitDepth?.takeIf { it > 0 }
    val rate = song.samplingRate?.takeIf { it > 0 }
    val detail = when {
        depth != null && rate != null -> "$depth/${rate / 1000}"
        song.bitRate != null -> "${song.bitRate}k"
        else -> null
    }
    return listOfNotNull(format, detail).joinToString(" • ")
}
