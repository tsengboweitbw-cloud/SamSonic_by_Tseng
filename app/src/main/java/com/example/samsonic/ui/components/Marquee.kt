package com.example.samsonic.ui.components

import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * For a single line of text that may not fit: if it's too long, after a pause it scrolls
 * round and round, pausing again each time it comes back to its start; if it fits, it
 * stays still. Put it on a one-line Text (it replaces the ellipsis).
 */
fun Modifier.marqueeWhenLong(): Modifier = basicMarquee(
    iterations = Int.MAX_VALUE,
    initialDelayMillis = 1500,
    repeatDelayMillis = 2000,
    spacing = MarqueeSpacing(32.dp),
    velocity = 30.dp,
)
