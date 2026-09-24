package com.example.samsonic.data

import com.example.samsonic.data.SessionManager.Companion.DEVICE_SOURCE_ID
import com.example.samsonic.data.device.DeviceLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The music source in use. */
sealed interface ActiveSource {
    /** Stable across launches; the app rebuilds its screens whenever it changes. */
    val key: String

    data object Device : ActiveSource {
        override val key = DEVICE_SOURCE_ID
    }

    data class Server(val server: SavedServer) : ActiveSource {
        override val key get() = server.id
    }
}

/**
 * Which music library the app shows: one of the saved Subsonic servers, or the
 * music on this phone. The choice is remembered across launches.
 */
class MusicSources(
    private val sessionManager: SessionManager,
    private val subsonic: SubsonicRepository,
    private val device: DeviceLibrary,
) {
    val servers: StateFlow<List<SavedServer>> = sessionManager.servers

    private val _active = MutableStateFlow(resolve(sessionManager.activeSourceId.value))
    /** Null until the user signs in to a server or picks the music on this phone. */
    val active: StateFlow<ActiveSource?> = _active.asStateFlow()

    init {
        subsonic.configure((_active.value as? ActiveSource.Server)?.server?.credentials)
    }

    /** The library of the [active] source. */
    val library: MusicLibrary
        get() = if (_active.value is ActiveSource.Device) device else subsonic

    private fun resolve(id: String?): ActiveSource? = when (id) {
        null -> null
        DEVICE_SOURCE_ID -> ActiveSource.Device
        else -> servers.value.firstOrNull { it.id == id }?.let(ActiveSource::Server)
    }

    /** Signs in to a server, saves it and switches to it. The source in use is untouched if that fails. */
    suspend fun addServer(serverUrl: String, username: String, password: String): Result<Unit> =
        subsonic.ping(serverUrl, username, password).map { creds ->
            activate(ActiveSource.Server(sessionManager.addServer(creds)))
        }

    fun useServer(id: String) {
        servers.value.firstOrNull { it.id == id }?.let { activate(ActiveSource.Server(it)) }
    }

    fun useDevice() = activate(ActiveSource.Device)

    /** Forgets a saved server; if it was in use, no source is left chosen (back to sign in). */
    fun removeServer(id: String) {
        sessionManager.removeServer(id)
        if ((_active.value as? ActiveSource.Server)?.server?.id == id) activate(null)
    }

    private fun activate(source: ActiveSource?) {
        subsonic.configure((source as? ActiveSource.Server)?.server?.credentials)
        sessionManager.setActiveSource(source?.key)
        _active.value = source
    }
}
