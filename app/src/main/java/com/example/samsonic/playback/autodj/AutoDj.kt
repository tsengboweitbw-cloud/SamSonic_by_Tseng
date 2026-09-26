package com.example.samsonic.playback.autodj

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.samsonic.data.AutoDjMode
import com.example.samsonic.data.AutoDjSettings
import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.model.Song
import com.example.samsonic.playback.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// How many songs and albums lately played or queued Auto DJ keeps from picking again.
private const val RECENT_SONGS = 500
private const val RECENT_ALBUMS = 40

/**
 * Keeps the music going when the queue runs out: as its last song starts (in play
 * order, so with shuffle on too), adds what [pickAutoDj] picks after it, for as long
 * as [settings] has a mode on. Not with repeat on, where the queue plays again anyway.
 * Watches [PlayerState.changes] rather than Compose state, so it works with the screen off.
 */
class AutoDj(
    private val player: PlayerState,
    private val library: () -> MusicLibrary,
    private val settings: AutoDjSettings,
    private val scope: CoroutineScope,
) {
    /** Whether a pick is being fetched, for Up next to show. */
    var isPicking by mutableStateOf(false)
        private set

    private val recentSongs = LinkedHashSet<String>()
    private val recentAlbums = LinkedHashSet<String>()
    // The stretch at the queue's end a pick was last made for, so each gets one pick.
    private var pickedFor: String? = null

    fun start() {
        scope.launch {
            // Remember what plays, so it isn't picked again soon.
            player.changes.collect {
                val song = player.currentSong ?: return@collect
                recentSongs.remember(song.id, RECENT_SONGS)
                song.albumId?.let { recentAlbums.remember(it, RECENT_ALBUMS) }
            }
        }
        scope.launch {
            combine(player.changes, settings.config) { _, config ->
                player.lastToPlay()?.takeIf { config.mode != AutoDjMode.OFF }?.let { it to player.queue.size }
            }
                .distinctUntilChanged()
                .collect { at ->
                    val (seed, queueSize) = at ?: return@collect
                    // Once per stretch at the queue's end, even if picking finds nothing.
                    val key = "${seed.id}@$queueSize"
                    if (key == pickedFor) return@collect
                    pickedFor = key
                    pickAfter(seed)
                }
        }
    }

    private fun pickAfter(seed: Song) {
        scope.launch {
            isPicking = true
            try {
                val exclude = recentSongs + player.queue.map { it.id }
                val excludeAlbums = recentAlbums + player.queue.mapNotNull { it.albumId }
                val picked = pickAutoDj(library(), settings.config.value, seed, exclude, excludeAlbums)
                // Still wanted: the mode is still on and nothing was queued after the seed meanwhile.
                if (picked.isNotEmpty() && settings.config.value.mode != AutoDjMode.OFF && player.lastToPlay()?.id == seed.id) {
                    picked.forEach { song -> song.albumId?.let { recentAlbums.remember(it, RECENT_ALBUMS) } }
                    player.continueWith(picked)
                }
            } finally {
                isPicking = false
            }
        }
    }

    /** Forgets what played before, as when the music source changes. */
    fun reset() {
        recentSongs.clear()
        recentAlbums.clear()
        pickedFor = null
    }
}

/** Adds [id] as the latest, dropping the oldest past [limit]. */
private fun LinkedHashSet<String>.remember(id: String, limit: Int) {
    remove(id)
    add(id)
    if (size > limit) remove(first())
}
