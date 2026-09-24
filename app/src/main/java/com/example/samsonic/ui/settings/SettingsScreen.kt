package com.example.samsonic.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.data.ThemeManager
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.theme.oneUiRowClickable
import com.example.samsonic.ui.theme.toHexRgb
import com.example.samsonic.ui.common.TitledPage
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val player = LocalPlayerState.current
    val container = LocalAppContainer.current
    val credentials by container.sessionManager.credentials.collectAsStateWithLifecycle()
    val themeMode by container.themeManager.themeMode.collectAsStateWithLifecycle()
    val accentColor by container.themeManager.accentColor.collectAsStateWithLifecycle()
    val albumArtCornerRadius by container.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()
    val likesEnabled by container.themeManager.likesEnabled.collectAsStateWithLifecycle()
    val albumArtistsOnly by container.libraryLayoutManager.albumArtistsOnly.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val themeMenu = remember { PanelState(scope) }
    val accentMenu = remember { PanelState(scope) }

    // A nav bar tab like Search and Library, so the same fixed large title and no back button.
    TitledPage(
        modifier = modifier,
        title = {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        },
        overlay = { haze ->
            ThemeMenu(themeMenu, haze, current = themeMode, onSelect = container.themeManager::setThemeMode)
            AccentColorMenu(
                accentMenu,
                haze,
                initialColor = accentColor,
                defaultColor = ThemeManager.DefaultAccent,
                onConfirm = container.themeManager::setAccentColor,
            )
        },
    ) { topPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPadding, bottom = 24.dp + contentPaddingBottom),
        ) {
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
                    SleepTimerRow(player)
                    SwitchRow(
                        icon = Icons.Filled.Favorite,
                        title = "Like button",
                        checked = likesEnabled,
                        onCheckedChange = container.themeManager::setLikesEnabled,
                    )
                }
            }

            item { GroupLabel("Library") }
            item {
                SettingsCard {
                    // The Artists tab reloads with the other list the next time it shows.
                    SwitchRow(
                        icon = Icons.Filled.Person,
                        title = "Album artists only",
                        checked = albumArtistsOnly,
                        onCheckedChange = container.libraryLayoutManager::setAlbumArtistsOnly,
                    )
                }
            }

            item { GroupLabel("Appearance") }
            item {
                SettingsCard {
                    NavRow(
                        icon = Icons.Filled.DarkMode,
                        title = "Theme",
                        value = themeMode.label,
                        onClick = { themeMenu.open() },
                        modifier = Modifier.menuOrigin(themeMenu),
                    )
                    NavRow(
                        icon = Icons.Filled.Palette,
                        title = "Accent color",
                        value = "#${accentColor.toHexRgb()}",
                        onClick = { accentMenu.open() },
                        modifier = Modifier.menuOrigin(accentMenu),
                    )
                    SliderRow(
                        icon = Icons.Filled.RoundedCorner,
                        title = "Album art roundness",
                        valueLabel = "${albumArtCornerRadius.value.roundToInt()}dp",
                        value = albumArtCornerRadius.value,
                        valueRange = 0f..48f,
                        onValueChange = { container.themeManager.setAlbumArtCornerRadius(it.dp) },
                    )
                    GlassSliderRows(container.themeManager)
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
private fun NavRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .oneUiRowClickable(onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
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
