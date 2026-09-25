package com.example.samsonic.locale

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.example.samsonic.R
import java.util.Locale

/** The languages the app can be set to, by [tag]; [SYSTEM] follows the phone's. */
enum class AppLanguage(val tag: String?, @StringRes val label: Int) {
    SYSTEM(null, R.string.language_system),
    ENGLISH("en", R.string.language_english),
    TRADITIONAL_CHINESE("zh-TW", R.string.language_traditional_chinese),
}

/**
 * The app's own language, set in Settings. From Android 13 it's Android's per-app language,
 * the same setting as in the system's app info, and Android restarts the screens itself. On
 * Android 12 there is no such setting, so the choice is saved here and applied to each
 * context as it's made ([wrap]); the screen restarts at once, the rest of the app (the
 * notification, Song info's output lines) on its next launch.
 */
object AppLanguages {
    private const val PREFS = "samsonic_language"
    private const val KEY = "language"

    fun current(context: Context): AppLanguage {
        val tags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            prefs(context).getString(KEY, null)
        }
        return fromTags(tags)
    }

    fun set(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
            return
        }
        prefs(activity).edit().putString(KEY, language.tag).apply()
        activity.recreate()
    }

    /** [base] in the saved language, for attachBaseContext before Android 13 (and [base] itself from 13). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = prefs(base).getString(KEY, null) ?: return base
        val locales = LocaleList.forLanguageTags(tag)
        Locale.setDefault(locales[0])
        val config = Configuration(base.resources.configuration).apply { setLocales(locales) }
        return base.createConfigurationContext(config)
    }

    /** The language set by [tags] ("zh-TW", "en-US"), or [AppLanguage.SYSTEM] for none. */
    private fun fromTags(tags: String?): AppLanguage {
        val locale = tags?.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it.substringBefore(',')) }
            ?: return AppLanguage.SYSTEM
        return when {
            locale.language == "zh" -> AppLanguage.TRADITIONAL_CHINESE
            locale.language == "en" -> AppLanguage.ENGLISH
            else -> AppLanguage.SYSTEM
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
