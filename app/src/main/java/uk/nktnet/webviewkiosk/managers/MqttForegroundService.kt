package uk.nktnet.webviewkiosk.managers

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.config.mqtt.messages.MqttDisconnectingEvent

class MqttForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        ServiceCompat.startForeground(
            this,
            CustomNotificationType.MQTT,
            NotificationManager.buildMqttNotification(contentIntent),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            } else {
                0
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MqttManager.connect(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        MqttManager.disconnect(
            MqttDisconnectingEvent.DisconnectCause.SYSTEM_ACTIVITY_STOPPED
        )
        super.onDestroy()
    }
}
