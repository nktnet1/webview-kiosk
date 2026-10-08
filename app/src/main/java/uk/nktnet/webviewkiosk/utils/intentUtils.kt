package uk.nktnet.webviewkiosk.utils

import android.content.Intent
import android.net.Uri
import android.os.BadParcelableException
import android.os.Build
import android.os.Parcelable
import android.util.Log

data class IntentResult(
    val uploadUri: Uri? = null,
    val url: String? = null
)

private fun <T> readIntentExtra(read: () -> T): T? {
    return try {
        read()
    } catch (e: BadParcelableException) {
        // Reading any extra may deserialize an unrelated, unreadable parcelable in the bundle.
        Log.w("IntentUtils", "Ignoring unreadable intent extras", e)
        null
    }
}

fun Intent.getBooleanExtraSafely(name: String, defaultValue: Boolean): Boolean {
    return readIntentExtra { getBooleanExtra(name, defaultValue) } ?: defaultValue
}

fun <T : Parcelable> Intent.getParcelableExtraSafely(name: String, type: Class<T>): T? {
    return readIntentExtra {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, type)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra<Parcelable>(name)?.let { value ->
                if (type.isInstance(value)) type.cast(value) else null
            }
        }
    }
}

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
                val textUrl = readIntentExtra { intent.getStringExtra(Intent.EXTRA_TEXT) }
                if (textUrl != null && validateUrl(textUrl)) {
                    return IntentResult(url = textUrl.trim())
                }
                return IntentResult()
            }

            val uri = intent.getParcelableExtraSafely(Intent.EXTRA_STREAM, Uri::class.java)
                ?: intent.data

            IntentResult(uploadUri = uri)
        }
        else -> IntentResult()
    }
}
