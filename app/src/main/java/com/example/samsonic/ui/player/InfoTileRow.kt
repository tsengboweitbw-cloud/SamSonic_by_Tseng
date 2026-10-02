package com.example.samsonic.ui.player

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.samsonic.ui.components.pressClickable
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

/**
 * Now Playing's info: the first of [capsules] alone in one line, and the rest in a small
 * window under it that a tap on it opens. Holds one line's height even when empty, so the
 * page doesn't jump as a song loads.
 */
@Composable
internal fun InfoCapsuleRow(capsules: List<InfoCapsule>) {
    var open by remember { mutableStateOf(false) }
    val first = capsules.firstOrNull()
    val rest = capsules.drop(1)
    // Nothing left to show: close, so the window isn't there when details arrive later.
    if (rest.isEmpty() && open) open = false
    Box(Modifier.heightIn(min = CapsuleHeight.toDp())) {
        if (first != null) {
            InfoCapsuleItem(
                first,
                expandable = rest.isNotEmpty(),
                modifier = if (rest.isNotEmpty()) Modifier.pressClickable(onClick = { open = !open }, pressedScale = 0.94f) else Modifier,
            )
            if (open) InfoWindow(rest, onDismiss = { open = false })
        }
    }
}

/** The rest of the info capsules as a small floating window, under the first capsule (above it if there's no room). */
@Composable
private fun InfoWindow(capsules: List<InfoCapsule>, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val position = remember(density) { BelowAnchor(gap = with(density) { 4.dp.roundToPx() }, margin = with(density) { 12.dp.roundToPx() }) }
    val shape = RoundedCornerShape(16.dp)
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.85f, transformOrigin = TransformOrigin(0f, 0f)),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    // Room for the shadow to spread inside the popup's window.
                    .padding(12.dp)
                    .shadow(8.dp, shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                capsules.forEach { InfoWindowRow(it) }
            }
        }
    }
}

@Composable
private fun InfoWindowRow(capsule: InfoCapsule) {
    val content = if (capsule.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        val tile = capsule.tile
        if (tile is InfoTile.Glyph) {
            Icon(
                tile.icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .size(24.dp)
                    .background(content.copy(alpha = if (capsule.active) 0.16f else 0.12f), CircleShape)
                    .padding(5.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = capsule.description,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = capsule.text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Under the anchor, left edges together and kept [margin] inside the screen; above it if it won't fit below. */
private class BelowAnchor(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left - margin).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gap - margin
        val y = if (below + popupContentSize.height <= windowSize.height) below else anchorBounds.top - gap - popupContentSize.height + margin
        return IntOffset(x, y.coerceAtLeast(0))
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
