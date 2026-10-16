package uk.nktnet.webviewkiosk.utils

import android.content.Intent
import android.net.Uri
import android.os.BadParcelableException
import android.os.Build
import android.os.Parcelable
import android.util.Log

data class IntentResult(
    val uploadUri: Uri? = null,
    val url: String? = null,
)

private fun <T> readIntentExtra(read: () -> T): T? {
    return try {
        read()
    } catch (e: RuntimeException) {
        // A caller can supply extras with the wrong type or an unreadable
        // Parcelable.
        // Android versions differ in whether they return null or throw for a type
        // mismatch.
        if (e !is BadParcelableException && e !is ClassCastException) {
            throw e
        }
        Log.w("IntentUtils", "Ignoring unreadable intent extras", e)
        null
    }
}

fun Intent.getBooleanExtraSafely(name: String, defaultValue: Boolean): Boolean {
    return readIntentExtra { getBooleanExtra(name, defaultValue) } ?: defaultValue
}

fun <T : Parcelable> Intent.getParcelableExtraSafely(
    name: String,
    type: Class<T>,
): T? {
    return readIntentExtra {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, type)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra<Parcelable>(name)?.let { value ->
                if (type.isInstance(value)) {
                    type.cast(value)
                } else {
                    null
                }
            }
        }
    }
}

fun handleMainIntent(intent: Intent): IntentResult {
    return when (intent.action) {
        Intent.ACTION_VIEW -> {
            // This exported activity registers VIEW only for HTTP(S) links and
            // content
            // attachments. An explicit intent must not bypass that scheme policy
            // (for
            // example, javascript: would otherwise reach WebView.loadUrl directly).
            when (val uri = intent.data) {
                null -> IntentResult()
                else ->
                    when (uri.scheme) {
                        "content" -> IntentResult(uploadUri = uri)
                        "http",
                        "https" ->
                            if (!uri.host.isNullOrBlank()) {
                                IntentResult(url = uri.toString())
                            } else {
                                IntentResult()
                            }
                        else -> IntentResult()
                    }
            }
        }
        Intent.ACTION_SEND -> {
            if (intent.type == "text/plain") {
                val textUrl = readIntentExtra {
                    intent.getStringExtra(Intent.EXTRA_TEXT)
                }
                if (textUrl != null && validateUrl(textUrl)) {
                    return IntentResult(url = textUrl.trim())
                }
                // Some apps share text/plain documents using EXTRA_STREAM rather
                // than text.
                // An invalid or absent URL should still allow a genuine file
                // attachment.
            }

            val uri =
                intent.getParcelableExtraSafely(Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.data

            IntentResult(
                uploadUri =
                    uri?.takeIf {
                        it.scheme == "content" || it.scheme == "file"
                    }
            )
        }
        else -> IntentResult()
    }
}
