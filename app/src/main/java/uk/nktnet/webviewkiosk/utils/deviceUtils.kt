package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.content.Context
import android.view.WindowManager

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
