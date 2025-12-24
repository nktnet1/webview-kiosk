package com.nktnet.webview_kiosk.services

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
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nktnet.webview_kiosk.BuildConfig
import com.nktnet.webview_kiosk.managers.CustomNotificationType
import com.nktnet.webview_kiosk.managers.CustomNotificationManager

@RequiresApi(28)
class LockTaskService: Service() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var updateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private val returnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopLockTaskService()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ContextCompat.registerReceiver(
            this,
            returnReceiver,
            IntentFilter(RETURN_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
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

        if (updateJob?.isActive != true) {
            updateJob = scope.launch {
                val am = getSystemService(ActivityManager::class.java)
                delay(3000)
                while (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED) {
                    delay(1000)
                }
                stopLockTaskService()
            }
        }

        return START_STICKY
    }

    private fun stopLockTaskService() {
        try {
            updateJob?.cancel()
            scope.cancel()
            unregisterReceiver(returnReceiver)
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val RETURN_ACTION = "${BuildConfig.APPLICATION_ID}.action.RETURN_ACTION"
    }
}
