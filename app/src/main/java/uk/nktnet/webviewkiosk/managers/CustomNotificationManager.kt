package uk.nktnet.webviewkiosk.managers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
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
    private lateinit var appContext: Context
    private val lastMqttMessages = ArrayDeque<NotificationCompat.MessagingStyle.Message>(5)

    fun init(context: Context) {
        appContext = context.applicationContext
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channels = listOf(
            NotificationChannel(
                CustomNotificationChannel.LockTaskMode.ID,
                appContext.getString(R.string.notification_lock_task_title),
                NotificationManager.IMPORTANCE_LOW
            ),
            NotificationChannel(
                CustomNotificationChannel.MqttService.ID,
                appContext.getString(R.string.notification_mqtt_service_title),
                NotificationManager.IMPORTANCE_LOW,
            ),
            NotificationChannel(
                CustomNotificationChannel.MqttNotifyCommand.ID,
                appContext.getString(R.string.notification_mqtt_notify_command_title),
                NotificationManager.IMPORTANCE_DEFAULT,
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

    fun buildLockTaskNotification(contentIntent: PendingIntent) =
        buildBaseNotification(
            contentIntent,
            CustomNotificationChannel.LockTaskMode.ID,
            R.string.notification_lock_task_title,
            appContext.getString(R.string.notification_lock_task_text),
            R.drawable.baseline_lock_24,
        )

    fun buildMqttServiceNotification(
        contentIntent: PendingIntent,
        content: String
    ) = buildBaseNotification(
        contentIntent,
        CustomNotificationChannel.MqttService.ID,
        R.string.notification_mqtt_service_title,
        content,
        R.drawable.mqtt_24,
    )

    fun updateServiceNotification(service: Service, id: Int, notification: Notification) {
        NotificationManagerCompat.from(service).notify(id, notification)
    }

    fun sendMqttNotifyCommandNotification(
        context: Context,
        notifyCommand: MqttNotifyCommand
    ) {
        val now = System.currentTimeMillis()
        if (lastMqttMessages.size == 5) {
            lastMqttMessages.removeFirst()
        }
        val mqttPerson = Person.Builder().setName("MQTT").build()
        lastMqttMessages.addLast(
            NotificationCompat.MessagingStyle.Message(
                notifyCommand.data.message,
                now,
                mqttPerson
            )
        )

        val user = Person.Builder().setName("MQTT").build()
        val messagingStyle = NotificationCompat.MessagingStyle(user)
        lastMqttMessages.forEach { msg ->
            messagingStyle.addMessage(msg)
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
            .setStyle(messagingStyle)
            .setContentIntent(pendingIntent)
            .setSilent(notifyCommand.data.silent)
            .setOngoing(notifyCommand.data.onGoing)
            .setPriority(notifyCommand.data.priority.androidValue)
            .build()

        NotificationManagerCompat.from(context).notify(
            CustomNotificationType.MQTT_NOTIFY_COMMAND,
            notification
        )
    }
}
