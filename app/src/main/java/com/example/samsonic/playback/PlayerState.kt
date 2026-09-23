package com.example.samsonic.playback

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.samsonic.data.SubsonicRepository
import com.example.samsonic.model.Song
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class RepeatMode { OFF, ALL, ONE }

/**
 * UI-facing playback state, backed by a real Media3 [MediaController] bound to
 * [PlaybackService]. Owned by [com.example.samsonic.AppContainer] (application-scoped) so
 * playback and its notification/lock-screen controls survive Activity recreation and
 * backgrounding the app.
 */
class PlayerState(
    private val context: Context,
    private val repository: SubsonicRepository,
    private val scope: CoroutineScope,
) {
    private var controller: MediaController? = null
    private var songById: Map<String, Song> = emptyMap()
    private var sleepTimerJob: Job? = null

    var isReady by mutableStateOf(false)
        private set
    var currentSong by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionSeconds by mutableFloatStateOf(0f)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(RepeatMode.OFF)
        private set
    var sleepTimerMinutes by mutableStateOf<Int?>(null)
        private set
    var queue: SnapshotStateList<Song> = mutableListOf<Song>().toMutableStateList()
        private set
    var currentIndex by mutableStateOf(-1)
        private set
    /**
     * [queue] indices in the order they will actually play (the shuffle order
     * when shuffle is on), so the Now Playing cover carousel pages through
     * exactly what next/previous will play.
     */
    var playOrder by mutableStateOf<List<Int>>(emptyList())
        private set
    private var likedOverrides by mutableStateOf(mapOf<String, Boolean>())

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@PlayerState.isPlaying = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentIndex = controller?.currentMediaItemIndex ?: -1
            currentSong = mediaItem?.mediaId?.let { songById[it] }
            positionSeconds = 0f
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            shuffle = shuffleModeEnabled
            refreshPlayOrder()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            refreshPlayOrder()
        }

        override fun onRepeatModeChanged(mode: Int) {
            repeatMode = mode.toRepeatMode()
        }
    }

    init {
        scope.launch {
            while (true) {
                controller?.let { c -> if (c.isPlaying) positionSeconds = c.currentPosition / 1000f }
                delay(500)
            }
        }
    }

    fun connect() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(playerListener)
            isPlaying = c.isPlaying
            shuffle = c.shuffleModeEnabled
            repeatMode = c.repeatMode.toRepeatMode()
            refreshPlayOrder()
            isReady = true
        }, MoreExecutors.directExecutor())
    }

    fun play(song: Song, playbackContext: List<Song>) {
        val c = controller ?: return
        songById = songById + playbackContext.associateBy { it.id }
        queue.clear()
        queue.addAll(playbackContext)
        val startIndex = playbackContext.indexOf(song).coerceAtLeast(0)
        c.setMediaItems(playbackContext.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.play()
    }

    /** Appends [songs] to the end of the queue; with nothing queued yet, starts playing them. */
    fun addToQueue(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) return play(songs.first(), songs)
        songById = songById + songs.associateBy { it.id }
        queue.addAll(songs)
        c.addMediaItems(songs.map { it.toMediaItem() })
    }

    fun playQueueIndex(index: Int) {
        val c = controller ?: return
        if (index !in queue.indices) return
        c.seekTo(index, 0L)
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun skipNext() {
        controller?.seekToNext()
    }

    fun skipPrevious() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPrevious()
    }

    fun seekToFraction(fraction: Float) {
        val c = controller ?: return
        val song = currentSong ?: return
        val clamped = fraction.coerceIn(0f, 1f)
        c.seekTo((clamped * song.durationSeconds * 1000).toLong())
        // Update optimistically rather than waiting for the position-polling
        // loop's next tick (up to 500ms away) - otherwise the seek bar falls
        // back to the stale pre-seek position for a moment, then jumps again
        // once the poll catches up, visible as a snap-back-then-forward glitch.
        positionSeconds = clamped * song.durationSeconds
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (repeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_ALL
            RepeatMode.ALL -> Player.REPEAT_MODE_ONE
            RepeatMode.ONE -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerMinutes = minutes
        if (minutes != null) {
            sleepTimerJob = scope.launch {
                delay(minutes * 60_000L)
                controller?.pause()
                sleepTimerMinutes = null
            }
        }
    }

    /** Stops playback and clears the queue - used when signing out so the mini player doesn't
     *  keep showing a track from the account that was just signed out of. */
    fun stopAndClearQueue() {
        controller?.stop()
        controller?.clearMediaItems()
        queue.clear()
        currentIndex = -1
        currentSong = null
    }

    fun isLiked(song: Song): Boolean = likedOverrides[song.id] ?: song.liked

    fun toggleLike(song: Song) {
        val newValue = !isLiked(song)
        likedOverrides = likedOverrides + (song.id to newValue)
        scope.launch {
            runCatching { if (newValue) repository.star(song.id) else repository.unstar(song.id) }
        }
    }

    private fun Song.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artistName)
            .setAlbumTitle(albumTitle)
            .apply { repository.coverArtUrl(coverArt)?.let { setArtworkUri(it.toUri()) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(repository.streamUrl(id))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun refreshPlayOrder() {
        val c = controller ?: return
        val timeline = c.currentTimeline
        val shuffled = c.shuffleModeEnabled
        val order = ArrayList<Int>(timeline.windowCount)
        var index = if (timeline.isEmpty) C.INDEX_UNSET else timeline.getFirstWindowIndex(shuffled)
        while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffled)
        }
        playOrder = order
    }

    private fun Int.toRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        else -> RepeatMode.OFF
    }
}

val LocalPlayerState = compositionLocalOf<PlayerState> {
    error("PlayerState not provided - wrap the app in CompositionLocalProvider(LocalPlayerState provides ...)")
}
