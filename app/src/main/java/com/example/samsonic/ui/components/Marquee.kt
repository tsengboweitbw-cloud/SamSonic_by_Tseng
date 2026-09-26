package com.example.samsonic.ui.components

import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Whether [marqueeWhenLong] text scrolls: false where it can't be seen, such as Now Playing
 * while it's folded away, where it would draw a frame every vsync for nothing.
 */
val LocalMarqueeRunning = staticCompositionLocalOf { true }

/**
 * For a single line of text that may not fit: if it's too long, after a pause it scrolls
 * round and round, pausing again each time it comes back to its start; if it fits, it
 * stays still. Put it on a one-line Text (it replaces the ellipsis). Held still where
 * [LocalMarqueeRunning] is false.
 */
@Composable
fun Modifier.marqueeWhenLong(): Modifier = basicMarquee(
    iterations = if (LocalMarqueeRunning.current) Int.MAX_VALUE else 0,
    initialDelayMillis = 1500,
    repeatDelayMillis = 2000,
    spacing = MarqueeSpacing(32.dp),
    velocity = 30.dp,
)
