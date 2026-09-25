package com.example.samsonic.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.example.samsonic.ui.components.pressClickable
import com.example.samsonic.util.formatDuration
import com.example.samsonic.util.formatFileSize
import dev.chrisbanes.haze.HazeState

/**
 * One line of song info; [onClick] opens the page it names (album, artist), if any.
 * [artistsOf] makes it the song's artist line instead, each artist opening their own.
 */
private data class Detail(
    val label: String,
    val value: String,
    val onClick: (() -> Unit)? = null,
    val artistsOf: Song? = null,
)

/** The current song's details, as a [PanelCard] grown out of the info button. */
@Composable
internal fun SongInfoPanel(panel: PanelState, haze: HazeState) {
    val song = LocalPlayerState.current.currentSong ?: return
    PanelCard(panel, PanelIcons.Info, title = "Song info", haze = haze) {
        val links = LocalPlayerLinks.current
        val albumArtist = rememberAlbumArtist(song)
        SelectionContainer(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                songDetails(song, albumArtist, links).forEach { DetailRow(it) }
                val playback = rememberPlaybackDetails()
                if (playback.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Playback",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    playback.forEach { (label, value) -> DetailRow(Detail(label, value)) }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(detail: Detail) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = detail.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        // Linked values take the accent color, so they read as tappable.
        val artistsOf = detail.artistsOf
        if (artistsOf != null) {
            val linked = artistsOf.artistCredits.any { it.id != null }
            ArtistNames(
                song = artistsOf,
                style = MaterialTheme.typography.bodyMedium,
                color = if (linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onOpen = LocalPlayerLinks.current.openArtist,
                maxLines = Int.MAX_VALUE,
                modifier = Modifier.weight(1f, fill = false),
            )
            return@Row
        }
        val onClick = detail.onClick
        Text(
            text = detail.value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f, fill = false)
                .then(if (onClick != null) Modifier.pressClickable(onClick, pressedScale = 0.95f) else Modifier),
        )
    }
}

/** Each detail the server reported; the rest are left out. */
private fun songDetails(song: Song, albumArtist: ArtistLink?, links: PlayerLinks): List<Detail> = listOfNotNull(
    Detail("Title", song.title),
    Detail("Artist", song.artistName, artistsOf = song),
    song.albumTitle.takeIf { it.isNotBlank() }?.let { title ->
        Detail("Album", title, song.albumId?.let { id -> { links.openAlbum(id) } })
    },
    albumArtist?.let { artist ->
        Detail("Album artist", artist.name, artist.id?.let { id -> { links.openArtist(id) } })
    },
    song.trackNumber.takeIf { it > 0 }?.let { track ->
        Detail("Track", song.discNumber?.takeIf { it > 0 }?.let { "Disc $it · $track" } ?: "$track")
    },
    song.year?.takeIf { it > 0 }?.let { Detail("Year", "$it") },
    song.genre?.takeIf { it.isNotBlank() }?.let { Detail("Genre", it) },
    Detail("Duration", formatDuration(song.durationSeconds)),
    (song.suffix?.uppercase() ?: song.contentType)?.let { Detail("Format", it) },
    song.bitRate?.let { Detail("Bit rate", "$it kbps") },
    song.samplingRate?.let { Detail("Sample rate", "%.1f kHz".format(it / 1000f)) },
    song.bitDepth?.let { Detail("Bit depth", "$it-bit") },
    song.channelCount?.let { Detail("Channels", if (it == 1) "Mono" else if (it == 2) "Stereo" else "$it") },
    song.sizeBytes?.let { Detail("File size", formatFileSize(it)) },
    song.playCount?.let { Detail("Play count", "$it") },
    song.path?.takeIf { it.isNotBlank() }?.let { Detail("Path", it) },
)
