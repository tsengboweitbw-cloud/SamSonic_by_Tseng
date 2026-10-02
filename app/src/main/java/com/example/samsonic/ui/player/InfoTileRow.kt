package com.example.samsonic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import com.example.samsonic.ui.components.marqueeWhenLong

// Every tile is this exact size, whether it holds an icon or a format name, so the
// rows' text all starts at the same x and the tiles stack as one neat column.
// In sp, so they grow with the text in them and beside them (a larger font size, a
// tablet, DeX) rather than cutting it off.
private val TileWidth = 32.sp
private val TileHeight = 18.sp
private val TileShape = RoundedCornerShape(5.dp)
private val TileIconSize = 13.sp

/** What a Now Playing info row's tile shows: an icon, or a short text such as "FLAC". */
internal sealed interface InfoTile {
    data class Glyph(val icon: ImageVector) : InfoTile
    data class Label(val text: String) : InfoTile
}

/**
 * One of Now Playing's small info lines: a [tile] naming what the line is about
 * ([description] says it for screen readers), then its [text]. [active] tints the
 * tile with the accent; off, it's a quiet grey.
 */
@Composable
internal fun InfoTileRow(tile: InfoTile, description: String, text: String, active: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = TileHeight.toDp())) {
        val content = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            modifier = Modifier
                .size(TileWidth.toDp(), TileHeight.toDp())
                .background(content.copy(alpha = if (active) 0.16f else 0.12f), TileShape)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            when (tile) {
                is InfoTile.Glyph -> Icon(tile.icon, contentDescription = null, tint = content, modifier = Modifier.size(TileIconSize.toDp()))
                is InfoTile.Label -> TileLabel(tile.text, content)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.marqueeWhenLong(),
        )
    }
}

/** A blank line the height of an [InfoTileRow], holding a row's place until it has something to show. */
@Composable
internal fun InfoTileRowPlaceholder() {
    Spacer(Modifier.height(TileHeight.toDp()))
}

/** A format name sized to fit the tile: smaller the longer it is ("DSF", "FLAC", "OGG/OPUS"...). */
@Composable
private fun TileLabel(text: String, color: Color) {
    val size = when {
        text.length <= 3 -> 9.sp
        text.length == 4 -> 8.sp
        else -> 6.5.sp
    }
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = size,
            lineHeight = size,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        ),
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

/** [this] (in sp) as dp at the current font size. */
@Composable
private fun TextUnit.toDp(): Dp = with(LocalDensity.current) { this@toDp.toDp() }
