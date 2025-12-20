package uk.nktnet.webviewkiosk.managers

import android.app.NotificationChannel
import android.app.NotificationManager as SysNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants

object CustomNotificationType {
    const val LOCK_TASK_MODE = 1001
}

object CustomNotificationChannel {
    object LockTaskMode {
        const val ID = "lock_task_mode_channel"
    }
}

object NotificationManager {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        createChannelIfNeeded()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CustomNotificationChannel.LockTaskMode.ID,
                appContext.getString(R.string.notification_lock_task_title),
                SysNotificationManager.IMPORTANCE_LOW,
            )
            NotificationManagerCompat
                .from(appContext)
                .createNotificationChannel(channel)
        }
    }

    fun buildNotification(contentIntent: PendingIntent) =
        NotificationCompat.Builder(
            appContext,
            CustomNotificationChannel.LockTaskMode.ID,
        )
            .setContentTitle(appContext.getString(R.string.notification_lock_task_title))
            .setContentText(
                appContext.getString(
                    R.string.notification_lock_task_text,
                    Constants.APP_NAME)
            )
            .setSmallIcon(R.drawable.baseline_lock_24)
            .setContentIntent(contentIntent)
            .setSilent(true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
}
