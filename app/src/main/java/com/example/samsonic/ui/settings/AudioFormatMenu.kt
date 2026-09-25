package com.example.samsonic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.data.AudioFormatDisplay
import com.example.samsonic.model.Song
import com.example.samsonic.ui.components.SongRowEnd
import com.example.samsonic.ui.components.songSubtitle
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.theme.OneUiRow
import com.example.samsonic.ui.theme.accentWash
import dev.chrisbanes.haze.HazeState

/** The choice's name: the Settings row's value, and what a screen reader says for its sample. */
internal val AudioFormatDisplay.label: String
    get() = when (this) {
        AudioFormatDisplay.OFF -> "Off"
        AudioFormatDisplay.UNDER_ARTIST -> "Under the artist"
        AudioFormatDisplay.QUIET_HEART -> "By the duration"
        AudioFormatDisplay.CODEC_BADGE -> "Codec badge"
        AudioFormatDisplay.HI_RES_BADGE -> "Hi-Res badge"
    }

/** The song every sample shows: liked and hi-res, so each choice has something to show. */
private val SampleSong = Song(
    id = "preview",
    title = "Song title",
    artistId = null,
    artistName = "Artist",
    albumId = null,
    albumTitle = "",
    trackNumber = 1,
    durationSeconds = 206,
    coverArt = null,
    liked = true,
    suffix = "flac",
    samplingRate = 96_000,
    bitDepth = 24,
)

// Every sample alike, whatever its end holds: the tallest, a row with its heart button.
private val SampleHeight = 60.dp

/**
 * The Audio format setting's secondary menu ([SettingsMenu]): one sample song row per
 * choice, drawn exactly as song rows would be with it, the current one highlighted as
 * the playing song is. Picking one applies it and folds the menu back.
 */
@Composable
internal fun AudioFormatMenu(
    panel: PanelState,
    haze: HazeState,
    current: AudioFormatDisplay,
    onSelect: (AudioFormatDisplay) -> Unit,
) {
    SettingsMenu(panel, haze, title = "Audio format") {
        AudioFormatDisplay.entries.forEach { display ->
            SampleOption(
                display = display,
                selected = display == current,
                onClick = {
                    onSelect(display)
                    panel.close()
                },
            )
        }
    }
}

/**
 * One choice as the sample song row it gives, built from the rows' own pieces
 * ([songSubtitle], [SongRowEnd]) at a song row's sizes, with the Like button setting
 * as it is. Only the row as a whole responds; the heart in it is just a picture.
 */
@Composable
private fun SampleOption(display: AudioFormatDisplay, selected: Boolean, onClick: () -> Unit) {
    val likesEnabled by LocalAppContainer.current.themeManager.likesEnabled.collectAsStateWithLifecycle()
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()
    val song = SampleSong
    Box(
        modifier = Modifier
            .padding(OneUiRow.Inset)
            .fillMaxWidth()
            .height(SampleHeight)
            .clip(OneUiRow.Shape)
            .then(
                if (selected) {
                    Modifier.accentWash(OneUiRow.Shape)
                } else {
                    Modifier
                },
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = display.label },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                // The sample's own text would be read out as a song; the label says it all.
                .clearAndSetSemantics {}
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // In the accent color rather than a placeholder's gradient, so it fits the theme.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = songSubtitle(song.artistName, song, display),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SongRowEnd(
                song = song,
                display = display,
                liked = song.liked,
                likesEnabled = likesEnabled,
                heartButton = if (likesEnabled) {
                    {
                        // A song row's heart button, minus the button.
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (song.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                tint = if (song.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }
    }
}
