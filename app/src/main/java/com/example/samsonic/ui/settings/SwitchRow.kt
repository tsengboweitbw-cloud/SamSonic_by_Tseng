package com.example.samsonic.ui.settings

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.OneUiRow
import com.example.samsonic.ui.theme.OneUiSwitch
import com.example.samsonic.ui.theme.switchToggleable

/**
 * A settings row with a One UI switch at the end. The whole row is the tap
 * target, and it shares its press state with the switch, so pressing anywhere
 * on the row presses the thumb too. New toggles in Settings should use this.
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
    val interactionSource = remember { MutableInteractionSource() }
    val hintState = rememberRowHint()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Same inset and rounded press shape as oneUiRowClickable.
            .padding(OneUiRow.Inset)
            .clip(OneUiRow.Shape)
            .hintHold(hintState)
            .then(
                if (enabled) {
                    Modifier.switchToggleable(
                        checked = checked,
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onCheckedChange = { on ->
                            onCheckedChange(on)
                            if (on && hintWhenTurnedOn) hintState.show()
                        },
                        onLongClick = hintLongPress(hintState, hint),
                    )
                } else {
                    Modifier
                        .semantics { disabled() }
                        .combinedClickable(onLongClick = hintLongPress(hintState, hint), onClick = { hintState.show() })
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
        OneUiSwitch(checked = checked, interactionSource = interactionSource, modifier = dim)
        RowHint(hintState, hint)
    }
}

/** How faint a settings row that can't be used now is. */
internal const val DisabledAlpha = 0.38f
