package uk.nktnet.webviewkiosk.services

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.hivemq.client.mqtt.MqttClientState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.managers.CustomNotificationManager
import uk.nktnet.webviewkiosk.managers.CustomNotificationType
import uk.nktnet.webviewkiosk.managers.MqttManager

class MqttForegroundService : Service() {
    private var isServiceActive = true
    private val scope = CoroutineScope(Dispatchers.IO)
    private var updateJob: Job? = null
    private var lastStatus: MqttClientState? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    MqttManager.publishScreenOnEvent()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    MqttManager.publishScreenOffEvent()
                }
                Intent.ACTION_USER_PRESENT -> {
                    MqttManager.publishUserPresentEvent()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isServiceActive = true
        if (updateJob?.isActive != true) {
            updateJob = scope.launch {
                while (isServiceActive) {
                    val status = MqttManager.getState()
                    if (lastStatus != null && status == lastStatus) {
                        continue
                    }
                    lastStatus = status
                    updateNotification(status)
                    delay(1000L)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForegroundService()
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    private fun stopForegroundService() {
        try {
            isServiceActive = false
            updateJob?.cancel()
            scope.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startForegroundService() {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        ServiceCompat.startForeground(
            this,
            CustomNotificationType.MQTT,
            CustomNotificationManager.buildMqttNotification(
                contentIntent,
                "Status: ${MqttManager.getState().name}"
            ),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            } else {
                0
            }
        )
    }

    private fun updateNotification(newStatus: MqttClientState) {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = CustomNotificationManager.buildMqttNotification(
            contentIntent,
            "Status: $newStatus",
        )
        CustomNotificationManager.updateNotification(
            this,
            CustomNotificationType.MQTT,
            notification
        )
    }
}
