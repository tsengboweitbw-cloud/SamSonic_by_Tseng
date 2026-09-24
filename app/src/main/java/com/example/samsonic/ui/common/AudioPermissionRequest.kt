package com.example.samsonic.ui.common

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.example.samsonic.data.device.audioPermission
import com.example.samsonic.data.device.hasAudioPermission

/**
 * Returns an action that runs [onGranted] once the app may read the phone's music,
 * asking for the permission first if needed; [onDenied] runs if the user says no.
 * After the user has turned it down for good, the system won't ask again, so the
 * action opens the app's page in system settings instead, where it can be allowed.
 */
@Composable
fun rememberAudioPermissionRequest(onGranted: () -> Unit, onDenied: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val granted by rememberUpdatedState(onGranted)
    val denied by rememberUpdatedState(onDenied)
    var refused by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) granted() else {
            refused = true
            denied()
        }
    }
    return {
        val refusedForGood = refused && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, audioPermission)
        when {
            context.hasAudioPermission() -> granted()
            refusedForGood -> context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            else -> launcher.launch(audioPermission)
        }
    }
}
