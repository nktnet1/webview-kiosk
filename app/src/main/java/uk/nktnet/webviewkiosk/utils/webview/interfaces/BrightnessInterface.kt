package uk.nktnet.webviewkiosk.utils.webview.interfaces

import android.app.Activity
import android.content.Context
import android.webkit.JavascriptInterface
import uk.nktnet.webviewkiosk.utils.getWindowBrightness
import uk.nktnet.webviewkiosk.utils.setWindowBrightness

class BrightnessInterface(private val context: Context) {
    val name = "WebviewKioskBrightnessInterface"

    @Suppress("unused")
    @JavascriptInterface
    fun getBrightness(): Int {
        return getWindowBrightness(context)
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
