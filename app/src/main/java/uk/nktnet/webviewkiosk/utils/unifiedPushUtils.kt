package uk.nktnet.webviewkiosk.utils

import android.util.Base64

fun isValidVapidPublicKey(key: String): Boolean {
    if (key.length != 87) {
        return false
    }

    val decoded = try {
        Base64.decode(key, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    } catch (_: IllegalArgumentException) {
        return false
    }

    return decoded.size == 65 && decoded[0] == 0x04.toByte()
}
