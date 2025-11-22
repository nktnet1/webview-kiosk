package com.nktnet.webview_kiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.option.ImmersiveModeOption

fun shouldBeImmersed(activity: Activity, userSettings: UserSettings): Boolean {
    val activityManager = activity.getSystemService(ACTIVITY_SERVICE) as ActivityManager
    val isLocked = getIsLocked(activityManager)
    return when (userSettings.immersiveMode) {
        ImmersiveModeOption.ALWAYS_ON -> true
        ImmersiveModeOption.ALWAYS_OFF -> false
        ImmersiveModeOption.ONLY_WHEN_LOCKED -> isLocked
    }
}

fun enterImmersiveMode(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.insetsController?.let { controller ->
            controller.hide(
                WindowInsets.Type.statusBars()
                or WindowInsets.Type.navigationBars()
            )
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }
}

fun exitImmersiveMode(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.insetsController?.show(
            WindowInsets.Type.statusBars()
            or WindowInsets.Type.navigationBars()
        )
    } else {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}
