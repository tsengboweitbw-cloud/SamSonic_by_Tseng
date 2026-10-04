package com.example.samsonic.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * Keeps a failed song from leaving the player stuck: a network hiccup gets one retry, and after
 * that (or for a corrupt or unsupported file) the song is skipped, so the queue carries on.
 * [onMessage] is told whether it was skipped (true) or playback just stopped (false), when
 * there was nothing to skip to.
 */
class PlaybackErrorHandler(
    private val player: Player,
    private val onMessage: (skipped: Boolean) -> Unit,
) : Player.Listener {
    private var retriedId: String? = null
    private var failuresInARow = 0

    override fun onPlayerError(error: PlaybackException) {
        val id = player.currentMediaItem?.mediaId
        failuresInARow++
        if (isNetworkError(error) && id != null && id != retriedId) {
            retriedId = id
            player.prepare()
            return
        }
        // Every song in the queue failing in turn: stop rather than loop through them.
        if (player.hasNextMediaItem() && failuresInARow < player.mediaItemCount) {
            player.seekToNextMediaItem()
            player.prepare()
            onMessage(true)
        } else {
            onMessage(false)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            failuresInARow = 0
            retriedId = null
        }
    }

    private fun isNetworkError(error: PlaybackException) =
        error.errorCode in PlaybackException.ERROR_CODE_IO_UNSPECIFIED..ERROR_CODE_IO_LAST
}

// IO errors are the 2000s; READ_POSITION_OUT_OF_RANGE (2008) is the last of them.
private const val ERROR_CODE_IO_LAST = PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
