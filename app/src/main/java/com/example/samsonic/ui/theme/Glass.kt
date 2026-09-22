package com.example.samsonic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
fun Modifier.glassSurface(
    shape: Shape,
    hazeState: HazeState?,
    tint: Color,
    alpha: Float = GlassAlpha.Card,
    blurRadius: Dp = 28.dp,
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
    return filled.border(1.dp, GlassRimLight, shape)
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
                            ObsidianBackground.copy(alpha = 0.55f),
                            ObsidianBackground.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )
    }
}
