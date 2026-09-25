package com.example.samsonic.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.samsonic.R
import com.example.samsonic.playback.PlayerState
import kotlin.math.roundToInt

private const val MaxSleepMinutes = 60

/**
 * Sleep timer as a 0-60 minute slider, 0 meaning off. Setting the timer
 * restarts its countdown, so dragging only moves the label and the timer is
 * set once, on release.
 */
@Composable
internal fun SleepTimerRow(player: PlayerState) {
    // Minutes under the finger while dragging; null when not dragging.
    var dragMinutes by remember { mutableStateOf<Int?>(null) }
    val minutes = dragMinutes ?: player.sleepTimerMinutes ?: 0
    SliderRow(
        icon = Icons.Filled.Bedtime,
        title = stringResource(R.string.settings_sleep_timer),
        hint = stringResource(R.string.settings_sleep_timer_hint),
        valueLabel = if (minutes == 0) stringResource(R.string.settings_off) else stringResource(R.string.settings_sleep_timer_minutes, minutes),
        value = minutes.toFloat(),
        valueRange = 0f..MaxSleepMinutes.toFloat(),
        onValueChange = { dragMinutes = it.roundToInt() },
        onValueChangeFinished = {
            dragMinutes?.let { player.setSleepTimer(it.takeIf { m -> m > 0 }) }
            dragMinutes = null
        },
    )
}
