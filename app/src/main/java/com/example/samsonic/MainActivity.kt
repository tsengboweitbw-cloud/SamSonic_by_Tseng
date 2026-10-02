package com.example.samsonic

import com.example.samsonic.ui.components.LocalRowPrefs
import com.example.samsonic.ui.components.RowPrefs
import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.data.ThemeMode
import com.example.samsonic.locale.AppLanguages
import com.example.samsonic.locale.LocalLanguageFade
import com.example.samsonic.locale.rememberLanguageFade
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.common.LocalWindowLayout
import com.example.samsonic.ui.common.currentWindowLayout
import com.example.samsonic.ui.common.gridFormFor
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.navigation.LastTab
import com.example.samsonic.ui.navigation.SamSonicNavHost
import com.example.samsonic.ui.theme.GlassSettings
import com.example.samsonic.ui.theme.LocalGlassSettings
import com.example.samsonic.ui.theme.SamSonicTheme

class MainActivity : ComponentActivity() {
    // The language picked in Settings, before Android 13 (from 13 Android applies it itself).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguages.wrap(newBase))
    }

    // The phone's languages as this activity started, to tell a language change apart
    // from the window changing size (below).
    private var systemLocales: LocaleList? = null

    // Language and window size changes (rotating, split screen, folding) don't restart
    // this activity (the manifest's configChanges): the screens redraw in the new language,
    // or relay out for the new size, keeping what's open. Before Android 13 the app's own
    // language is applied only as the activity starts, so the phone's language changing
    // would override it: restart then, as Android would have, but only for that.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            Resources.getSystem().configuration.locales != systemLocales
        ) {
            recreate()
        }
    }

    // A keyboard's shortcuts (a tablet's keyboard cover, DeX): Space plays or pauses,
    // Ctrl+Left and Ctrl+Right go to the previous and next song, Esc goes back. Space and
    // Esc only when the screens leave them (a text field types the space, a focused button
    // is pressed by it). Ctrl+arrows ahead of the screens, as their focus handling takes
    // arrow keys (and with them Ctrl+arrows) for moving focus, whether or not it moves;
    // but not while typing, where they move by word.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val arrow = event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        if (arrow) {
            if (!typing() && shortcut(event)) return true
            return super.dispatchKeyEvent(event)
        }
        return super.dispatchKeyEvent(event) || shortcut(event)
    }

    // Whether a text field has the keyboard (Search's): its keys are its own.
    private fun typing(): Boolean {
        val imm = getSystemService(InputMethodManager::class.java)
        return imm?.isAcceptingText == true && currentFocus != null
    }

    // The app's shortcuts: true if [event] is one (its press and its release both, so a
    // screen never sees half of it), acting on the press.
    private fun shortcut(event: KeyEvent): Boolean {
        val player = (application as SamSonicApplication).container.playerState
        val action: (() -> Unit)? = when {
            event.keyCode == KeyEvent.KEYCODE_SPACE && event.hasNoModifiers() ->
                player.takeIf { it.currentSong != null }?.let { { it.togglePlayPause() } }
            event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.isCtrlPressed ->
                player.takeIf { it.currentSong != null }?.let { { it.skipPrevious() } }
            event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.isCtrlPressed ->
                player.takeIf { it.currentSong != null }?.let { { it.skipNext() } }
            event.keyCode == KeyEvent.KEYCODE_ESCAPE && event.hasNoModifiers() ->
            { { onBackPressedDispatcher.onBackPressed() } }
            else -> null
        }
        if (action == null) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) action()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        systemLocales = Resources.getSystem().configuration.locales
        enableEdgeToEdge()
        val container = (application as SamSonicApplication).container
        // The grids' column counts for this kind of screen from the first frame (then
        // following the window below), not a phone's, switched a frame later.
        val startSize = resources.configuration
        container.libraryLayoutManager.setGridForm(
            gridFormFor(startSize.screenWidthDp.dp, startSize.screenHeightDp.dp),
            startSize.screenWidthDp.toFloat(),
            startSize.screenHeightDp.toFloat(),
        )
        setContent {
            val themeMode by container.themeManager.themeMode.collectAsStateWithLifecycle()
            val accentColor by container.themeManager.accentColor.collectAsStateWithLifecycle()
            val glassOpacity by container.themeManager.glassOpacity.collectAsStateWithLifecycle()
            val glassBlur by container.themeManager.glassBlur.collectAsStateWithLifecycle()
            val backdropBlur by container.themeManager.backdropBlur.collectAsStateWithLifecycle()
            val panelOpacity by container.themeManager.panelOpacity.collectAsStateWithLifecycle()
            val panelBlur by container.themeManager.panelBlur.collectAsStateWithLifecycle()
            val playerGlassOpacity by container.themeManager.playerGlassOpacity.collectAsStateWithLifecycle()
            val playerGlassBlur by container.themeManager.playerGlassBlur.collectAsStateWithLifecycle()
            val activeSource by container.sources.active.collectAsStateWithLifecycle()
            val artCornerRadius by container.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()
            val likesEnabled by container.themeManager.likesEnabled.collectAsStateWithLifecycle()
            val audioFormat by container.themeManager.audioFormatDisplay.collectAsStateWithLifecycle()
            val rowPrefs = remember(artCornerRadius, likesEnabled, audioFormat) { RowPrefs(artCornerRadius, likesEnabled, audioFormat) }
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

                val windowLayout = currentWindowLayout()
                val density = LocalDensity.current
                val gridForm = windowLayout.gridForm
                SideEffect { container.libraryLayoutManager.setGridForm(gridForm, windowLayout.width.value, windowLayout.height.value, windowLayout.twoPane) }
                val languageFade = rememberLanguageFade()
                // The tab to come back to when the screens start over for a new source.
                val lastTab = rememberSaveable(saver = LastTab.Saver) { LastTab() }
                CompositionLocalProvider(
                    LocalAppContainer provides container,
                    LocalPlayerState provides container.playerState,
                    LocalGlassSettings provides GlassSettings(glassOpacity, glassBlur, backdropBlur, panelOpacity, panelBlur, playerGlassOpacity, playerGlassBlur),
                    LocalLanguageFade provides languageFade,
                    LocalRowPrefs provides rowPrefs,
                    LocalWindowLayout provides windowLayout,
                    // Text a size up on a tablet and in DeX, read from further away than a
                    // phone or a foldable; on top of the size set on the phone itself.
                    LocalDensity provides Density(density.density, density.fontScale * windowLayout.textScale),
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        // Switching music sources starts the app's screens over, so no
                        // page, cache or back stack entry carries over the old library,
                        // but on the tab you switched from (Settings), not Home.
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = languageFade.alpha }) {
                            key(activeSource?.key) {
                                SamSonicNavHost(lastTab)
                            }
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