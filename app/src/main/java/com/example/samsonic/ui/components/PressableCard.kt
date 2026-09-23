package com.example.samsonic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.OneUiRow

/**
 * How far the press plate reaches past the card; under half the tightest grid gutter (10dp).
 * A lazy row clips to its bounds, so it needs this much vertical content padding.
 */
val PlateOutset = 6.dp

/**
 * A grid or carousel card (album, artist, playlist) that shows its press feedback in the
 * shared rounded [OneUiRow.Shape], like list rows. The ripple is drawn on a plate just
 * larger than the card and clipped to that shape on its own, so the card's content is
 * never clipped - cover art keeps the corner radius the user picked.
 */
@Composable
fun PressableCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)) {
        content()
        Box(
            Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val outset = PlateOutset.roundToPx()
                    val plate = measurable.measure(
                        Constraints.fixed(constraints.maxWidth + outset * 2, constraints.maxHeight + outset * 2),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) { plate.place(-outset, -outset) }
                }
                .clip(OneUiRow.Shape)
                .indication(interaction, ripple()),
        )
    }
}
