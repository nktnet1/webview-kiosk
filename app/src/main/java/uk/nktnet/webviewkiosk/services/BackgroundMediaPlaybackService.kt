package uk.nktnet.webviewkiosk.services

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.managers.CustomNotificationChannel
import uk.nktnet.webviewkiosk.managers.CustomNotificationManager
import uk.nktnet.webviewkiosk.managers.CustomNotificationType
import uk.nktnet.webviewkiosk.utils.webview.BackgroundMediaPlaybackController

/** The WebView remains the player; this foreground service holds no media URL. */
class BackgroundMediaPlaybackService : Service() {
    companion object {
        private const val ACTION_START = "uk.nktnet.webviewkiosk.action.MEDIA_START"
        private const val ACTION_STOP = "uk.nktnet.webviewkiosk.action.MEDIA_STOP"

        fun startIntent(context: Context) =
            Intent(context, BackgroundMediaPlaybackService::class.java)
                .setAction(ACTION_START)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            BackgroundMediaPlaybackController.stopFromNotification()
            stopSelf()
            return START_NOT_STICKY
        }
        if (
            intent?.action != ACTION_START ||
                !UserSettings(this).backgroundMediaPlayback ||
                !BackgroundMediaPlaybackController.isPlaybackRequested()
        ) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        CustomNotificationManager.init(this)
        val openIntent =
            packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(this, MainActivity::class.java)
        val openPendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                0,
                Intent(this, BackgroundMediaPlaybackService::class.java)
                    .setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(
                    this,
                    CustomNotificationChannel.BackgroundMediaPlayback.ID,
                )
                .setSmallIcon(R.drawable.outline_circle_notifications_24)
                .setContentTitle(
                    getString(R.string.notification_background_media_title)
                )
                .setContentText(
                    getString(R.string.notification_background_media_text)
                )
                .setContentIntent(openPendingIntent)
                .addAction(
                    R.drawable.outline_circle_notifications_24,
                    getString(R.string.notification_background_media_stop),
                    stopPendingIntent,
                )
                .setSilent(true)
                .setOngoing(true)
                .setCategory(android.app.Notification.CATEGORY_TRANSPORT)
                .setForegroundServiceBehavior(
                    NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                )
                .build()
        try {
            ServiceCompat.startForeground(
                this,
                CustomNotificationType.BACKGROUND_MEDIA_PLAYBACK,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                },
            )
        } catch (error: RuntimeException) {
            Log.w(
                "BackgroundMediaPlayback",
                "Unable to promote playback service",
                error,
            )
            BackgroundMediaPlaybackController.stopFromNotification()
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }
}
