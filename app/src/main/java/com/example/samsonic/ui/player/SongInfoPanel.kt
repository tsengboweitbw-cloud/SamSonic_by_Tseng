package com.example.samsonic.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.samsonic.model.Song
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.util.formatDuration
import com.example.samsonic.util.formatFileSize
import dev.chrisbanes.haze.HazeState

/** The current song's details, as a [PanelCard] grown out of the info button. */
@Composable
internal fun SongInfoPanel(panel: PanelState, haze: HazeState) {
    val song = LocalPlayerState.current.currentSong ?: return
    PanelCard(panel, PanelIcons.Info, title = "Song info", haze = haze) {
        SelectionContainer(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                songDetails(song).forEach { (label, value) -> DetailRow(label, value) }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** Label and value for each detail the server reported; the rest are left out. */
private fun songDetails(song: Song): List<Pair<String, String>> = listOfNotNull(
    "Title" to song.title,
    "Artist" to song.artistName,
    song.albumTitle.takeIf { it.isNotBlank() }?.let { "Album" to it },
    song.trackNumber.takeIf { it > 0 }?.let { track ->
        "Track" to (song.discNumber?.takeIf { it > 0 }?.let { "Disc $it · $track" } ?: "$track")
    },
    song.year?.takeIf { it > 0 }?.let { "Year" to "$it" },
    song.genre?.takeIf { it.isNotBlank() }?.let { "Genre" to it },
    "Duration" to formatDuration(song.durationSeconds),
    (song.suffix?.uppercase() ?: song.contentType)?.let { "Format" to it },
    song.bitRate?.let { "Bit rate" to "$it kbps" },
    song.samplingRate?.let { "Sample rate" to "%.1f kHz".format(it / 1000f) },
    song.bitDepth?.let { "Bit depth" to "$it-bit" },
    song.channelCount?.let { "Channels" to if (it == 1) "Mono" else if (it == 2) "Stereo" else "$it" },
    song.sizeBytes?.let { "File size" to formatFileSize(it) },
    song.playCount?.let { "Play count" to "$it" },
    song.path?.takeIf { it.isNotBlank() }?.let { "Path" to it },
)
