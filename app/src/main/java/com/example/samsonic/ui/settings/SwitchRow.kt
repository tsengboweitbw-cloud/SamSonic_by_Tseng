package com.example.samsonic.ui.settings

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.OneUiRow
import com.example.samsonic.ui.theme.OneUiSwitch

/**
 * A settings row with a One UI switch at the end. Only the switch toggles, so
 * a stray tap on the row (or a scroll that starts on it) can't flip a setting;
 * the rest of the row just takes the long press for the hint. New toggles in
 * Settings should use this.
 *
 * Its [hint] shows on a long press (see [RowHint]); with [hintWhenTurnedOn], also
 * on its own as the switch goes on, for a hint that must be seen then.
 *
 * Not [enabled], it's dimmed and won't toggle, still showing [checked]; a tap shows its
 * [hint] instead, which should say why.
 */
@Composable
internal fun SwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String? = null,
    hintWhenTurnedOn: Boolean = false,
    enabled: Boolean = true,
) {
    val hintState = rememberRowHint()
    val longPress = hintLongPress(hintState, hint)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Same inset and rounded press shape as oneUiRowClickable.
            .padding(OneUiRow.Inset)
            .clip(OneUiRow.Shape)
            .hintHold(hintState)
            .then(
                if (enabled) {
                    // Read as one control by TalkBack: the title with the switch's toggle.
                    Modifier
                        .semantics(mergeDescendants = true) {}
                        .then(if (longPress != null) Modifier.pointerInput(longPress) { detectTapGestures(onLongPress = { longPress() }) } else Modifier)
                } else {
                    Modifier
                        .semantics { disabled() }
                        .combinedClickable(onLongClick = longPress, onClick = { hintState.show() })
                },
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Dimmed piece by piece, not as a whole, so the hint it shows isn't.
        val dim = if (enabled) Modifier else Modifier.alpha(DisabledAlpha)
        Icon(icon, contentDescription = null, tint = rowIconTint(), modifier = dim.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = dim.weight(1f))
        Spacer(Modifier.width(12.dp))
        OneUiSwitch(
            checked = checked,
            onCheckedChange = if (enabled) {
                { on ->
                    onCheckedChange(on)
                    if (on && hintWhenTurnedOn) hintState.show()
                }
            } else {
                null
            },
            modifier = dim,
        )
        RowHint(hintState, hint)
    }
}

/** How faint a settings row that can't be used now is. */
internal const val DisabledAlpha = 0.38f
