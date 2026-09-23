package com.example.samsonic.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Top-left back button on its own frosted glass circle: the same blur, tint
 * and opacity as the floating nav bar and mini player, so the page's chrome
 * reads as one set.
 *
 * The page already sits inside the NavHost's hazeSource, and a hazeEffect
 * nested in its own source can draw recursively. So [hazeState] must come
 * from a source around just the page's list (see [backButtonHazeSource]),
 * with the button floating outside it.
 */
@Composable
fun GlassBackButton(onClick: () -> Unit, hazeState: HazeState?, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .glassSurface(
                shape = CircleShape,
                hazeState = hazeState,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = GlassAlpha.Nav,
            ),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

/**
 * Makes this list the backdrop a [GlassBackButton] blurs. Chain it after the
 * list's scrollTopFade, so the button blurs the rows unfaded, like the nav bar.
 */
fun Modifier.backButtonHazeSource(state: HazeState): Modifier = graphicsLayer().hazeSource(state)

/** Room a list leaves at its top so its first row starts below a floating [GlassBackButton]. */
val BackButtonClearance = 8.dp + 48.dp
