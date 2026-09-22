package com.example.samsonic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.components.MediaArtFill
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Shared [HazeState] for the current NavHost content Box, so floating glass
 * chrome (bottom nav, mini player, search pill, ...) can all sample the same
 * scrolling content behind them. Null off the nav host (e.g. previews).
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * A One UI 9.0 frosted-glass floating surface: clips to [shape], blurs and
 * tints whatever is scrolling behind it (via [hazeState]), and draws a thin
 * light rim along the edge for depth. Falls back to a flat translucent fill
 * when no haze source is available (e.g. API < 31, where the underlying
 * RenderEffect blur silently no-ops).
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    hazeState: HazeState?,
    tint: Color,
    alpha: Float = GlassAlpha.Card,
    blurRadius: Dp = 44.dp,
): Modifier {
    val clipped = clip(shape)
    val filled = if (hazeState != null) {
        clipped.hazeEffect(
            state = hazeState,
            style = HazeStyle(tint = HazeTint(tint.copy(alpha = alpha)), blurRadius = blurRadius),
        )
    } else {
        clipped.background(tint.copy(alpha = alpha))
    }
    // A light rim reads as a highlight on dark glass; a dark rim reads as one
    // on light glass - pick by the current theme's actual background, not a
    // fixed assumption that the app is always dark.
    val rim = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }
    return filled.border(1.dp, rim, shape)
}

/**
 * Full-bleed, dynamically blurred album art used as the Now Playing backdrop:
 * a native [Modifier.blur] on the art itself (not a backdrop sample), so it
 * needs no haze source and works purely off the current track's cover art.
 */
@Composable
fun BlurredArtBackdrop(
    coverArt: String?,
    colorSeed: Int,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier = modifier.fillMaxSize()) {
        MediaArtFill(
            coverArt = coverArt,
            colorSeed = colorSeed,
            modifier = Modifier
                .fillMaxSize()
                .blur(60.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            background.copy(alpha = 0.55f),
                            background.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )
    }
}
