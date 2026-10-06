package uk.nktnet.webviewkiosk.services

import android.app.ActivityManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.BuildConfig
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.managers.CustomNotificationManager
import uk.nktnet.webviewkiosk.managers.CustomNotificationType
import kotlin.time.Duration.Companion.milliseconds

@RequiresApi(28)
class LockTaskService: Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var updateJob: Job? = null
    private var receiverRegistered = false

    override fun onBind(intent: Intent?): IBinder? = null

    private val returnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopLockTaskService()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForegroundNotification()
        } catch (e: Exception) {
            Log.e(javaClass.simpleName, "Unable to start lock task foreground service", e)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (updateJob?.isActive != true) {
            updateJob = scope.launch {
                try {
                    val am = getSystemService(ActivityManager::class.java)
                    delay(3000.milliseconds)
                    while (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED) {
                        delay(1000.milliseconds)
                    }
                    stopLockTaskService()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(javaClass.simpleName, "Unable to monitor lock task mode", e)
                    stopLockTaskService()
                }
            }
        }

        return START_STICKY
    }

    private fun startForegroundNotification() {
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                returnReceiver,
                IntentFilter(RETURN_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        ServiceCompat.startForeground(
            this,
            CustomNotificationType.LOCK_TASK_MODE,
            CustomNotificationManager.buildLockTaskNotification(
                this,
                contentIntent
            ),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            } else {
                0
            }
        )
    }

    private fun stopLockTaskService() {
        stopSelf()
    }

    override fun onDestroy() {
        updateJob?.cancel()
        scope.cancel()
        if (receiverRegistered) {
            receiverRegistered = false
            try {
                unregisterReceiver(returnReceiver)
            } catch (e: IllegalArgumentException) {
                Log.w(javaClass.simpleName, "Lock task receiver was already unregistered", e)
            }
        }
        super.onDestroy()
    }

    companion object {
        const val RETURN_ACTION = "${BuildConfig.APPLICATION_ID}.action.RETURN_ACTION"
    }
}
