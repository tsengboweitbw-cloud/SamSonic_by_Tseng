package com.example.samsonic.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.samsonic.data.MusicLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Share of a song that has to be listened to before it counts as played. */
private const val SubmitFraction = 0.1
private const val TickMillis = 1_000L

/**
 * Reports what [player] plays to the server: "now playing" (submission=false) as soon as
 * a song starts playing, then a play (submission=true) once 10% of it has been listened
 * to. Time is counted while it actually plays, so seeking past 10% doesn't count as a
 * listen. A song played again (repeat, or picked again) is reported again.
 *
 * Runs on the player's (main) thread. Scrobbles are best-effort: a failed one is dropped.
 */
class Scrobbler(
    private val player: Player,
    // The active source's library, read at each use: it changes when the user switches sources.
    private val repository: () -> MusicLibrary,
    private val scope: CoroutineScope,
) : Player.Listener {
    private var songId: String? = null
    private var nowPlayingSent = false
    private var submitted = false
    private var listenedMs = 0L
    private var ticker: Job? = null

    init {
        player.addListener(this)
        startSong(player.currentMediaItem)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = startSong(mediaItem)

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            sendNowPlaying()
            startTicker()
        } else {
            ticker?.cancel()
        }
    }

    private fun startSong(mediaItem: MediaItem?) {
        songId = mediaItem?.mediaId?.takeIf { it.isNotEmpty() }
        nowPlayingSent = false
        submitted = false
        listenedMs = 0L
        if (player.isPlaying) {
            sendNowPlaying()
            startTicker()
        }
    }

    private fun sendNowPlaying() {
        if (nowPlayingSent) return
        val id = songId ?: return
        nowPlayingSent = true
        send(id, submission = false)
    }

    /** Adds up listening time while playing, submitting the song once it passes [SubmitFraction]. */
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(TickMillis)
                val now = SystemClock.elapsedRealtime()
                listenedMs += ((now - last) * player.playbackParameters.speed).toLong()
                last = now
                maybeSubmit()
            }
        }
    }

    private fun maybeSubmit() {
        if (submitted) return
        val id = songId ?: return
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        if (listenedMs < duration * SubmitFraction) return
        submitted = true
        send(id, submission = true)
    }

    private fun send(id: String, submission: Boolean) {
        val library = repository()
        scope.launch { runCatching { library.scrobble(id, submission) } }
    }

    fun release() {
        ticker?.cancel()
        player.removeListener(this)
    }
}
