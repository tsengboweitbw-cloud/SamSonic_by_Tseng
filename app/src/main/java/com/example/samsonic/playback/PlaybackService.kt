package com.example.samsonic.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.samsonic.MainActivity
import com.example.samsonic.SamSonicApplication
import com.example.samsonic.playback.dsd.DsdExtractorsFactory
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Hosts the real ExoPlayer + MediaSession so playback, the notification, and lock-screen
 * controls keep running when the app is backgrounded. Streaming goes through the same
 * OkHttpClient as the API calls, so it honors the same network security config (cleartext /
 * self-signed LAN servers), and through the music cache ([MusicCache][com.example.samsonic.data.MusicCache]),
 * which [MusicPrefetcher] fills ahead of the player.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var scrobbler: Scrobbler? = null
    private var prefetcher: MusicPrefetcher? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(UnstableApi::class) // Media3's extractor API, for DSD.
    override fun onCreate() {
        super.onCreate()
        val container = (application as SamSonicApplication).container
        val httpFactory = OkHttpDataSource.Factory(container.okHttpClient)
        // Server streams through the music cache; the phone's own music read directly.
        val dataSourceFactory = CachingDataSourceFactory(
            cache = container.musicCache.cache,
            upstream = httpFactory,
            direct = DefaultDataSource.Factory(this, httpFactory),
        )
        // DSD (DSF/DFF) has no Android decoder; its extractor turns it into PCM itself.
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, DsdExtractorsFactory())

        val player = ExoPlayer.Builder(this, renderersFactory(this, container.bitPerfect))
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(streamingLoadControl())
            .build()
        player.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (shuffleModeEnabled) player.reshuffleFromCurrent()
            }
        })
        // Application-scoped, so a scrobble sent just before the service stops still goes out.
        scrobbler = Scrobbler(player, { container.repository }, container.applicationScope)
        container.audioOutput.attach(player)
        container.bitPerfect.attach(player)
        prefetcher = MusicPrefetcher(
            player = player,
            cache = container.musicCache.cache,
            context = this,
            upstream = httpFactory,
            wifiOnly = container.musicCache.prefetchWifiOnly,
            scope = serviceScope,
        )

        // One UI builds its status bar music chip and Now Bar card only for a media
        // notification that opens something when tapped, so the session needs an activity.
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback(player))
            .build()
    }

    /** Offers the shuffle commands (see ShuffleCommands.kt) to this app's own controller and applies them to [player]. */
    private inner class SessionCallback(private val player: ExoPlayer) : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            if (controller.packageName != packageName) return super.onConnect(session, controller)
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(ShuffleInsertCommand)
                        .add(ShuffleMoveNextCommand)
                        .build(),
                )
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ShuffleInsertCommand.customAction -> player.shuffleInsert(args)
                ShuffleMoveNextCommand.customAction -> player.shuffleMoveNext(args)
                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        (application as SamSonicApplication).container.run {
            audioOutput.detach()
            bitPerfect.detach()
        }
        prefetcher?.release()
        prefetcher = null
        serviceScope.cancel()
        scrobbler?.release()
        scrobbler = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

/**
 * A deeper buffer than Media3's default (which suits video), so a poor connection has
 * minutes of music in hand rather than seconds: it loads up to three minutes ahead
 * and tops up once under one. Capped by size too, as hi-res files run large. After a
 * stall it waits for a few seconds' worth before playing on, so a weak signal gives
 * one short pause instead of stopping and starting over and over.
 */
@OptIn(UnstableApi::class)
private fun streamingLoadControl(): DefaultLoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        /* minBufferMs = */ 60_000,
        /* maxBufferMs = */ 180_000,
        /* bufferForPlaybackMs = */ 2_500,
        /* bufferForPlaybackAfterRebufferMs = */ 8_000,
    )
    .setTargetBufferBytes(48 * 1024 * 1024)
    .setPrioritizeTimeOverSizeThresholds(false)
    .build()
