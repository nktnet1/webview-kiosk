package uk.nktnet.webviewkiosk.services

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.hivemq.client.mqtt.MqttClientState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.handlers.MqttHandler
import uk.nktnet.webviewkiosk.managers.CustomNotificationManager
import uk.nktnet.webviewkiosk.managers.CustomNotificationType
import uk.nktnet.webviewkiosk.managers.MqttManager

class MqttForegroundService : Service() {
    private var isServiceActive = true
    private val scope = CoroutineScope(Dispatchers.IO)
    private var pollLockTaskModeJob: Job? = null
    private var mqttCommandJob: Job? = null
    private var mqttSettingsJob: Job? = null
    private var mqttRequestJob: Job? = null
    private var lastStatus: MqttClientState? = null
    private lateinit var wakeLock: PowerManager.WakeLock

    private val systemReceiver = object : BroadcastReceiver() {
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
                else -> Unit
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
        registerReceiver(systemReceiver, filter)

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "${MqttForegroundService::class.java.name}:partial-wakelock"
        )
        wakeLock.setReferenceCounted(false)

        @SuppressLint("WakelockTimeout")
        wakeLock.acquire()

        mqttCommandJob = scope.launch {
            MqttManager.commands.collect { command ->
                MqttHandler.handleInboundCommand(
                    this@MqttForegroundService,
                    command
                )
            }
        }
        mqttSettingsJob = scope.launch {
            MqttManager.settings.collect { settings ->
                MqttHandler.handleInboundSettings(
                    this@MqttForegroundService,
                    settings
                )
            }
        }
        mqttRequestJob = scope.launch {
            MqttManager.requests.collect { request ->
                MqttHandler.handleInboundMqttRequest(
                    this@MqttForegroundService,
                    request
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isServiceActive = true
        if (pollLockTaskModeJob?.isActive != true) {
            pollLockTaskModeJob = scope.launch {
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
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        ServiceCompat.startForeground(
            this,
            CustomNotificationType.MQTT_SERVICE,
            CustomNotificationManager.buildMqttServiceNotification(
                this,
                contentIntent,
                "Status: ${MqttManager.getState().name}",
            ),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            } else {
                0
            }
        )
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForegroundService()
        unregisterReceiver(systemReceiver)
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }

    private fun stopForegroundService() {
        try {
            isServiceActive = false
            pollLockTaskModeJob?.cancel()
            mqttCommandJob?.cancel()
            mqttSettingsJob?.cancel()
            mqttRequestJob?.cancel()
            scope.cancel()
        } catch (e: Exception) {
            Log.e(javaClass.simpleName, "Failed to stop foreground service", e)
        }
    }

    private fun updateNotification(newStatus: MqttClientState) {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = CustomNotificationManager.buildMqttServiceNotification(
            this,
            contentIntent,
            "Status: $newStatus",
        )
        CustomNotificationManager.updateServiceNotification(
            this,
            CustomNotificationType.MQTT_SERVICE,
            notification
        )
    }
}
