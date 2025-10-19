package uk.nktnet.webviewkiosk.main

import android.content.pm.ActivityInfo
import androidx.appcompat.app.AppCompatActivity
import uk.nktnet.webviewkiosk.config.option.DeviceRotationOption

fun AppCompatActivity.applyDeviceRotation(rotation: DeviceRotationOption) {
    requestedOrientation = when (rotation) {
        DeviceRotationOption.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        DeviceRotationOption.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        DeviceRotationOption.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        DeviceRotationOption.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        DeviceRotationOption.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    }
}
