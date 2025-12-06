package com.nktnet.webview_kiosk.utils

import android.content.Intent
import android.net.Uri
import android.os.Build

data class IntentResult(
    val uploadUri: Uri? = null,
    val url: String? = null
)

fun handleMainIntent(intent: Intent): IntentResult {
    return when (intent.action) {
        Intent.ACTION_VIEW -> {
            intent.data?.let { dataUri ->
                if (dataUri.scheme == "content") {
                    IntentResult(uploadUri = dataUri)
                } else {
                    IntentResult(url = dataUri.toString())
                }
            } ?: IntentResult()
        }
        Intent.ACTION_SEND -> {
            if (intent.type == "text/plain") {
                val textUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (textUrl != null && validateUrl(textUrl)) {
                    return IntentResult(url = textUrl.trim())
                }
                return IntentResult()
            }

            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } ?: intent.data

            IntentResult(uploadUri = uri)
        }
        else -> IntentResult()
    }
}
