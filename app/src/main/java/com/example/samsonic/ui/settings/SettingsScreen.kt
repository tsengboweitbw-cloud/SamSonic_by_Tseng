package com.example.samsonic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.data.ThemeManager
import com.example.samsonic.data.ThemeMode
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface
import com.example.samsonic.ui.theme.toHexRgb

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val player = LocalPlayerState.current
    val container = LocalAppContainer.current
    val credentials by container.sessionManager.credentials.collectAsStateWithLifecycle()
    val themeMode by container.themeManager.themeMode.collectAsStateWithLifecycle()
    val accentColor by container.themeManager.accentColor.collectAsStateWithLifecycle()
    var showColorPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + contentPaddingBottom),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(text = "Settings", style = MaterialTheme.typography.titleLarge)
            }
        }

        item { GroupLabel("Server") }
        item {
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text = credentials?.serverUrl ?: "Not connected", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = credentials?.username?.let { "Signed in as $it" } ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (credentials != null) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Connected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        item { GroupLabel("Playback") }
        item {
            SettingsCard {
                NavRow(
                    icon = Icons.Filled.Bedtime,
                    title = "Sleep timer",
                    value = player.sleepTimerMinutes?.let { "$it min" } ?: "Off",
                    onClick = {
                        val options = listOf(null, 15, 30, 45, 60)
                        val currentIdx = options.indexOf(player.sleepTimerMinutes)
                        player.setSleepTimer(options[(currentIdx + 1) % options.size])
                    },
                )
            }
        }

        item { GroupLabel("Appearance") }
        item {
            SettingsCard {
                NavRow(
                    icon = Icons.Filled.DarkMode,
                    title = "Theme",
                    value = when (themeMode) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    },
                    onClick = {
                        val modes = ThemeMode.entries
                        container.themeManager.setThemeMode(modes[(themeMode.ordinal + 1) % modes.size])
                    },
                )
                NavRow(
                    icon = Icons.Filled.Palette,
                    title = "Accent color",
                    value = "#${accentColor.toHexRgb()}",
                    onClick = { showColorPicker = true },
                )
            }
        }

        item { GroupLabel("Account") }
        item {
            SettingsCard {
                NavRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "Sign out",
                    value = "",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = {
                        player.stopAndClearQueue()
                        container.repository.signOut()
                        onSignedOut()
                    },
                )
            }
        }

        item { GroupLabel("About") }
        item {
            SettingsCard {
                NavRow(icon = Icons.Filled.Info, title = "SamSonic", value = "v0.2.0 • Navidrome/Subsonic", onClick = {})
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showColorPicker) {
        AccentColorPickerDialog(
            initialColor = accentColor,
            defaultColor = ThemeManager.DefaultAccent,
            onDismiss = { showColorPicker = false },
            onConfirm = {
                container.themeManager.setAccentColor(it)
                showColorPicker = false
            },
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    // No hazeState: nested inside the NavHost's own hazeSource subtree (see
    // MediaLists.SongRow comment) - falls back to a flat translucent fill.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .glassSurface(
                shape = RoundedCornerShape(OneUiRadius.Card),
                hazeState = null,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = GlassAlpha.Sheet,
            ),
    ) {
        content()
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = tint)
        }
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
