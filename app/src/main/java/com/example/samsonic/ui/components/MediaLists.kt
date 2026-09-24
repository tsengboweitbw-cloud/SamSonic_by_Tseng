package com.example.samsonic.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.ui.common.ArtKeys
import com.example.samsonic.ui.common.rememberSharedArt
import com.example.samsonic.ui.library.rememberAddToPlaylistLongPress
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.Song
import com.example.samsonic.model.artSeed
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRow
import com.example.samsonic.ui.theme.glassSurface

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
) {
    // With [onTitleClick] the whole header is one tap target leading to the full list,
    // title on the left and a chevron at the right edge.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onTitleClick != null) Modifier.pressClickable(onTitleClick, pressedScale = 0.97f) else Modifier)
            // The chevron's glyph sits ~8dp inside its 24dp icon, so a 12dp end padding
            // lines the glyph up with the content's 20dp edge.
            .padding(start = 20.dp, end = if (onTitleClick != null) 12.dp else 20.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        if (onTitleClick != null) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.pressClickable({ onActionClick?.invoke() }, pressedScale = 0.92f, enabled = onActionClick != null),
            )
        }
    }
}

@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artSize: Dp = 140.dp,
) {
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()
    val art = rememberSharedArt(ArtKeys.album(album.id), album, onClick)
    // The menu grows out of the cover.
    val addToPlaylist = rememberAddToPlaylistLongPress(album, originRadius = cornerRadius)
    PressableCard(
        onClick = art.onClick,
        modifier = modifier,
        onLongClick = addToPlaylist.onLongClick,
        onLongClickLabel = addToPlaylist.label,
    ) {
        Column(
            modifier = Modifier.widthIn(max = artSize),
        ) {
            MediaArt(
                coverArt = album.coverArt,
                colorSeed = album.id.artSeed(),
                size = artSize,
                cornerRadius = cornerRadius,
                modifier = art.modifier.then(addToPlaylist.origin),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = album.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ArtistCard(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artSize: Dp = 104.dp,
) {
    val art = rememberSharedArt(ArtKeys.artist(artist.id), artist, onClick)
    PressableCard(onClick = art.onClick, modifier = modifier) {
        Column(
            modifier = Modifier.width(artSize),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MediaArt(
                coverArt = artist.coverArt,
                colorSeed = artist.id.artSeed(),
                size = artSize,
                cornerRadius = artSize / 2,
                icon = false,
                fit = false,
                modifier = art.modifier.clip(CircleShape),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = artist.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${artist.albumCount} albums",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artSize: Dp = 140.dp,
) {
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()
    val art = rememberSharedArt(ArtKeys.playlist(playlist.id), playlist, onClick)
    PressableCard(onClick = art.onClick, modifier = modifier) {
        Column(
            modifier = Modifier.widthIn(max = artSize),
        ) {
            PlaylistArt(
                playlist = playlist,
                size = artSize,
                cornerRadius = cornerRadius,
                modifier = art.modifier,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.songCount} songs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    liked: Boolean = false,
    onToggleLike: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
) {
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()
    val likesEnabled by LocalAppContainer.current.themeManager.likesEnabled.collectAsStateWithLifecycle()
    val display = audioFormatDisplay()
    val addToPlaylist = rememberAddToPlaylistLongPress(song)

    // No hazeState here: this row is nested inside the NavHost's own hazeSource
    // subtree, and haze warns that a hazeEffect nested inside its own hazeSource
    // can cause recursive drawing - so this falls back to a flat translucent fill.
    SongSwipeActions(song = song, modifier = modifier) {
        Row(
            // SongSwipeActions already insets the row by OneUiRow.Inset; clipping before
            // clickable keeps the press feedback the same rounded shape as the highlight.
            modifier = Modifier
                .fillMaxWidth()
                .clip(OneUiRow.Shape)
                .then(
                    if (isCurrent) {
                        Modifier.glassSurface(
                            shape = OneUiRow.Shape,
                            hazeState = null,
                            tint = MaterialTheme.colorScheme.primary,
                            alpha = GlassAlpha.Highlight,
                        )
                    } else {
                        Modifier
                    }
                )
                .then(addToPlaylist.origin)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = addToPlaylist.onLongClick,
                    onLongClickLabel = addToPlaylist.label,
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            } else {
                MediaArt(
                    coverArt = song.coverArt,
                    colorSeed = song.id.artSeed(),
                    size = 44.dp,
                    cornerRadius = cornerRadius,
                    shadowElevation = 0.dp,
                )
                Spacer(Modifier.width(12.dp))
            }
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
                    text = songSubtitle(song.artistName, song, display),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val hasHeart = onToggleLike != null && likesEnabled
            SongRowEnd(
                song = song,
                display = display,
                liked = liked,
                likesEnabled = hasHeart,
                heartButton = if (hasHeart) {
                    {
                        PressIconButton(onClick = onToggleLike) {
                            Icon(
                                imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (liked) "Unlike" else "Like",
                                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    null
                },
            )
            if (onMoreClick != null) {
                PressIconButton(onClick = onMoreClick) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
