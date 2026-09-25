package com.example.samsonic.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * A few large, very soft pools of the [AccentPalette] behind the app's pages:
 * enough color for the frosted bars and cards to pick up as they blur what's
 * behind them, faint enough that the page itself still reads as the theme's
 * own background. Static, so it costs nothing while scrolling.
 */
@Composable
fun AmbientGlow(modifier: Modifier = Modifier) {
    val palette = MaterialTheme.accentPalette
    // Light backgrounds show a tint far more readily than dark ones.
    val strength = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.2f else 0.13f
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        fun pool(color: Color, x: Float, y: Float, radius: Float, alpha: Float) {
            val center = Offset(w * x, h * y)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        // The accent leads from the top, where the big titles sit; its companions
        // trail down the sides, and the contrasting spark stays small, low and faint.
        pool(palette.primary, x = 0.1f, y = 0.02f, radius = w * 0.95f, alpha = strength)
        pool(palette.secondary, x = 1f, y = 0.3f, radius = w * 0.8f, alpha = strength * 0.8f)
        pool(palette.tertiary, x = 0f, y = 0.68f, radius = w * 0.8f, alpha = strength * 0.7f)
        pool(palette.highlight, x = 0.95f, y = 0.98f, radius = w * 0.6f, alpha = strength * 0.5f)
    }
}
