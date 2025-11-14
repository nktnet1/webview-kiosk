package uk.nktnet.webviewkiosk.utils.webview.interfaces

import android.app.Activity
import android.content.Context
import android.webkit.JavascriptInterface
import uk.nktnet.webviewkiosk.utils.setWindowBrightness

class BrightnessInterface(private val context: Context) {
    val name = "WebviewKioskBrightnessInterface"

    @Suppress("unused")
    @JavascriptInterface
    fun getBrightness(): Int {
        val activity = context as? Activity ?: return -1
        val brightness = activity.window.attributes.screenBrightness
        return if (brightness < 0) {
            -1
        } else {
            (brightness * 100).toInt().coerceIn(0, 100)
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun setBrightness(brightness: Int) {
        val activity = context as? Activity ?: return
        activity.runOnUiThread {
            setWindowBrightness(activity, brightness)
        }
    }
}
