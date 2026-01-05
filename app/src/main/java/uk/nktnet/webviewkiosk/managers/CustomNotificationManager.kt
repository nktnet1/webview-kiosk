package uk.nktnet.webviewkiosk.managers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttNotifyCommand

object CustomNotificationType {
    const val LOCK_TASK_MODE = 1001
    const val MQTT_SERVICE = 1002
    const val MQTT_NOTIFY_COMMAND = 1003
}

object CustomNotificationChannel {
    object LockTaskMode {
        const val ID = "lock_task_mode_channel"
    }
    object MqttService {
        const val ID = "mqtt_service_channel"
    }
    object MqttNotifyCommand {
        const val ID = "mqtt_notify_command_channel"
    }
}

object CustomNotificationManager {
    private val lastMqttMessages = ArrayDeque<String>(5)

    fun init(context: Context) {
        createChannels(context.applicationContext)
    }

    private fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channels = listOf(
            NotificationChannel(
                CustomNotificationChannel.LockTaskMode.ID,
                context.getString(R.string.notification_lock_task_title),
                NotificationManager.IMPORTANCE_LOW
            ),
            NotificationChannel(
                CustomNotificationChannel.MqttService.ID,
                context.getString(R.string.notification_mqtt_service_title),
                NotificationManager.IMPORTANCE_LOW,
            ),
            NotificationChannel(
                CustomNotificationChannel.MqttNotifyCommand.ID,
                context.getString(R.string.notification_mqtt_notify_command_title),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )

        val nm = NotificationManagerCompat.from(context)
        channels.forEach {
            nm.createNotificationChannel(
                it
            )
        }
    }

    private fun buildBaseServiceNotification(
        context: Context,
        contentIntent: PendingIntent,
        channelId: String,
        titleRes: Int,
        text: String,
        @DrawableRes smallIcon: Int,
    ): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(titleRes))
            .setContentText(text)
            .setSmallIcon(smallIcon)
            .setContentIntent(contentIntent)
            .setSilent(true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun buildLockTaskNotification(context: Context, contentIntent: PendingIntent) =
        buildBaseServiceNotification(
            context,
            contentIntent,
            CustomNotificationChannel.LockTaskMode.ID,
            R.string.notification_lock_task_title,
            context.getString(
                R.string.notification_lock_task_text,
                context.getString(R.string.app_name),
            ),
            R.drawable.baseline_lock_24,
        )

    fun buildMqttServiceNotification(
        context: Context,
        contentIntent: PendingIntent,
        content: String
    ) = buildBaseServiceNotification(
        context,
        contentIntent,
        CustomNotificationChannel.MqttService.ID,
        R.string.notification_mqtt_service_title,
        content,
        R.drawable.mqtt_24,
    )

    fun updateServiceNotification(service: Service, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(service).notify(id, notification)
        } catch (e: SecurityException) {
            Log.e(
                javaClass.simpleName,
                "Failed to update service notifications",
                e
            )
        }
    }

    fun sendMqttNotifyCommandNotification(
        context: Context,
        notifyCommand: MqttNotifyCommand
    ) {
        if (lastMqttMessages.size >= 5) {
            lastMqttMessages.removeFirst()
        }
        lastMqttMessages.addLast(notifyCommand.data.contentText)

        val inboxStyle = NotificationCompat.InboxStyle()
        lastMqttMessages.forEach { msg ->
            inboxStyle.addLine(msg)
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            CustomNotificationChannel.MqttNotifyCommand.ID
        )
            .setSmallIcon(R.drawable.outline_circle_notifications_24)
            .setStyle(inboxStyle)
            .setContentIntent(pendingIntent)
            .setSilent(notifyCommand.data.silent)
            .setOngoing(notifyCommand.data.onGoing)
            .setPriority(notifyCommand.data.priority.androidValue)
            .setTimeoutAfter(notifyCommand.data.timeout)
            .setAutoCancel(notifyCommand.data.autoCancel)
            .setContentTitle(notifyCommand.data.contentTitle)
            .setContentText(notifyCommand.data.contentText)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                CustomNotificationType.MQTT_NOTIFY_COMMAND,
                notification
            )
        } catch (e: SecurityException) {
            Log.e(javaClass.simpleName, "Failed to send notification", e)
        }
    }
}
