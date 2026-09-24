package com.example.samsonic.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

data class ServerCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
)

/** A Subsonic server the user has signed in to, kept so they can switch back to it. */
@Serializable
data class SavedServer(
    val id: String,
    val serverUrl: String,
    val username: String,
    val password: String,
) {
    val credentials: ServerCredentials get() = ServerCredentials(serverUrl, username, password)
}

/**
 * Persists the saved servers (URL + credentials) and which music source is in use,
 * on-device via EncryptedSharedPreferences. Subsonic's auth scheme needs the plaintext
 * password on every request (to salt a fresh token), so it - not just a session token -
 * is what has to be stored; encrypting it at rest is the mitigation.
 */
class SessionManager(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

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

    init {
        migrateSingleServer()
    }

    private val _servers = MutableStateFlow(loadServers())
    val servers: StateFlow<List<SavedServer>> = _servers.asStateFlow()

    /** [DEVICE_SOURCE_ID], a [SavedServer.id], or null when no source is chosen yet. */
    private val _activeSourceId = MutableStateFlow(prefs.getString(KEY_ACTIVE, null))
    val activeSourceId: StateFlow<String?> = _activeSourceId.asStateFlow()

    private fun loadServers(): List<SavedServer> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SavedServer>>(raw) }.getOrDefault(emptyList())
    }

    /** Earlier versions kept a single server under their own keys; it becomes the first saved server. */
    private fun migrateSingleServer() {
        val url = prefs.getString(KEY_URL, null) ?: return
        val user = prefs.getString(KEY_USER, null)
        val pass = prefs.getString(KEY_PASS, null)
        val editor = prefs.edit().remove(KEY_URL).remove(KEY_USER).remove(KEY_PASS)
        if (user != null && pass != null) {
            val server = SavedServer(UUID.randomUUID().toString(), url, user, pass)
            editor.putString(KEY_SERVERS, json.encodeToString(listOf(server))).putString(KEY_ACTIVE, server.id)
        }
        editor.apply()
    }

    /**
     * Saves [credentials] and returns the saved server. Signing in to a server
     * again with the same user updates that entry instead of adding another.
     */
    fun addServer(credentials: ServerCredentials): SavedServer {
        val existing = _servers.value.firstOrNull {
            it.serverUrl == credentials.serverUrl && it.username == credentials.username
        }
        val server = SavedServer(
            id = existing?.id ?: UUID.randomUUID().toString(),
            serverUrl = credentials.serverUrl,
            username = credentials.username,
            password = credentials.password,
        )
        val servers = if (existing != null) {
            _servers.value.map { if (it.id == server.id) server else it }
        } else {
            _servers.value + server
        }
        saveServers(servers)
        return server
    }

    fun removeServer(id: String) {
        saveServers(_servers.value.filterNot { it.id == id })
    }

    fun setActiveSource(id: String?) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
        _activeSourceId.value = id
    }

    private fun saveServers(servers: List<SavedServer>) {
        prefs.edit().putString(KEY_SERVERS, json.encodeToString(servers)).apply()
        _servers.value = servers
    }

    companion object {
        /** [activeSourceId] when the music on this phone is in use. */
        const val DEVICE_SOURCE_ID = "device"

        private const val KEY_SERVERS = "servers"
        private const val KEY_ACTIVE = "active_source"

        // The single-server keys of earlier versions, read once by migrateSingleServer.
        private const val KEY_URL = "server_url"
        private const val KEY_USER = "username"
        private const val KEY_PASS = "password"
    }
}
