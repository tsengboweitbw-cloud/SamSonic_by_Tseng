package com.example.samsonic.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.data.PlaylistCovers
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.artSeed
import com.example.samsonic.model.isFavourites
import com.example.samsonic.ui.theme.accentPalette

/**
 * A playlist's cover as [MediaArt] draws it, except Favourites, which has no cover of
 * its own and gets [FavouritesArt] instead. A cover picked on this phone ([PlaylistCovers])
 * takes the place of the server's; while editing, [pendingCover] (an image picked but not
 * saved) shows over it, or the server's cover if [showPicked] is false (the pick is to be let go).
 */
@Composable
fun PlaylistArt(
    playlist: Playlist,
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    shadowElevation: Dp = 6.dp,
    smallFirst: Boolean = false,
    pendingCover: String? = null,
    showPicked: Boolean = true,
) {
    if (playlist.isFavourites) {
        FavouritesArt(size = size, cornerRadius = cornerRadius, modifier = modifier, shadowElevation = shadowElevation)
    } else {
        val container = LocalAppContainer.current
        val picked by container.playlistCovers.covers.collectAsStateWithLifecycle()
        val key = PlaylistCovers.key(container.sources.serverKey, playlist.id)
        val imageUrl = pendingCover ?: if (showPicked) picked[key]?.let { Uri.fromFile(it).toString() } else null
        MediaArt(
            imageUrl = imageUrl,
            coverArt = playlist.coverArt,
            colorSeed = playlist.id.artSeed(),
            size = size,
            cornerRadius = cornerRadius,
            shadowElevation = shadowElevation,
            smallFirst = smallFirst,
            modifier = modifier,
        )
    }
}

/** The Favourites playlist's cover: a black heart on the accent easing into its neighbour, shaped like any cover. */
@Composable
fun FavouritesArt(size: Dp, cornerRadius: Dp, modifier: Modifier = Modifier, shadowElevation: Dp = 6.dp) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .shadow(shadowElevation, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(MaterialTheme.accentPalette.primary, MaterialTheme.accentPalette.secondary))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(size * 0.45f),
        )
    }
}
