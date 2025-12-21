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
    const val MQTT = 1002
}

object CustomNotificationChannel {
    object LockTaskMode {
        const val ID = "lock_task_mode_channel"
    }
    object Mqtt {
        const val ID = "mqtt_channel"
    }
}

object NotificationManager {
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
                SysNotificationManager.IMPORTANCE_LOW
            ),
            NotificationChannel(
                CustomNotificationChannel.Mqtt.ID,
                appContext.getString(R.string.notification_mqtt_title),
                SysNotificationManager.IMPORTANCE_LOW
            )
        )

        val nm = NotificationManagerCompat.from(appContext)
        channels.forEach { nm.createNotificationChannel(it) }
    }

    fun buildLockTaskNotification(contentIntent: PendingIntent) =
        buildBaseNotification(
            contentIntent,
            CustomNotificationChannel.LockTaskMode.ID,
            R.string.notification_lock_task_title,
            R.string.notification_lock_task_text
        )

    fun buildMqttNotification(contentIntent: PendingIntent) =
        buildBaseNotification(
            contentIntent,
            CustomNotificationChannel.Mqtt.ID,
            R.string.notification_mqtt_title,
            R.string.notification_mqtt_text
        )

    private fun buildBaseNotification(
        contentIntent: PendingIntent,
        channelId: String,
        titleRes: Int,
        textRes: Int
    ) = NotificationCompat.Builder(appContext, channelId)
        .setContentTitle(appContext.getString(titleRes))
        .setContentText(appContext.getString(textRes, Constants.APP_NAME))
        .setSmallIcon(R.drawable.baseline_lock_24)
        .setContentIntent(contentIntent)
        .setSilent(true)
        .setOngoing(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()
}
