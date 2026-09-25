package com.example.samsonic.locale

import android.app.Activity
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Changing the app's language without a jolt: the screens fade out, the language changes
 * while they're hidden, and they fade back in with the new text.
 *
 * From Android 13 the activity handles the change itself (configChanges in the manifest),
 * so nothing restarts: the text is redrawn in place, and every screen keeps its place. On
 * Android 12 the screen has to restart to take the language; the new one fades in.
 */
@Stable
class LanguageFade internal constructor(private val scope: CoroutineScope, startHidden: Boolean) {
    private val animatable = Animatable(if (startHidden) 0f else 1f)

    /** How much of the app's content shows, for its graphicsLayer. */
    val alpha: Float get() = animatable.value

    /** The screen's languages, kept current by [rememberLanguageFade]. */
    internal var locales: LocaleList by mutableStateOf(LocaleList.getEmptyLocaleList())

    private var job: Job? = null

    fun switchTo(activity: Activity, language: AppLanguage) {
        job?.cancel()
        // The same text either way (the phone's language picked while it's showing): no fade.
        val target = language.tag?.let { LocaleList.forLanguageTags(it) } ?: Resources.getSystem().configuration.locales
        if (!locales.isEmpty && shownAs(target) == shownAs(locales)) {
            AppLanguages.set(activity, language)
            return
        }
        job = scope.launch {
            animatable.animateTo(0f, tween(FADE_OUT_MS))
            val before = locales
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) revealOnRestart = true
            AppLanguages.set(activity, language)
            // Android hands over the new configuration a moment later; should none come
            // (the text didn't change after all), don't stay hidden long.
            withTimeoutOrNull(WAIT_MS) { snapshotFlow { locales }.first { it != before } }
            // One frame for the new text to be laid out, still hidden.
            withFrameNanos {}
            animatable.animateTo(1f, tween(FADE_IN_MS))
        }
    }

    internal suspend fun reveal() = animatable.animateTo(1f, tween(FADE_IN_MS))

    internal companion object {
        const val FADE_OUT_MS = 160
        const val FADE_IN_MS = 280
        const val WAIT_MS = 600L

        /** The language the app's text comes out in for [locales]: Chinese, else English. */
        fun shownAs(locales: LocaleList): String = if (!locales.isEmpty && locales[0].language == "zh") "zh" else "en"

        /** Before Android 13: the next screen starts hidden and fades in. */
        @Volatile var revealOnRestart = false
    }
}

val LocalLanguageFade = staticCompositionLocalOf<LanguageFade?> { null }

@Composable
fun rememberLanguageFade(): LanguageFade {
    val scope = rememberCoroutineScope()
    val fade = remember { LanguageFade(scope, startHidden = LanguageFade.revealOnRestart) }
    val locales = LocalConfiguration.current.locales
    SideEffect { fade.locales = locales }
    LaunchedEffect(Unit) {
        if (LanguageFade.revealOnRestart) {
            LanguageFade.revealOnRestart = false
            fade.reveal()
        }
    }
    return fade
}
