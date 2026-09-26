package com.example.samsonic.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.data.LibrarySection
import com.example.samsonic.data.LibraryViewMode
import com.example.samsonic.ui.common.GuardChrome
import com.example.samsonic.ui.common.LocalChromeGuard
import com.example.samsonic.ui.player.MorphPanel
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.settings.menuOrigin
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.LocalGlassSettings
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState

// The Library view options' dim.
private const val ScrimAlpha = 0.32f

/**
 * A "see all" page's own view options, kept apart from the Library tab's: the
 * view button, floating top right across from the back button, itself grows
 * into the panel ([MorphPanel]) - hidden while its glass is out, its icon riding
 * along - and the panel lands where the Library's view options sit: full width
 * under the Library's title row, with the same settings for [section].
 * [haze] is the page content's haze source, which the panel's glass blurs.
 * Placed in a page under the status bar, as the Library's title is.
 */
@Composable
internal fun BoxScope.CollectionViewMenu(section: LibrarySection, title: String, haze: HazeState) {
    val layoutManager = LocalAppContainer.current.libraryLayoutManager
    val layouts by layoutManager.layouts.collectAsStateWithLifecycle()
    val layout = layouts.getValue(section)
    val scope = rememberCoroutineScope()
    val panel = remember { PanelState(scope) }
    val showing by remember(panel) { derivedStateOf { panel.progress > 0f } }
    val icon = if (layout.mode == LibraryViewMode.GRID) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList

    LibraryViewButton(
        mode = layout.mode,
        open = false,
        onClick = { panel.open() },
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = 16.dp, top = 8.dp)
            .menuOrigin(panel)
            // The panel's glass is this button while it's out, so there's just the one.
            .graphicsLayer { alpha = if (showing) 0f else 1f },
    )

    BackHandler(enabled = panel.isOpen) { panel.close() }
    if (!showing) return
    val glass = LocalGlassSettings.current
    val statusBar = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    // The floating chrome can't be used while it's out either: a tap on it closes it, and it
    // dims the chrome with the page there, so this dim stops above it.
    GuardChrome(active = true, dim = { ScrimAlpha * panel.progress }, onDismiss = { if (panel.isOpen) panel.close() })
    val chromeHeight = LocalChromeGuard.current?.height ?: 0.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Up over the status bar too, so the whole page dims together.
            .drawBehind {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(0f, -statusBar),
                    size = Size(size.width, (size.height + statusBar - chromeHeight.toPx()).coerceAtLeast(0f)),
                    alpha = (ScrimAlpha * panel.progress).coerceIn(0f, 1f),
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = panel.isOpen,
                onClick = { panel.close() },
            ),
    ) {
        MorphPanel(
            panel = panel,
            icon = icon,
            // The navigation bar's glass, its opacity and blur settings included, as its
            // view button's is.
            surface = Modifier.glassSurface(
                shape = RoundedCornerShape(OneUiRadius.Card),
                hazeState = haze,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = GlassAlpha.Nav,
                blurRadius = glass.blurRadius,
                rim = false,
            ),
            modifier = Modifier
                .padding(top = libraryTitleRowHeight(), start = 16.dp, end = 16.dp)
                .fillMaxWidth()
                // Swallows taps so only the space around the panel dismisses.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            radius = OneUiRadius.Card,
            // As the Library's: a tap outside or back closes it.
            dragToClose = false,
        ) {
            ViewOptionsPanel(
                sectionName = title,
                layout = layout,
                onLayoutChange = { layoutManager.setLayout(section, it) },
            )
        }
    }
}

/**
 * The Library title row's height (see [LibraryScreen]): its large title or the
 * 48dp view button, whichever is taller, and 12dp above and below. The Library's
 * view options sit right under it, so this page's go there too.
 */
@Composable
private fun libraryTitleRowHeight(): Dp {
    val style = MaterialTheme.typography.displaySmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val title = stringResource(R.string.library_title)
    val titleHeight = remember(style, density, title) {
        with(density) { measurer.measure(title, style).size.height.toDp() }
    }
    return maxOf(titleHeight, 48.dp) + 24.dp
}
