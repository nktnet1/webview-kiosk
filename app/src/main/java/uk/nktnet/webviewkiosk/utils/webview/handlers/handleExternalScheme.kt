package uk.nktnet.webviewkiosk.utils.webview.handlers

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

fun handleExternalScheme(context: Context, url: String) {
    try {
        if (url.startsWith("intent:")) {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            context.startActivity(intent)
        } else {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
