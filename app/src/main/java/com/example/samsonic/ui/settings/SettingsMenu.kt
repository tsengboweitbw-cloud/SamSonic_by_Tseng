package com.example.samsonic.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.common.GuardChrome
import com.example.samsonic.ui.common.pageScrim
import com.example.samsonic.ui.player.MorphPanel
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.player.MorphGlassBase
import com.example.samsonic.ui.player.washToReach
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.LocalGlassSettings
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState

/**
 * How far up from the page's bottom the floating chrome (mini player, nav bar) reaches,
 * for a [SettingsMenu] to keep its card clear of: set by the page the menus are over.
 */
internal val LocalMenuBottomInset = staticCompositionLocalOf { 0.dp }

// The same dim as the Library view options' scrim.
private const val ScrimAlpha = 0.32f

// How much a row shrinks per unit of its menu's close overshoot (a few % at most).
private const val LandingSqueeze = 1.5f

/**
 * A settings row's secondary menu: a glass card with [title] that grows out of
 * the row ([MorphPanel]) over the dimmed page, and folds back into it on back,
 * a tap outside it, or [PanelState.close]. The row marks itself with
 * [menuOrigin]. [content] is composed only while the card is out, so its state
 * starts fresh at every open.
 * [haze] is the page content's haze source, which the card's glass blurs.
 */
@Composable
internal fun SettingsMenu(
    panel: PanelState,
    haze: HazeState,
    title: String,
    // For an origin with no glass of its own, such as a song row (see MorphPanel).
    originRadius: Dp? = null,
    // For content that animates its size while the card is out (see MorphPanel).
    resizable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = panel.isOpen) { panel.close() }
    val showing by remember(panel) { derivedStateOf { panel.progress > 0f } }
    if (!showing) return
    val glass = LocalGlassSettings.current
    // Over a page with the floating chrome, that can't be used either: a tap on it closes the menu.
    val bottomInset = LocalMenuBottomInset.current
    GuardChrome(active = bottomInset > 0.dp, dim = { ScrimAlpha * panel.progress }, onDismiss = { if (panel.isOpen) panel.close() })
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pageScrim(clearBottom = bottomInset) { ScrimAlpha * panel.progress }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = panel.isOpen,
                onClick = { panel.close() },
            )
            // Clear of the floating chrome below (see LocalMenuBottomInset), or of a text
            // field's keyboard, whichever reaches higher, so the card is centred in the
            // page between them and the title.
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets(bottom = bottomInset)))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        MorphPanel(
            panel = panel,
            icon = null,
            // Samples the page undimmed, as the Library view options do, so the same dense base.
            surface = Modifier.glassSurface(
                shape = RoundedCornerShape(OneUiRadius.Card),
                hazeState = haze,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                // Starts thin; the wash thickens it to the panel's density.
                alpha = MorphGlassBase,
                blurRadius = glass.panelBlur,
                downsample = true,
                scaleOpacity = false,
                rim = false,
            ),
            wash = MaterialTheme.colorScheme.surfaceContainerHigh,
            washAlpha = washToReach(MorphGlassBase, GlassAlpha.Panel * glass.panelOpacity),
            modifier = Modifier
                .fillMaxWidth()
                // Swallows taps so only the space around the card dismisses.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            radius = OneUiRadius.Card,
            // A short card may sit above its row, where a drag would have no travel to follow.
            dragToClose = false,
            originRadius = originRadius,
            resizable = resizable,
        ) {
            Column(Modifier.padding(vertical = 20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

/** Marks this row as where [panel]'s menu grows from, and has it catch the menu's close bounce. */
internal fun Modifier.menuOrigin(panel: PanelState): Modifier = this
    .onGloballyPositioned { panel.origin = it.boundsInRoot() }
    .graphicsLayer {
        val squeeze = 1f - panel.landing * LandingSqueeze
        scaleX = squeeze
        scaleY = squeeze
    }
