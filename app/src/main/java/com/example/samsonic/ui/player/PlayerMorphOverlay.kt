package com.example.samsonic.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.artSeed
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.components.artPrimaryColor
import com.example.samsonic.ui.components.artSecondaryColor
import com.example.samsonic.ui.components.aspectRatioOrNull
import com.example.samsonic.ui.components.fitAspect
import com.example.samsonic.ui.theme.AccentPalette
import com.example.samsonic.ui.theme.accentPalette
import com.example.samsonic.ui.theme.progressBrush

// Matches the Now Playing cover's shadow, so the hand-off at the end doesn't pop.
private val FullArtShadow = 16.dp

/**
 * Draws the cover and progress line in flight between the mini player and Now
 * Playing (see [PlayerMorphState]). Fills the player sheet's host, above the
 * sheet. Everything happens in the draw phase, so following the moving anchors
 * costs no recomposition or relayout.
 */
@Composable
fun PlayerMorphOverlay(morph: PlayerMorphState, modifier: Modifier = Modifier) {
    val player = LocalPlayerState.current
    val song = player.currentSong ?: return
    val container = LocalAppContainer.current
    val cornerRadius by container.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()

    // Same URL as the Now Playing cover. Loaded up front at full size (no wait for
    // a draw size), so it's ready the first time the morph runs.
    val context = LocalPlatformContext.current
    val request = remember(song.coverArt, context) {
        ImageRequest.Builder(context)
            .data(container.repository.coverArtUrl(song.coverArt, 1000))
            .size(Size.ORIGINAL)
            .build()
    }
    val painter = rememberAsyncImagePainter(request)
    val seed = song.id.artSeed()
    val gradient = listOf(artPrimaryColor(seed), artSecondaryColor(seed))
    val palette = MaterialTheme.accentPalette
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val shadowPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }

    Canvas(modifier) {
        val fraction = morph.fraction
        morph.boundsOf(PlayerElement.Art)?.let { bounds ->
            drawArt(
                square = bounds,
                radius = cornerRadius.toPx(),
                shadow = lerp(0f, FullArtShadow.toPx(), fraction),
                gradient = gradient,
                painter = painter,
                shadowPaint = shadowPaint,
            )
        }
        morph.boundsOf(PlayerElement.Progress)?.let { bounds ->
            val progress = if (song.durationSeconds > 0) {
                (player.positionSeconds / song.durationSeconds).coerceIn(0f, 1f)
            } else 0f
            drawProgress(
                bounds = bounds,
                // The mini line's stroke into the seek bar's resting track stroke.
                stroke = lerp(2.dp.toPx(), 3.dp.toPx(), fraction),
                progress = progress,
                palette = palette,
                trackColor = trackColor,
            )
        }
    }
}

private fun DrawScope.drawArt(
    square: Rect,
    radius: Float,
    shadow: Float,
    gradient: List<Color>,
    painter: Painter,
    shadowPaint: android.graphics.Paint,
) {
    // Same as the real covers: art keeps its proportions inside the square, and only
    // the art gets the corners and shadow. The gradient only stands in until it loads.
    val ratio = painter.intrinsicSize.aspectRatioOrNull()
    val bounds = if (ratio != null) fitAspect(square, ratio) else square
    if (shadow > 0.5f) {
        shadowPaint.color = android.graphics.Color.BLACK
        shadowPaint.setShadowLayer(shadow, 0f, shadow / 3f, Color.Black.copy(alpha = 0.45f).toArgb())
        drawIntoCanvas {
            it.nativeCanvas.drawRoundRect(
                bounds.left, bounds.top, bounds.right, bounds.bottom, radius, radius, shadowPaint,
            )
        }
    }
    val clip = Path().apply { addRoundRect(RoundRect(bounds, CornerRadius(radius))) }
    clipPath(clip) {
        if (ratio == null) {
            drawRect(
                Brush.linearGradient(gradient, start = bounds.topLeft, end = bounds.bottomRight),
                topLeft = bounds.topLeft,
                size = bounds.size,
            )
        }
        translate(bounds.left, bounds.top) {
            with(painter) { draw(bounds.size) }
        }
    }
}

private fun DrawScope.drawProgress(
    bounds: Rect,
    stroke: Float,
    progress: Float,
    palette: AccentPalette,
    trackColor: Color,
) {
    val y = bounds.center.y
    drawLine(trackColor, Offset(bounds.left, y), Offset(bounds.right, y), stroke, StrokeCap.Round)
    if (progress > 0f) {
        val end = Offset(bounds.left + bounds.width * progress, y)
        drawLine(progressBrush(palette, bounds.left, bounds.right), Offset(bounds.left, y), end, stroke, StrokeCap.Round)
    }
}
