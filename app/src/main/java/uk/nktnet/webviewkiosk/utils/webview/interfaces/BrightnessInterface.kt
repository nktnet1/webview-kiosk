package com.nktnet.webview_kiosk.utils.webview.interfaces

import android.app.Activity
import android.content.Context
import android.webkit.JavascriptInterface
import com.nktnet.webview_kiosk.utils.getWindowBrightness
import com.nktnet.webview_kiosk.utils.setWindowBrightness

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
