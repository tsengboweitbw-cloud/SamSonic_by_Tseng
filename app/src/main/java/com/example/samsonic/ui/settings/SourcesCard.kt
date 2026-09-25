package com.example.samsonic.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.data.ActiveSource
import com.example.samsonic.data.SavedServer
import com.example.samsonic.data.device.hasAudioPermission
import com.example.samsonic.ui.common.rememberAudioPermissionRequest
import com.example.samsonic.ui.components.PressIconButton
import com.example.samsonic.ui.theme.oneUiRowClickable

/**
 * The music sources to pick from: the phone's own music and every saved Subsonic
 * server, the one in use ticked. Tapping another switches the whole app to it.
 * A server's bin asks for a second tap before the server is forgotten.
 */
@Composable
internal fun SourcesCard(onAddServer: () -> Unit) {
    val sources = LocalAppContainer.current.sources
    val servers by sources.servers.collectAsStateWithLifecycle()
    val active by sources.active.collectAsStateWithLifecycle()
    var confirmingRemove by remember { mutableStateOf<String?>(null) }

    // Re-checked on return from system settings, where the permission may have been allowed.
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.hasAudioPermission()) }
    LifecycleResumeEffect(Unit) {
        hasPermission = context.hasAudioPermission()
        onPauseOrDispose { }
    }
    val useDevice = rememberAudioPermissionRequest(
        onGranted = {
            hasPermission = true
            sources.useDevice()
        },
    )

    SettingsCard {
        SourceRow(
            icon = Icons.Filled.PhoneAndroid,
            title = stringResource(R.string.settings_source_device),
            hint = if (hasPermission) stringResource(R.string.settings_source_device_hint) else stringResource(R.string.settings_source_device_hint_permission),
            selected = active is ActiveSource.Device,
            onClick = {
                confirmingRemove = null
                useDevice()
            },
        )
        servers.forEach { server ->
            val confirming = confirmingRemove == server.id
            SourceRow(
                icon = Icons.Filled.Dns,
                title = server.displayAddress,
                hint = if (confirming) stringResource(R.string.settings_source_confirm_remove_hint) else stringResource(R.string.settings_source_signed_in, server.username),
                hintAccent = if (confirming) MaterialTheme.colorScheme.error else null,
                // It says what the next tap does, so it can't wait for a long press.
                showHintNow = confirming,
                selected = (active as? ActiveSource.Server)?.server?.id == server.id,
                onClick = {
                    confirmingRemove = null
                    sources.useServer(server.id)
                },
                trailing = {
                    PressIconButton(
                        onClick = {
                            if (confirming) sources.removeServer(server.id) else confirmingRemove = server.id
                        },
                        size = 40.dp,
                    ) {
                        Icon(
                            imageVector = if (confirming) Icons.Filled.DeleteForever else Icons.Outlined.Delete,
                            contentDescription = if (confirming) stringResource(R.string.settings_source_confirm_remove) else stringResource(R.string.settings_source_remove),
                            tint = if (confirming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        }
        SourceRow(
            icon = Icons.Filled.Add,
            title = stringResource(R.string.settings_source_add),
            hint = stringResource(R.string.settings_source_add_hint),
            selected = false,
            onClick = {
                confirmingRemove = null
                onAddServer()
            },
        )
    }
}

/** The server's address without the scheme and trailing slash, e.g. "192.168.1.10:4533". */
private val SavedServer.displayAddress: String
    get() = serverUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')

@Composable
private fun SourceRow(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    // Shown on a long press (see RowHint), or at once while [showHintNow].
    hint: String? = null,
    // The bubble's tint; null for the app's accent.
    hintAccent: Color? = null,
    showHintNow: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    val hintState = rememberRowHint()
    LaunchedEffect(showHintNow) { if (showHintNow) hintState.show() else hintState.hide() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hintHold(hintState)
            .oneUiRowClickable(onClick, onLongClick = hintLongPress(hintState, hint))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = rowIconTint(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.settings_source_in_use), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        // Takes no height, so a server row's bin button (taller than the text)
        // leaves the row as tall as every other settings row; it hangs over the
        // row's padding instead.
        Box(
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                layout(placeable.width, 0) { placeable.place(0, -placeable.height / 2) }
            },
        ) { trailing() }
        RowHint(hintState, hint, hintAccent)
    }
}
