package com.example.samsonic.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
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
