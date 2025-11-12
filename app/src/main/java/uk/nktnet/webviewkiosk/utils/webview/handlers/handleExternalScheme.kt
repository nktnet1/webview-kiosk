package uk.nktnet.webviewkiosk.utils.webview.handlers

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

fun handleExternalScheme(context: Context, url: String) {
    try {
        val intent = if (url.startsWith("intent:")) {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } else {
            Intent(Intent.ACTION_VIEW, url.toUri())
        }

        val chooser = Intent.createChooser(intent, "Open with")
        context.startActivity(chooser)

    } catch (e: Exception) {
        e.printStackTrace()
    }
}
