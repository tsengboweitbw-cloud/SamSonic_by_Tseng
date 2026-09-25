package com.example.samsonic

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.data.ThemeMode
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.navigation.SamSonicNavHost
import com.example.samsonic.ui.theme.GlassSettings
import com.example.samsonic.ui.theme.LocalGlassSettings
import com.example.samsonic.ui.theme.SamSonicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SamSonicApplication).container
        setContent {
            val themeMode by container.themeManager.themeMode.collectAsStateWithLifecycle()
            val accentColor by container.themeManager.accentColor.collectAsStateWithLifecycle()
            val glassOpacity by container.themeManager.glassOpacity.collectAsStateWithLifecycle()
            val glassBlur by container.themeManager.glassBlur.collectAsStateWithLifecycle()
            val backdropBlur by container.themeManager.backdropBlur.collectAsStateWithLifecycle()
            val panelOpacity by container.themeManager.panelOpacity.collectAsStateWithLifecycle()
            val panelBlur by container.themeManager.panelBlur.collectAsStateWithLifecycle()
            val activeSource by container.sources.active.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // The status and navigation bar icons follow the app's theme, not the
            // system's: with Theme set against the system (dark app, light phone or
            // the reverse), the system's choice left them unreadable on the app.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(NavBarLightScrim, NavBarDarkScrim) { darkTheme },
                )
                onDispose {}
            }
            SamSonicTheme(darkTheme = darkTheme, accentColor = accentColor) {
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
                    LocalGlassSettings provides GlassSettings(glassOpacity, glassBlur, backdropBlur, panelOpacity, panelBlur),
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        // Switching music sources starts the app's screens over, so no
                        // page, cache or back stack entry carries over the old library.
                        key(activeSource?.key) {
                            SamSonicNavHost()
                        }
                    }
                }
            }
        }
    }
}

// The scrims enableEdgeToEdge puts behind three-button navigation by default, kept so
// its buttons stay as legible as before.
private val NavBarLightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NavBarDarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
