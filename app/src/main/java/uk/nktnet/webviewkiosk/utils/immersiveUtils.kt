package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import android.os.Build
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.ImmersiveModeOption

fun shouldBeImmersed(activity: Activity, userSettings: UserSettings): Boolean {
    val activityManager =
        activity.getSystemService(ACTIVITY_SERVICE) as ActivityManager
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
        // Use the window-backed compat controller: on Android 11 a window
        // LayoutParams update can clear the platform controller's bar behaviour.
        // AndroidX keeps the sticky policy in decor flags to survive that update.
        // Configure the policy before hiding, including before the first attach.
        val controller =
            WindowCompat.getInsetsController(
                activity.window,
                activity.window.decorView,
            )
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(
            WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.navigationBars()
        )
    } else {
        val decorView = activity.window.decorView
        val currentFlags = decorView.systemUiVisibility
        val immersiveFlags =
            (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        decorView.systemUiVisibility = currentFlags or immersiveFlags
    }
}

@Suppress("DEPRECATION")
fun exitImmersiveMode(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val controller =
            WindowCompat.getInsetsController(
                activity.window,
                activity.window.decorView,
            )
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        controller.show(
            WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.navigationBars()
        )
    } else {
        val decorView = activity.window.decorView
        val currentFlags = decorView.systemUiVisibility

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            val hiddenFlags =
                (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            // API 23 needs the edge-to-edge layout flags preserved while the system
            // bars are
            // visible. Expanding this workaround to later legacy releases changes
            // IME resize
            // behaviour (notably API 29 with a bottom address bar).
            val edgeToEdgeLayoutFlags =
                (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            decorView.systemUiVisibility =
                (currentFlags and hiddenFlags.inv()) or edgeToEdgeLayoutFlags
        } else {
            val flagsToRemove =
                (View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            decorView.systemUiVisibility = currentFlags and flagsToRemove.inv()
        }
    }
}
