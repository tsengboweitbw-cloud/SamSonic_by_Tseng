package com.example.samsonic.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.samsonic.R
import com.example.samsonic.locale.AppLanguage
import com.example.samsonic.locale.AppLanguages
import com.example.samsonic.locale.LocalLanguageFade
import com.example.samsonic.ui.player.PanelState
import dev.chrisbanes.haze.HazeState

private val AppLanguage.icon: ImageVector
    get() = when (this) {
        AppLanguage.SYSTEM -> Icons.Filled.PhoneAndroid
        else -> Icons.Filled.Translate
    }

/**
 * The Language row. The language is read again whenever the configuration changes, which a
 * new language does (see LanguageFade), so it always names the one showing.
 */
@Composable
internal fun LanguageRow(menu: PanelState) {
    val context = LocalContext.current
    val current = remember(LocalConfiguration.current) { AppLanguages.current(context) }
    NavRow(
        icon = Icons.Filled.Language,
        title = stringResource(R.string.language_title),
        value = stringResource(current.label),
        hint = stringResource(R.string.language_hint),
        onClick = { menu.open() },
        modifier = Modifier.menuOrigin(menu),
    )
}

/** The Language row's secondary menu ([SettingsMenu]): the phone's language, or one of the app's. */
@Composable
internal fun LanguageMenu(panel: PanelState, haze: HazeState) {
    val activity = LocalActivity.current ?: return
    val current = remember(LocalConfiguration.current) { AppLanguages.current(activity) }
    val fade = LocalLanguageFade.current
    SettingsMenu(panel, haze, title = stringResource(R.string.language_title)) {
        AppLanguage.entries.forEach { language ->
            MenuOption(icon = language.icon, label = stringResource(language.label), selected = language == current, onClick = {
                panel.close()
                if (language == current) return@MenuOption
                fade?.switchTo(activity, language) ?: AppLanguages.set(activity, language)
            })
        }
    }
}
