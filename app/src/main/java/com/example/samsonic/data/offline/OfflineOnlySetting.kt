package com.example.samsonic.data.offline

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the app shows only the songs kept for offline (Settings), remembered across launches. */
class OfflineOnlySetting(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("samsonic_offline", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun set(on: Boolean) {
        _enabled.value = on
        prefs.edit { putBoolean(KEY, on) }
    }

    private companion object {
        const val KEY = "offline_only"
    }
}
