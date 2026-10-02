package com.example.samsonic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.setValue
import com.example.samsonic.ui.components.pressClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.theme.LocalGlassSettings
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


// In sp, so the capsules grow with the text in them and beside them (a larger font
// size, a tablet, DeX) rather than cutting it off.
private val CapsuleHeight = 20.sp
private val CapsuleIconSize = 11.sp

/** What a Now Playing info capsule leads with: an icon, or a short text such as "FLAC". */
internal sealed interface InfoTile {
    data class Glyph(val icon: ImageVector) : InfoTile
    data class Label(val text: String) : InfoTile
}

/**
 * One of Now Playing's info capsules: a [tile] naming what it is about ([description]
 * says it for screen readers), then its [text]. [active] tints it with the accent; off,
 * it's a quiet grey.
 */
internal data class InfoCapsule(
    val tile: InfoTile,
    val description: String,
    val text: String,
    val active: Boolean = true,
)

/** The audio info capsule's window ([PlayerSheetState.format]), for the capsule to open; null where there's none. */
internal val LocalInfoPanel = compositionLocalOf<PanelState?> { null }

/**
 * Now Playing's info: the first of [capsules] alone in one line, and a tap on it opens a
 * small window, growing out of it, with them all. Holds one line's height even when
 * empty, so the page doesn't jump as a song loads.
 */
@Composable
internal fun InfoCapsuleRow(capsules: List<InfoCapsule>) {
    val panel = LocalInfoPanel.current
    val first = capsules.firstOrNull()
    val expandable = panel != null && capsules.size > 1
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(Modifier.heightIn(min = CapsuleHeight.toDp())) {
        if (first != null) {
            InfoCapsuleItem(
                first,
                expandable = expandable,
                modifier = Modifier
                    .onGloballyPositioned { bounds = it.boundsInRoot() }
                    // Gone under its window as that grows, back as it folds.
                    .graphicsLayer { alpha = 1f - ((panel?.progress ?: 0f) * 4f).coerceAtMost(1f) }
                    .then(
                        if (expandable) {
                            Modifier.pressClickable(
                                onClick = {
                                    panel?.origin = bounds
                                    panel?.open()
                                },
                                pressedScale = 0.94f,
                            )
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

/**
 * The info capsule's window: a small glass card with a row for each of the capsule's
 * lines (the format, the output, the device, the device rate), like the panels over a
 * dimmed Now Playing. It grows out of the capsule and sits where it is (kept inside the
 * screen); tapping outside it or back folds it into the capsule again.
 */
@Composable
internal fun InfoCapsulePanel(panel: PanelState, haze: HazeState) {
    val song = LocalPlayerState.current.currentSong ?: return
    val capsules = rememberInfoCapsules(song)
    val showing by remember(panel) { derivedStateOf { panel.progress > 0f } }
    if (!showing) return
    val shape = RoundedCornerShape(OneUiRadius.Card)
    val dim = dialogDimAmount()
    val glass = LocalGlassSettings.current
    val settled by remember(panel) { derivedStateOf { panel.isSettled } }
    val margin = with(LocalDensity.current) { 16.dp.roundToPx() }
    val tileWidth = remember { mutableIntStateOf(0) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = dim * panel.progress)) }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = panel.isOpen,
                onClick = { panel.close() },
            ),
    ) {
        MorphPanel(
            panel = panel,
            icon = null,
            surface = Modifier.glassSurface(
                shape = shape,
                hazeState = haze,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = MorphGlassBase,
                blurRadius = glass.playerBlur,
                downsample = !settled,
                noiseFactor = 0f,
                scaleOpacity = false,
                rim = false,
            ),
            wash = MaterialTheme.colorScheme.surfaceContainerHigh,
            washAlpha = washToReach(MorphGlassBase, glass.playerAlpha),
            modifier = Modifier
                // Its top-left at the capsule's, as far as the screen allows.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = 0, minHeight = 0, maxWidth = (constraints.maxWidth - margin * 2).coerceAtLeast(0)),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val x = panel.origin.left.roundToInt().coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
                        val y = panel.origin.top.roundToInt().coerceIn(margin, (constraints.maxHeight - placeable.height - margin).coerceAtLeast(margin))
                        placeable.place(x, y)
                    }
                }
                // Swallows taps so only the space around the card dismisses.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            radius = OneUiRadius.Card,
            // Next to its capsule, with nothing to pull down from.
            dragToClose = false,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.width(IntrinsicSize.Max).padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                capsules.forEach { InfoWindowRow(it, tileWidth) }
            }
        }
    }
}

/** One of the window's rows: the capsule's icon (or the format's label), its name, then its value. */
@Composable
private fun InfoWindowRow(capsule: InfoCapsule, tileWidth: MutableIntState) {
    val content = if (capsule.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(24.dp)
                .widthIn(min = maxOf(24.dp, with(LocalDensity.current) { tileWidth.intValue.toDp() }))
                // Every row's tile as wide as the widest (the format's label, usually).
                .onSizeChanged { if (it.width > tileWidth.intValue) tileWidth.intValue = it.width }
                .background(content.copy(alpha = if (capsule.active) 0.16f else 0.12f), CircleShape),
        ) {
            when (val tile = capsule.tile) {
                is InfoTile.Glyph -> Icon(tile.icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
                is InfoTile.Label -> Text(
                    text = tile.text,
                    color = content,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = capsule.description,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.widthIn(min = 20.dp).weight(1f))
        Text(
            text = capsule.text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun InfoCapsuleItem(capsule: InfoCapsule, expandable: Boolean, modifier: Modifier = Modifier) {
    val content = if (capsule.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(content.copy(alpha = if (capsule.active) 0.16f else 0.12f), CircleShape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        when (val tile = capsule.tile) {
            is InfoTile.Glyph -> Icon(
                tile.icon,
                contentDescription = capsule.description,
                tint = content,
                modifier = Modifier.size(CapsuleIconSize.toDp()),
            )
            is InfoTile.Label -> Text(
                text = tile.text,
                color = content,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.semantics { contentDescription = capsule.description },
            )
        }
        if (capsule.text.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = capsule.text,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
        // Says there's more behind a tap.
        if (expandable) {
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp).size(CapsuleIconSize.toDp()),
            )
        }
    }
}

/** [this] (in sp) as dp at the current font size. */
@Composable
private fun TextUnit.toDp(): Dp = with(LocalDensity.current) { this@toDp.toDp() }
