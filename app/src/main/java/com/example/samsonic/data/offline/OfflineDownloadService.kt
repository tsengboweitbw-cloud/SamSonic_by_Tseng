package com.example.samsonic.data.offline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.samsonic.MainActivity
import com.example.samsonic.R
import com.example.samsonic.SamSonicApplication
import com.example.samsonic.locale.AppLanguages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app running, with a notice showing how far it has got, while
 * [OfflineDownloader] saves the songs kept for offline. It stops itself once nothing
 * is being saved.
 */
class OfflineDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLanguages.wrap(base))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloader = (application as SamSonicApplication).container.offlineDownloader
        createChannel()
        startForeground(NOTIFICATION_ID, notification(downloader.progress.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        watching?.cancel()
        watching = scope.launch {
            downloader.progress.collect { progress ->
                if (progress == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(progress))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(progress: OfflineProgress?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.offline_notification_title))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
        if (progress != null && progress.total > 0) {
            builder.setContentText(progress.current ?: getString(R.string.offline_notification_waiting))
                .setSubText(getString(R.string.offline_notification_count, progress.done, progress.total))
                .setProgress(progress.total, progress.done, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.offline_channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "offline_downloads"
        const val NOTIFICATION_ID = 2201
    }
}
