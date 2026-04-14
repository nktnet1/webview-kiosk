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
import androidx.core.content.FileProvider
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundNotifyCommand
import java.io.File

object CustomNotificationType {
    const val LOCK_TASK_MODE = 1001
    const val MQTT_SERVICE = 1002
    const val REMOTE_NOTIFY_COMMAND = 1003
    const val BLOB_DOWNLOAD = 1004
}

object CustomNotificationChannel {
    object LockTaskMode {
        const val ID = "lock_task_mode_channel"
    }
    object MqttService {
        const val ID = "mqtt_service_channel"
    }
    object RemoteNotifyCommand {
        const val ID = "remote_notify_command_channel"
    }
    object BlobDownload {
        const val ID = "blob_download_channel"
    }
}

object CustomNotificationManager {
    private val lastNotifications = ArrayDeque<String>(5)

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
                CustomNotificationChannel.RemoteNotifyCommand.ID,
                context.getString(R.string.notification_remote_notify_command_title),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            NotificationChannel(
                CustomNotificationChannel.BlobDownload.ID,
                context.getString(R.string.notification_blob_download_title),
                NotificationManager.IMPORTANCE_LOW
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

    fun sendInboundNotifyCommandNotification(
        context: Context,
        notifyCommand: InboundNotifyCommand
    ) {
        if (lastNotifications.size >= 5) {
            lastNotifications.removeFirst()
        }
        lastNotifications.addLast(notifyCommand.data.contentText)

        val inboxStyle = NotificationCompat.InboxStyle()
        lastNotifications.forEach { msg ->
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
            CustomNotificationChannel.RemoteNotifyCommand.ID
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
                CustomNotificationType.REMOTE_NOTIFY_COMMAND,
                notification
            )
        } catch (e: SecurityException) {
            Log.e(
                javaClass.simpleName,
                "Failed to send remote notify command notification",
                e
            )
        }
    }

    fun sendBlobDownloadNotification(
        context: Context,
        file: File,
        mimeType: String? = null
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val resolvedMime = mimeType ?: "*/*"

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooserIntent = Intent.createChooser(
            viewIntent,
            "Open file with"
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            file.hashCode(),
            chooserIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            CustomNotificationChannel.BlobDownload.ID
        )
            .setSmallIcon(R.drawable.outline_cloud_download_24)
            .setContentTitle(file.name)
            .setContentText("Tap to open")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat
                .from(context)
                .notify(CustomNotificationType.BLOB_DOWNLOAD, notification)
        } catch (e: SecurityException) {
            Log.e(
                javaClass.simpleName,
                "Failed to send blob download notification",
                e
            )
        }
    }
}
