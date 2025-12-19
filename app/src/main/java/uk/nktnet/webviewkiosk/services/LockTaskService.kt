package uk.nktnet.webviewkiosk.services

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.BuildConfig
import uk.nktnet.webviewkiosk.R

object NotificationType {
    const val LOCK_TASK_MODE = 1001
}

object MyNotificationChannel {
    object LockTaskMode {
        const val ID = "lock_task_mode_channel"
    }
}

@RequiresApi(28)
class LockTaskService: Service() {
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            coroutineScope.cancel()
            stop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannelIfNeeded()

        val filter = IntentFilter(STOP_ACTION)
        ContextCompat.registerReceiver(
            this, stopReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, MyNotificationChannel.LockTaskMode.ID)
            .setContentTitle("Lock Task Mode Active")
            .setContentText("Tap to return")
            .setSmallIcon(R.drawable.baseline_lock_24)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(pendingIntent)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NotificationType.LOCK_TASK_MODE,
            notification,
            if (Build.VERSION.SDK_INT < 34) 0 else ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        )

        coroutineScope.launch {
            val am = getSystemService(ActivityManager::class.java)
            delay(3000)
            while (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED) {
                delay(1000)
            }
            stop()
        }

        return START_NOT_STICKY
    }

    private fun createChannelIfNeeded() {
        val channel = NotificationChannel(
            MyNotificationChannel.LockTaskMode.ID,
            "Lock Task Mode",
            NotificationManager.IMPORTANCE_LOW
        )
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    fun stop() {
        try {
            unregisterReceiver(stopReceiver)
        } catch (_: Exception) {}
        stopSelf()
    }

    companion object {
        const val STOP_ACTION = "${BuildConfig.APPLICATION_ID}.action.STOP_LOCK_TASK_MODE"
    }
}
