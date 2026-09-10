package uk.nktnet.webviewkiosk.utils.webview

import android.content.Context
import android.nfc.NfcAdapter

fun getNfcAdapterOrNull(context: Context): NfcAdapter? {
    return runCatching {
        NfcAdapter.getDefaultAdapter(context)
    }.getOrNull()
}

fun isNfcEnabled(context: Context): Boolean {
    return runCatching {
        getNfcAdapterOrNull(context)?.isEnabled == true
    }.getOrDefault(false)
}
