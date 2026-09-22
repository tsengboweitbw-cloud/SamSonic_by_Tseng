package com.example.samsonic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.ui.theme.ArtGradients
import kotlin.math.abs

/**
 * Cover art tile. Resolves [coverArt] (a Subsonic coverArt id) to an authenticated image URL
 * and loads it with Coil, layered over a deterministic gradient (keyed off [colorSeed]) that
 * stays visible while the image loads, on failure, or when there's simply no art - so lists
 * never show blank tiles.
 */
@Composable
fun MediaArt(
    coverArt: String?,
    colorSeed: Int,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    cornerRadius: Dp = 10.dp,
    icon: Boolean = true,
) {
    val repository = LocalAppContainer.current.repository
    val density = LocalDensity.current
    val pixelSize = remember(size, density) { with(density) { size.roundToPx() }.coerceAtLeast(64) }
    val url = remember(coverArt, pixelSize) { repository.coverArtUrl(coverArt, pixelSize) }

    Box(modifier = modifier.size(size).clip(RoundedCornerShape(cornerRadius))) {
        GradientPlaceholder(colorSeed = colorSeed, icon = icon, iconSize = size * 0.36f)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun MediaArtFill(
    coverArt: String?,
    colorSeed: Int,
    modifier: Modifier = Modifier,
) {
    val repository = LocalAppContainer.current.repository
    val url = remember(coverArt) { repository.coverArtUrl(coverArt, 1000) }

    Box(modifier = modifier.fillMaxSize()) {
        GradientPlaceholder(colorSeed = colorSeed, icon = true, iconSize = 72.dp)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun GradientPlaceholder(colorSeed: Int, icon: Boolean, iconSize: Dp) {
    val pair = ArtGradients[abs(colorSeed) % ArtGradients.size]
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

fun artPrimaryColor(colorSeed: Int): Color = ArtGradients[abs(colorSeed) % ArtGradients.size].first
fun artSecondaryColor(colorSeed: Int): Color = ArtGradients[abs(colorSeed) % ArtGradients.size].second
