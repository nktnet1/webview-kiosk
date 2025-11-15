package uk.nktnet.webviewkiosk.utils

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.serialization.Serializable
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.states.InactivityStateSingleton

@Serializable
data class WebviewKioskStatus(
    val isInteractive: Boolean,
    val lastInteractionTime: Long,
    val isLocked: Boolean,
    val batteryPercentage: Int,
    val currentUrl: String,
)

fun getStatus(context: Context): WebviewKioskStatus {
    val systemSettings = SystemSettings(context)
    val pm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService(PowerManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    val bm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService(BatteryManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    val activityManager = context.getSystemService(Application.ACTIVITY_SERVICE) as ActivityManager

    return WebviewKioskStatus(
        isLocked = getIsLocked(activityManager),
        lastInteractionTime = InactivityStateSingleton.lastInteractionState.value,
        isInteractive = pm.isInteractive,
        batteryPercentage = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100),
        currentUrl = systemSettings.currentUrl
    )
}
