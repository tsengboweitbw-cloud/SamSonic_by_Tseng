package com.example.samsonic.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

// One UI 9.0 corner scale: large, deliberate rounding rather than Material's
// tighter defaults. Named constants so screens stop hardcoding dp literals.
object OneUiRadius {
    val Chip = 12.dp
    val Art = 24.dp
    val Card = 28.dp
    val Hero = 32.dp
    val Pill = 100.dp
}

// List rows (songs, albums, library, settings, Up Next): the playing-row highlight, the
// press feedback and the lifted swipe card all use this one rounded shape, inset from
// the edges of the list.
object OneUiRow {
    val Shape = RoundedCornerShape(OneUiRadius.Art)
    val Inset = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
}

/**
 * Makes this list row a press target in the shared [OneUiRow] shape: inset by
 * [OneUiRow.Inset] and clipped to [OneUiRow.Shape], so its ripple is the same rounded
 * shape as the playing-row highlight rather than a full-width rectangle. Chain the
 * row's own inner padding after it, reduced by the inset.
 */
fun Modifier.oneUiRowClickable(onClick: () -> Unit): Modifier =
    padding(OneUiRow.Inset).clip(OneUiRow.Shape).clickable(onClick = onClick)

// Shared sizing for the floating chrome (bottom nav bar, mini player) so both
// bars are literally the same thickness, not just visually similar pills.
object OneUiChrome {
    val BarHeight = 64.dp
}

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(OneUiRadius.Chip),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(OneUiRadius.Card),
    extraLarge = RoundedCornerShape(OneUiRadius.Hero),
)
