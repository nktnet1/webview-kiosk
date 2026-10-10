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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.handlers.RemoteInboundHandler
import uk.nktnet.webviewkiosk.managers.CustomNotificationManager
import uk.nktnet.webviewkiosk.managers.CustomNotificationType
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

class MqttForegroundService : Service() {
    companion object {
        private val messageHost = AtomicReference<MqttForegroundService?>(null)

        fun isHandlingMessages(): Boolean = messageHost.get()?.canHandleRemoteMessages() == true
    }

    @Volatile
    private var isServiceActive = false
    @Volatile
    private var notificationGeneration = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollLockTaskModeJob: Job? = null
    private var unregisterCommandHost: (() -> Unit)? = null
    private var mqttSettingsJob: Job? = null
    private var mqttRequestJob: Job? = null
    private var lastStatus: MqttClientState? = null
    private var receiverRegistered = false
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

    private fun startProcessing() {
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(systemReceiver, filter)
            receiverRegistered = true
        }
        if (!::wakeLock.isInitialized) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "${MqttForegroundService::class.java.name}:partial-wakelock"
            )
            wakeLock.setReferenceCounted(false)
        }
        if (!wakeLock.isHeld) {
            @SuppressLint("WakelockTimeout")
            wakeLock.acquire()
        }

        isServiceActive = true
        if (unregisterCommandHost == null) {
            unregisterCommandHost = RemoteMessageManager.registerMqttCommandHost(applicationContext) {
                canHandleRemoteMessages()
            }
        }
        // Subscribe before restoring MQTT so its first delivery cannot race startup.
        if (mqttSettingsJob?.isActive != true) {
            mqttSettingsJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                RemoteMessageManager.settingsFlow.collect { settings ->
                    if (
                        canHandleRemoteMessages()
                        && settings.source == RemoteMessageManager.RemoteMessage.Source.MQTT
                        && settings.tryClaim()
                    ) {
                        RemoteInboundHandler.handleInboundSettings(
                            this@MqttForegroundService,
                            settings.message,
                            settings.source,
                        )
                    }
                }
            }
        }
        if (mqttRequestJob?.isActive != true) {
            mqttRequestJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                RemoteMessageManager.requestsFlow.collect { request ->
                    if (
                        canHandleRemoteMessages()
                        && request.source == RemoteMessageManager.RemoteMessage.Source.MQTT
                        && request.tryClaim()
                    ) {
                        RemoteInboundHandler.handleInboundMqttRequest(
                            this@MqttForegroundService,
                            request.message
                        )
                    }
                }
            }
        }
        // A start request alone is insufficient: keep the activity eligible until all
        // service handlers are registered after a successful foreground promotion.
        messageHost.set(this)
    }

    private fun canHandleRemoteMessages(): Boolean {
        if (!isServiceActive || messageHost.get() !== this) return false
        val userSettings = UserSettings(this)
        return userSettings.mqttEnabled && userSettings.mqttUseForegroundService
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val userSettings = UserSettings(this)
        if (!userSettings.mqttEnabled || !userSettings.mqttUseForegroundService) {
            return stopAfterRejectedStart(startId)
        }

        notificationGeneration++
        try {
            val contentIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            // A sticky restart may be the first entry point in a fresh process.
            CustomNotificationManager.init(applicationContext)
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
            startProcessing()
        } catch (e: Exception) {
            Log.e(
                javaClass.simpleName,
                "Unable to start MQTT foreground service",
                e
            )
            return stopAfterRejectedStart(startId)
        }

        // A configured manager may still be disconnected after a failed attempt.
        // Preserve a live connect/reconnect, but restore a stopped connection.
        if (MqttManager.getState() == MqttClientState.DISCONNECTED) {
            try {
                DeviceOwnerManager.init(applicationContext)
                MqttManager.connect(applicationContext)
            } catch (e: Exception) {
                Log.e(javaClass.simpleName, "Unable to restore MQTT after service restart", e)
                return stopAfterRejectedStart(startId)
            }
        }

        if (!canHandleRemoteMessages()) return stopAfterRejectedStart(startId)
        if (pollLockTaskModeJob?.isActive != true) {
            pollLockTaskModeJob = scope.launch {
                while (isServiceActive) {
                    val generation = notificationGeneration
                    val status = MqttManager.getState()
                    // Publish on the lifecycle thread so teardown cannot race notify().
                    withContext(Dispatchers.Main) {
                        if (!isServiceActive) return@withContext
                        if (!canHandleRemoteMessages()) {
                            stopProcessing()
                            stopSelf()
                            return@withContext
                        }
                        // A repeated start may have already shown a more recent status.
                        if (generation != notificationGeneration) return@withContext
                        if (lastStatus == null || status != lastStatus) {
                            updateNotification(status)
                            lastStatus = status
                        }
                    }
                    delay(1000.milliseconds)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopProcessing()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopAfterRejectedStart(startId: Int): Int {
        stopProcessing()
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun stopProcessing() {
        isServiceActive = false
        // A late cleanup must not revoke a replacement service's ownership.
        messageHost.compareAndSet(this, null)
        notificationGeneration++
        pollLockTaskModeJob?.cancel()
        pollLockTaskModeJob = null
        lastStatus = null
        unregisterCommandHost?.invoke()
        unregisterCommandHost = null
        mqttSettingsJob?.cancel()
        mqttSettingsJob = null
        mqttRequestJob?.cancel()
        mqttRequestJob = null
        if (receiverRegistered) {
            receiverRegistered = false
            try {
                unregisterReceiver(systemReceiver)
            } catch (e: IllegalArgumentException) {
                Log.w(javaClass.simpleName, "MQTT receiver was already unregistered", e)
            }
        }
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
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
