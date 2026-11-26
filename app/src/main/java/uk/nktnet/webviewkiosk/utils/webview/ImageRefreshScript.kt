package uk.nktnet.webviewkiosk.utils.webview

import android.content.Context
import org.json.JSONObject

/** Refresh images matching the long-pressed URL without reloading the page. */
internal fun refreshImageScript(context: Context, imageUrl: String): String {
    val script =
        context.assets.open("image-refresh.js").bufferedReader().use {
            it.readText()
        }
    val quotedUrl =
        JSONObject.quote(imageUrl)
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")
    return "${script.trim()}($quotedUrl)"
}
