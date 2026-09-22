package com.example.samsonic

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.navigation.SamSonicNavHost
import com.example.samsonic.ui.theme.SamSonicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SamSonicApplication).container
        setContent {
            SamSonicTheme {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val requestNotifications = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { }
                    LaunchedEffect(Unit) {
                        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                CompositionLocalProvider(
                    LocalAppContainer provides container,
                    LocalPlayerState provides container.playerState,
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        SamSonicNavHost()
                    }
                }
            }
        }
    }
}
