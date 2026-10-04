package com.example.samsonic.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.data.scrobble.PendingScrobble
import com.example.samsonic.data.scrobble.ScrobbleQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Share of a song that has to be listened to before it counts as played. */
private const val SubmitFraction = 0.4
private const val TickMillis = 1_000L

/**
 * Reports what [player] plays to the server: "now playing" (submission=false) as soon as
 * a song starts playing, then a play (submission=true) once 40% of it has been listened
 * to. Time is counted while it actually plays, so seeking past 40% doesn't count as a
 * listen. A song played again (repeat, or picked again) is reported again.
 *
 * Runs on the player's (main) thread. A "now playing" that fails is dropped; a finished listen
 * that fails (the server out of reach) waits in [queue], and goes out with the time it happened
 * as soon as a later scrobble gets through, or when this starts.
 */
class Scrobbler(
    private val player: Player,
    // The active source's library, read at each use: it changes when the user switches sources.
    private val repository: () -> MusicLibrary,
    private val scope: CoroutineScope,
    // The clock the listening time is counted by; a test gives its own.
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    // Where a listen that couldn't be sent waits, for the server [serverKey] says is in use (none: nowhere).
    private val queue: ScrobbleQueue? = null,
    private val serverKey: () -> String? = { null },
    private val wallClock: () -> Long = System::currentTimeMillis,
) : Player.Listener {
    private var songId: String? = null
    private var nowPlayingSent = false
    private var submitted = false
    private var listenedMs = 0L
    private var ticker: Job? = null

    init {
        player.addListener(this)
        startSong(player.currentMediaItem)
        flushWaiting()
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
            var last = clock()
            while (isActive) {
                delay(TickMillis)
                val now = clock()
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
        val server = serverKey()
        val heardAt = wallClock()
        scope.launch {
            try {
                library.scrobble(id, submission, timeMs = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A listen counts later; a "now playing" does not.
                if (submission && server != null) queue?.add(PendingScrobble(server, id, heardAt))
                return@launch
            }
            // The server answered: what's waiting can go now.
            if (server != null) runCatching { queue?.flush(library, server) }
        }
    }

    /** Sends the listens left waiting by an earlier run, to the server in use. */
    private fun flushWaiting() {
        val library = repository()
        val server = serverKey() ?: return
        scope.launch { runCatching { queue?.flush(library, server) } }
    }

    fun release() {
        ticker?.cancel()
        player.removeListener(this)
    }
}
