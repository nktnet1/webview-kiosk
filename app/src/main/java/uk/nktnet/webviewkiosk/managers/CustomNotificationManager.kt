package uk.nktnet.webviewkiosk.managers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import uk.nktnet.webviewkiosk.R

object CustomNotificationType {
    const val LOCK_TASK_MODE = 1001
    const val MQTT = 1002
}

object CustomNotificationChannel {
    object LockTaskMode {
        const val ID = "lock_task_mode_channel"
    }
    object Mqtt {
        const val ID = "mqtt_service_channel"
    }
}

object CustomNotificationManager {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channels = listOf(
            NotificationChannel(
                CustomNotificationChannel.LockTaskMode.ID,
                appContext.getString(R.string.notification_lock_task_title),
                NotificationManager.IMPORTANCE_LOW
            ),
            NotificationChannel(
                CustomNotificationChannel.Mqtt.ID,
                appContext.getString(R.string.notification_mqtt_title),
                NotificationManager.IMPORTANCE_LOW,
            )
        )

        val nm = NotificationManagerCompat.from(appContext)
        channels.forEach {
            it.setShowBadge(false)
            nm.createNotificationChannel(
                it
            )
        }
    }

    fun buildLockTaskNotification(contentIntent: PendingIntent) =
        buildBaseNotification(
            contentIntent,
            CustomNotificationChannel.LockTaskMode.ID,
            R.string.notification_lock_task_title,
            appContext.getString(R.string.notification_lock_task_text),
            R.drawable.baseline_lock_24,
        )

    fun buildMqttNotification(
        contentIntent: PendingIntent,
        content: String
    ) = buildBaseNotification(
        contentIntent,
        CustomNotificationChannel.Mqtt.ID,
        R.string.notification_mqtt_title,
        content,
        R.drawable.mqtt_24,
    )

    fun updateNotification(service: Service, id: Int, notification: Notification) {
        NotificationManagerCompat.from(service).notify(id, notification)
    }

    private fun buildBaseNotification(
        contentIntent: PendingIntent,
        channelId: String,
        titleRes: Int,
        text: String,
        @DrawableRes smallIcon: Int,
    ): Notification {
        return NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(appContext.getString(titleRes))
            .setContentText(text)
            .setSmallIcon(smallIcon)
            .setContentIntent(contentIntent)
            .setSilent(true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
