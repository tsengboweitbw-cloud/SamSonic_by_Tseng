package com.example.samsonic.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ServerCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
)

/**
 * Persists the server URL + credentials on-device via EncryptedSharedPreferences. Subsonic's
 * auth scheme needs the plaintext password on every request (to salt a fresh token), so it -
 * not just a session token - is what has to be stored; encrypting it at rest is the mitigation.
 */
class SessionManager(context: Context) {
    private val appContext = context.applicationContext

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "samsonic_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _credentials = MutableStateFlow(loadCredentials())
    val credentials: StateFlow<ServerCredentials?> = _credentials.asStateFlow()

    private fun loadCredentials(): ServerCredentials? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val user = prefs.getString(KEY_USER, null) ?: return null
        val pass = prefs.getString(KEY_PASS, null) ?: return null
        return ServerCredentials(url, user, pass)
    }

    fun save(credentials: ServerCredentials) {
        prefs.edit()
            .putString(KEY_URL, credentials.serverUrl)
            .putString(KEY_USER, credentials.username)
            .putString(KEY_PASS, credentials.password)
            .apply()
        _credentials.value = credentials
    }

    fun clear() {
        prefs.edit().clear().apply()
        _credentials.value = null
    }

    companion object {
        private const val KEY_URL = "server_url"
        private const val KEY_USER = "username"
        private const val KEY_PASS = "password"
    }
}
