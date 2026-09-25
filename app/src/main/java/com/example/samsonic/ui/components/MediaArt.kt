package com.example.samsonic.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.example.samsonic.data.CoverArtSizes
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.accentPalette

/**
 * Cover art tile. Resolves [coverArt] (a Subsonic coverArt id) to an authenticated image URL
 * and loads it with Coil, over a deterministic gradient (keyed off [colorSeed]) that fills
 * the square while the image loads, on failure, or when there's simply no art - so lists
 * never show blank tiles.
 *
 * With [fit] (album and playlist covers), art that isn't square keeps its proportions and
 * shrinks to fit the square: only the art itself gets the rounded corners and shadow, and
 * the rest of the square stays empty. Square art looks exactly as before. Without it
 * (artist photos, clipped to circles) the art is cropped to fill the square.
 */
@Composable
fun MediaArt(
    coverArt: String?,
    colorSeed: Int,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    cornerRadius: Dp = OneUiRadius.Art,
    icon: Boolean = true,
    shadowElevation: Dp = 6.dp,
    fit: Boolean = true,
    // Shows the small (list-row) size until this one loads: for a page's header,
    // whose art may have just grown out of a row's thumbnail, already in memory.
    smallFirst: Boolean = false,
) {
    val repository = LocalAppContainer.current.repository
    val density = LocalDensity.current
    val pixelSize = remember(size, density) { with(density) { size.roundToPx() }.coerceAtLeast(64) }
    val url = remember(coverArt, pixelSize) { repository.coverArtUrl(coverArt, pixelSize) }
    val smallUrl = remember(coverArt, smallFirst) {
        if (smallFirst) repository.coverArtUrl(coverArt, CoverArtSizes.Small)?.takeIf { it != url } else null
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        ArtSurface(
            url = url,
            fallbackUrl = smallUrl,
            colorSeed = colorSeed,
            icon = icon,
            iconSize = size * 0.36f,
            shape = RoundedCornerShape(cornerRadius),
            shadowElevation = shadowElevation,
            fit = fit,
        )
    }
}

/**
 * Cover art filling its parent: the Now Playing carousel ([fit], with its own [shape] and
 * shadow, as in [MediaArt]) and the blurred player backdrop (cropped, no shape).
 */
@Composable
fun MediaArtFill(
    coverArt: String?,
    colorSeed: Int,
    modifier: Modifier = Modifier,
    fit: Boolean = false,
    shape: Shape = RectangleShape,
    shadowElevation: Dp = 0.dp,
) {
    val repository = LocalAppContainer.current.repository
    val url = remember(coverArt) { repository.coverArtUrl(coverArt, 1000) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ArtSurface(
            url = url,
            colorSeed = colorSeed,
            icon = true,
            iconSize = 72.dp,
            shape = shape,
            shadowElevation = shadowElevation,
            fit = fit,
        )
    }
}

@Composable
private fun ArtSurface(
    url: String?,
    colorSeed: Int,
    icon: Boolean,
    iconSize: Dp,
    shape: Shape,
    shadowElevation: Dp,
    fit: Boolean,
    fallbackUrl: String? = null,
) {
    val painter = rememberAsyncImagePainter(url)
    val state by painter.state.collectAsState()
    // Stands in until [painter] has loaded; null when there's none.
    val fallback = fallbackUrl?.let { rememberAsyncImagePainter(it) }
    val fallbackState = fallback?.state?.collectAsState()?.value
    val mainLoaded = state is AsyncImagePainter.State.Success
    val shown = if (!mainLoaded && fallbackState is AsyncImagePainter.State.Success) fallback else painter
    val loaded = mainLoaded || shown === fallback
    // Until the art is in, the placeholder fills the whole square.
    val ratio = if (fit && loaded) shown.intrinsicSize.aspectRatioOrNull() else null
    ArtBox(url, shown, loaded, ratio, colorSeed, icon, iconSize, shape, shadowElevation, fit)
}

@Composable
private fun ArtBox(
    url: String?,
    painter: Painter,
    loaded: Boolean,
    ratio: Float?,
    colorSeed: Int,
    icon: Boolean,
    iconSize: Dp,
    shape: Shape,
    shadowElevation: Dp,
    fit: Boolean,
) {
    Box(
        modifier = (if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize())
            .then(
                if (shadowElevation > 0.dp) {
                    Modifier.shadow(
                        elevation = shadowElevation,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.45f),
                        spotColor = Color.Black.copy(alpha = 0.45f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape),
    ) {
        // Only behind art that hasn't arrived: once loaded, nothing shows through
        // (e.g. around transparent edges) but the art itself.
        if (!loaded) GradientPlaceholder(colorSeed = colorSeed, icon = icon, iconSize = iconSize)
        if (url != null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Width / height, or null while the size isn't known yet. */
fun Size.aspectRatioOrNull(): Float? =
    if (isSpecified() && width > 0f && height > 0f) width / height else null

private fun Size.isSpecified(): Boolean = this != Size.Unspecified && !width.isNaN() && !height.isNaN()

/** The largest rect with [aspectRatio] (width / height) that fits in [bounds], centred. */
fun fitAspect(bounds: Rect, aspectRatio: Float): Rect {
    val boundsRatio = bounds.width / bounds.height
    val (w, h) = if (aspectRatio > boundsRatio) {
        bounds.width to bounds.width / aspectRatio
    } else {
        bounds.height * aspectRatio to bounds.height
    }
    return Rect(Offset(bounds.center.x - w / 2f, bounds.center.y - h / 2f), Size(w, h))
}

@Composable
private fun GradientPlaceholder(colorSeed: Int, icon: Boolean, iconSize: Dp) {
    val pair = MaterialTheme.accentPalette.artGradient(colorSeed)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(pair.first, pair.second),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
fun artPrimaryColor(colorSeed: Int): Color = MaterialTheme.accentPalette.artGradient(colorSeed).first

@Composable
fun artSecondaryColor(colorSeed: Int): Color = MaterialTheme.accentPalette.artGradient(colorSeed).second
