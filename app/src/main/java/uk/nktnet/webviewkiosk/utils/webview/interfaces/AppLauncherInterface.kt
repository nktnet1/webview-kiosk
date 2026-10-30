package uk.nktnet.webviewkiosk.utils.webview.interfaces

import android.content.Context
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import uk.nktnet.webviewkiosk.utils.isPackageInstalled
import uk.nktnet.webviewkiosk.utils.openPackage

class AppLauncherInterface(private val context: Context) {
    companion object {
        const val NAME = "WebviewKioskAppInterface"
    }

    @Suppress("unused")
    @JavascriptInterface
    fun launchPackage(packageName: String): Boolean {
        val name = packageName.trim()
        if (name.isEmpty() || !isPackageInstalled(context, name)) {
            return false
        }
        // openPackage already enforces the lock task rules and reports the
        // reason to the user, so the page only needs to know it was accepted.
        openPackage(context = context, packageName = name)
        return true
    }

    @Suppress("unused")
    @JavascriptInterface
    fun isInstalled(packageName: String): Boolean =
        packageName.trim().isNotEmpty() &&
            isPackageInstalled(context, packageName.trim())

    /**
     * Lets a page draw only the tiles it can actually open, instead of
     * offering an app that is not on this device.
     */
    @Suppress("unused")
    @JavascriptInterface
    fun getInstalled(packageNames: String): String {
        val result = JSONObject()
        try {
            val requested = JSONArray(packageNames)
            for (i in 0 until requested.length()) {
                val name = requested.optString(i).trim()
                if (name.isNotEmpty()) {
                    result.put(name, isPackageInstalled(context, name))
                }
            }
        } catch (_: Exception) {
            return JSONObject().toString()
        }
        return result.toString()
    }
}
