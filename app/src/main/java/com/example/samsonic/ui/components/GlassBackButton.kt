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
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.glassSurface

/**
 * Top-left back button on its own frosted glass circle, in the same tint and
 * opacity as the floating nav bar so the page's chrome reads as one set.
 *
 * No hazeState: these pages sit inside the NavHost's own hazeSource subtree
 * (see MediaLists.SongRow), so this is the flat veil version of the nav glass.
 */
@Composable
fun GlassBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .glassSurface(
                shape = CircleShape,
                hazeState = null,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = GlassAlpha.Nav,
            ),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

/** Room a list leaves at its top so its first row starts below a floating [GlassBackButton]. */
val BackButtonClearance = 8.dp + 48.dp
