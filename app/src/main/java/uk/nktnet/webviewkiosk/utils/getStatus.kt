package uk.nktnet.webviewkiosk.utils

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.serialization.Serializable
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.states.UserInteractionStateSingleton

@Serializable
data class WebviewKioskStatus(
    val currentUrl: String,
    val isInteractive: Boolean,
    val lastInteractionTime: Long,
    val isLocked: Boolean,
    val batteryPercentage: Int,
    val appBrightnessPercentage: Int,
    val systemBrightness: Int,
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

    val systemBrightness = Settings.System.getInt(
        context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS,
        255
    )

    return WebviewKioskStatus(
        currentUrl = systemSettings.currentUrl,
        isLocked = getIsLocked(activityManager),
        lastInteractionTime = UserInteractionStateSingleton.lastInteractionState.value,
        isInteractive = pm.isInteractive,
        batteryPercentage = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100),
        appBrightnessPercentage = getWindowBrightness(context),
        systemBrightness = systemBrightness
    )
}
