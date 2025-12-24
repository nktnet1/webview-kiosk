package com.nktnet.webview_kiosk.utils

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.serialization.Serializable
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.option.LockStateType
import com.nktnet.webview_kiosk.states.UserInteractionStateSingleton

@Serializable
data class WebviewKioskStatus(
    val currentUrl: String,
    val isLocked: Boolean,
    val lockStateType: LockStateType,
    val lastInteractionTime: Long,
    val batteryPercentage: Int,
    val appBrightnessPercentage: Int,
    val systemBrightness: Int,
    val isDeviceInteractive: Boolean,
)

fun getStatus(context: Context): WebviewKioskStatus {
    val systemSettings = SystemSettings(context)
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    val bm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService(BatteryManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    val activityManager = context.getSystemService(
        Application.ACTIVITY_SERVICE
    ) as ActivityManager

    val systemBrightness = Settings.System.getInt(
        context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS,
        255
    )

    return WebviewKioskStatus(
        currentUrl = systemSettings.currentUrl,
        isLocked = getIsLocked(activityManager),
        lockStateType = LockStateType.fromActivityManager(activityManager),
        lastInteractionTime = UserInteractionStateSingleton.lastInteractionState.value,
        batteryPercentage = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100),
        appBrightnessPercentage = getWindowBrightness(context),
        systemBrightness = systemBrightness,
        isDeviceInteractive = pm.isInteractive,
    )
}
