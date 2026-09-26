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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.components.MediaArtFill
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
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
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    hazeState: HazeState?,
    tint: Color,
    alpha: Float = GlassAlpha.Card,
    blurRadius: Dp = LocalGlassSettings.current.blurRadius,
    // Fine grain breaks up the high-contrast edges (text, icons) that a
    // Gaussian blur alone still leaves faintly legible through the glass.
    noiseFactor: Float = 0.12f,
    // Modal surfaces (dialogs) pass false: nothing meaningful sits behind
    // them, so thinning them out only makes their own content unreadable.
    scaleOpacity: Boolean = true,
    // False for a caller that draws [glassRimBrush] along its own (e.g.
    // animated) outline instead of this surface's full bounds.
    rim: Boolean = true,
    // True for a big panel that grows and shrinks (see the hazeEffect below); a small bar's
    // lighter blur shows the smaller copy it blurs as blocks.
    downsample: Boolean = false,
    // How much of the accent's sheen washes over the glass (see [AccentSheen]); 0 for none.
    sheen: Float = 0f,
): Modifier {
    // Scale by the user's global opacity preference, keeping each surface's
    // base alpha as its relative density.
    val opacityScale = if (scaleOpacity) LocalGlassSettings.current.opacityScale else 1f
    val alpha = (alpha * opacityScale).coerceIn(0f, 1f)
    val clipped = clip(shape)
    val filled = if (hazeState != null) {
        // Thinner tint up top so the blurred colors behind bloom through,
        // denser toward the bottom where content (text) must stay hidden.
        val tintBrush = Brush.verticalGradient(
            colors = listOf(
                tint.copy(alpha = (alpha - 0.25f).coerceAtLeast(0f)),
                tint.copy(alpha = (alpha + 0.1f).coerceAtMost(1f)),
            ),
        )
        clipped.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                // The captured source content has no background of its own
                // (Scaffold paints it outside hazeSource), so without an
                // opaque base the blurred layer is translucent and the sharp
                // original text shows straight through it.
                backgroundColor = MaterialTheme.colorScheme.background,
                tint = HazeTint(tintBrush),
                blurRadius = blurRadius,
                // The smaller copy a downsampled glass blurs would stretch the grain into blotches.
                noiseFactor = if (downsample) 0f else noiseFactor,
            ),
        ) {
            // Blurs a smaller copy of what's behind (a third across, for all but a faint
            // blur), which looks the same under a blur this strong at a fraction of the
            // cost: at full size, a panel's glass redrawn every frame of its growing
            // (by the morph's moving clip) blurred the whole card each frame, and stuttered.
            if (downsample) inputScale = HazeInputScale.Auto
        }
    } else {
        clipped.background(tint.copy(alpha = alpha))
    }
    val tinted = if (sheen > 0f) filled.accentSheen(sheen) else filled
    return if (rim) tinted.border(GlassRimWidth, glassRimBrush(), shape) else tinted
}

/**
 * How strongly the accent washes over each kind of glass: a colour the eye catches,
 * never a coloured slab. Cards and menus a touch more, the chrome (always on screen)
 * less.
 */
object AccentSheen {
    const val Card = 0.18f
    const val Menu = 0.15f
    const val Chrome = 0.1f
}

/**
 * A soft wash of the accent over glass, [strength] at its strongest: the accent from the
 * top-left corner, where the rim catches the light, easing through its warmer neighbour
 * and fading out by the far corner, so every surface carries a hint of the user's colour.
 * Lighter in the light theme, where a tint shows far more readily.
 */
@Composable
private fun Modifier.accentSheen(strength: Float): Modifier {
    val palette = MaterialTheme.accentPalette
    val scale = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 1f else 0.7f
    val a = strength * scale
    return drawBehind {
        drawRect(
            Brush.linearGradient(
                0f to palette.primary.copy(alpha = a),
                0.55f to palette.secondary.copy(alpha = a * 0.45f),
                1f to palette.tertiary.copy(alpha = a * 0.15f),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
    }
}

val GlassRimWidth = 1.5.dp

/**
 * The thin rim [glassSurface] draws around its edge. A light rim reads as a
 * highlight on dark glass; a dark rim reads as one on light glass - pick by
 * the current theme's actual background, not a fixed assumption that the app
 * is always dark. The rim is brightest at the top-left and bottom-right edges,
 * like light catching real glass.
 */
@Composable
fun glassRimBrush(): Brush {
    val rimColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color.Black
    val rimStrength = if (rimColor == Color.White) 1f else 0.5f
    return Brush.linearGradient(
        colors = listOf(
            rimColor.copy(alpha = 0.38f * rimStrength),
            rimColor.copy(alpha = 0.08f * rimStrength),
            rimColor.copy(alpha = 0.22f * rimStrength),
        ),
    )
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
    blurRadius: Dp = LocalGlassSettings.current.backdropBlur,
) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier = modifier.fillMaxSize()) {
        MediaArtFill(
            coverArt = coverArt,
            colorSeed = colorSeed,
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius),
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


/**
 * The faint wash behind a highlighted row (the song playing, the option chosen):
 * the accent's [glassSurface] at [GlassAlpha.Highlight], easing into its
 * neighbour color toward the row's end.
 */
@Composable
fun Modifier.accentWash(shape: Shape): Modifier {
    val palette = MaterialTheme.accentPalette
    return glassSurface(shape = shape, hazeState = null, tint = palette.primary, alpha = GlassAlpha.Highlight, rim = false)
        // Already clipped to [shape] by the glass.
        .background(Brush.horizontalGradient(listOf(Color.Transparent, palette.secondary.copy(alpha = GlassAlpha.Highlight))))
        .border(GlassRimWidth, glassRimBrush(), shape)
}
