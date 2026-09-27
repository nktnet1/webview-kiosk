package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.ImmersiveModeOption

fun shouldBeImmersed(activity: Activity, userSettings: UserSettings): Boolean {
    val activityManager = activity.getSystemService(ACTIVITY_SERVICE) as ActivityManager
    val isLocked = getIsLocked(activityManager)
    return when (userSettings.immersiveMode) {
        ImmersiveModeOption.ALWAYS_ON -> true
        ImmersiveModeOption.ALWAYS_OFF -> false
        ImmersiveModeOption.ONLY_WHEN_LOCKED -> isLocked
    }
}

@Suppress("DEPRECATION")
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
        val decorView = activity.window.decorView
        val currentFlags = decorView.systemUiVisibility
        val immersiveFlags = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        decorView.systemUiVisibility = currentFlags or immersiveFlags
    }
}

@Suppress("DEPRECATION")
fun exitImmersiveMode(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.insetsController?.show(
            WindowInsets.Type.statusBars()
            or WindowInsets.Type.navigationBars()
        )

    } else {
        val decorView = activity.window.decorView
        val currentFlags = decorView.systemUiVisibility

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            val hiddenFlags = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
            // WindowCompat.setDecorFitsSystemWindows(window, false) is implemented with these
            // layout flags on API 23. Removing them when leaving immersive mode makes the decor
            // fit the visible system bars again, so Compose then applies the same insets twice.
            val edgeToEdgeLayoutFlags = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
            decorView.systemUiVisibility =
                (currentFlags and hiddenFlags.inv()) or edgeToEdgeLayoutFlags
        } else {
            val flagsToRemove = (
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
            decorView.systemUiVisibility = currentFlags and flagsToRemove.inv()
        }
    }
}
