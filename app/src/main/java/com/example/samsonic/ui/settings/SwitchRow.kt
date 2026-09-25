package com.example.samsonic.ui.settings

import androidx.compose.foundation.LocalIndication
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
 */
@Composable
internal fun SwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String? = null,
    hintWhenTurnedOn: Boolean = false,
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
            .switchToggleable(
                checked = checked,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onCheckedChange = { on ->
                    onCheckedChange(on)
                    if (on && hintWhenTurnedOn) hintState.show()
                },
                onLongClick = hintLongPress(hintState, hint),
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = rowIconTint(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        OneUiSwitch(checked = checked, interactionSource = interactionSource)
        RowHint(hintState, hint)
    }
}
