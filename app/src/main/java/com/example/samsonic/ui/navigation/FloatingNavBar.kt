package com.example.samsonic.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState

/**
 * One UI 9.0 floating pill bottom navigation: a frosted-glass bar over the
 * scrolling content, in place of Material's edge-to-edge opaque NavigationBar.
 * The selected tab widens into its own lighter pill showing icon + label;
 * the rest stay icon-only.
 */
@Composable
fun FloatingNavBar(
    destinations: List<BottomDestination>,
    selectedRoutes: Set<String>,
    onSelect: (String) -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .glassSurface(
                shape = RoundedCornerShape(OneUiRadius.Pill),
                hazeState = hazeState,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = GlassAlpha.Nav,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEach { destination ->
            val selected = destination.route in selectedRoutes
            // Selected tab takes extra room for its label.
            val weight by animateFloatAsState(if (selected) 1.8f else 1f, label = "navWeight")
            Row(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(OneUiRadius.Pill))
                    .then(
                        if (selected) {
                            Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(destination.route) }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = destination.label,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    Text(
                        text = destination.label,
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
