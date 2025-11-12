package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.DeviceRotationOption
import uk.nktnet.webviewkiosk.states.KeepScreenOnStateSingleton
import uk.nktnet.webviewkiosk.states.ThemeStateSingleton

fun setWindowBrightness(context: Context, value: Int) {
    val activity = context as? Activity ?: return
    val window = activity.window
    val layoutParams: WindowManager.LayoutParams = window.attributes
    if (value < 0) {
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    } else {
        layoutParams.screenBrightness = (value / 100f).coerceIn(0f, 1f)
    }
    window.attributes = layoutParams
}

fun setDeviceRotation(context: Context, rotation: DeviceRotationOption) {
    val activity = context as? AppCompatActivity ?: return
    activity.requestedOrientation = when (rotation) {
        DeviceRotationOption.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        DeviceRotationOption.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        DeviceRotationOption.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        DeviceRotationOption.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        DeviceRotationOption.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    }
}

fun updateDeviceSettings(context: Context) {
    val userSettings = UserSettings(context)
    KeepScreenOnStateSingleton.setKeepScreenOn(userSettings.keepScreenOn)
    ThemeStateSingleton.setTheme(userSettings.theme)
    setDeviceRotation(context, userSettings.rotation)
    setWindowBrightness(context, userSettings.brightness)
}
