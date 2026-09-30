package com.example.samsonic.ui.settings

import kotlin.math.roundToInt
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.layout
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
import com.example.samsonic.ui.common.LocalChromeGuard
import com.example.samsonic.ui.common.pageScrim
import com.example.samsonic.ui.player.MorphPanel
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.player.MorphGlassBase
import com.example.samsonic.ui.player.washToReach
import com.example.samsonic.ui.theme.AccentSheen
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

/**
 * Whether a [SettingsMenu]'s card is a little narrower than the row it grows from, centred on it
 * (Settings' own rows, a full card's width), rather than across the page less a margin
 * (Add to playlist, from a song row or a grid's album card). Set by the page.
 */
internal val LocalMenuMatchesOrigin = staticCompositionLocalOf { false }

// A card not matching its row keeps this far from the page's sides.
private val MenuSideMargin = 20.dp

// A card matching its row is this much narrower each side, centred on it.
private val MenuInsetFromRow = 12.dp

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
    val matchOrigin = LocalMenuMatchesOrigin.current
    GuardChrome(active = bottomInset > 0.dp, dim = { ScrimAlpha * panel.progress }, onDismiss = { if (panel.isOpen) panel.close() })
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Down to where the chrome's own dim takes over (ChromeGuardLayer), not the page's
            // room for the chrome: those differ beside the rail, and a strip was dimmed twice.
            .pageScrim(clearBottom = if (bottomInset > 0.dp) LocalChromeGuard.current?.height ?: bottomInset else 0.dp) { ScrimAlpha * panel.progress }
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
            .padding(vertical = 16.dp),
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
                sheen = AccentSheen.Menu,
                scaleOpacity = false,
                rim = false,
            ),
            wash = MaterialTheme.colorScheme.surfaceContainerHigh,
            washAlpha = washToReach(MorphGlassBase, GlassAlpha.Panel * glass.panelOpacity),
            modifier = Modifier
                // As wide as its row and in line with it (see LocalMenuMatchesOrigin), else
                // across the page less a margin either side.
                .layout { measurable, constraints ->
                    val room = constraints.maxWidth
                    val margin = MenuSideMargin.roundToPx()
                    val origin = panel.origin
                    val match = matchOrigin && origin.width > 0f
                    val width = if (match) {
                        (origin.width - MenuInsetFromRow.toPx() * 2).roundToInt().coerceIn(0, room)
                    } else {
                        (room - margin * 2).coerceAtLeast(0)
                    }
                    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(room, placeable.height) {
                        // Centred on its row, which is centred on its card: where this room
                        // starts across the screen is known only as it's placed.
                        val x = if (match) {
                            val left = coordinates?.positionInRoot()?.x ?: 0f
                            (origin.center.x - left - width / 2f).roundToInt().coerceIn(0, room - width)
                        } else {
                            margin
                        }
                        placeable.place(x, 0)
                    }
                }
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
