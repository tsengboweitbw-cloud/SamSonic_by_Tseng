package com.example.samsonic.ui.autodj

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.data.AutoDjMode
import com.example.samsonic.ui.components.ToggleChip
import com.example.samsonic.ui.settings.GroupLabel
import com.example.samsonic.ui.theme.scrollEdgeFades

/**
 * All of Auto DJ's settings, for its panel in Now Playing and its menu in Settings:
 * the mode, from a row of chips, then what it follows and its filters. Give it a
 * bounded height; it scrolls within.
 */
@Composable
fun AutoDjPanel(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val settings = container.autoDjSettings
    val config by settings.config.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier.scrollEdgeFades(listState),
        state = listState,
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        autoDjSettingsItems(config, settings, container.repository) {
            Column {
                GroupLabel(stringResource(R.string.auto_dj_group_mode))
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AutoDjMode.entries.forEach { mode ->
                        ToggleChip(autoDjModeLabel(mode), selected = config.mode == mode, onClick = { settings.setMode(mode) })
                    }
                }
            }
        }
    }
}
