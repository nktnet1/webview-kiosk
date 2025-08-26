package com.nktnet.webview_kiosk.utils.webview

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

fun handleExternalScheme(context: Context, url: String) {
    try {
        if (url.startsWith("intent:")) {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            context.startActivity(intent)
        } else {
            // mailto:, sms:, tel:, intent:, spotify:, whatsapp:, etc
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            context.startActivity(intent)
        }
    } catch (_: Exception) {
        // Do nothing
    }
}
