package uk.nktnet.webviewkiosk.utils.webview

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.util.Log

/**
 * Keeps NFC foreground dispatch registered across activity and NFC-service wake-ups.
 * This cannot turn the device's NFC radio back on if Android has disabled it.
 */
internal class NfcForegroundDispatch(
    private val activity: Activity,
    private val isAllowed: () -> Boolean,
    private val adapterProvider: () -> NfcAdapter? = {
        getNfcAdapterOrNull(activity)
    },
) {
    private var resumed = false

    fun onResume() {
        resumed = true
        register("activity resumed")
    }

    fun onPause() {
        resumed = false
        val adapter = adapterProvider() ?: return
        runCatching { adapter.disableForegroundDispatch(activity) }
            .onFailure {
                Log.w(TAG, "Failed to disable NFC foreground dispatch", it)
            }
    }

    fun onScreenOn() {
        if (resumed) {
            register("screen on")
        }
    }

    fun onWindowFocusGained() {
        if (resumed) {
            register("window focused")
        }
    }

    fun onAdapterStateChanged(state: Int) {
        if (!isAllowed()) {
            return
        }
        when (state) {
            NfcAdapter.STATE_ON -> {
                if (resumed) {
                    register("NFC adapter enabled")
                }
            }
            NfcAdapter.STATE_OFF ->
                Log.w(TAG, "NFC radio is off; it must be enabled in device settings")
        }
    }

    private fun register(reason: String) {
        if (!isAllowed()) {
            return
        }
        val adapter = adapterProvider()
        if (adapter == null) {
            Log.w(TAG, "NFC adapter unavailable ($reason)")
            return
        }
        val enabled = runCatching {
            adapter.isEnabled
        }
            .onFailure { Log.w(TAG, "Unable to query NFC state ($reason)", it) }
            .getOrDefault(false)
        if (!enabled) {
            Log.w(TAG, "NFC radio is not enabled ($reason)")
            return
        }

        val intent =
            Intent(activity, activity.javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
        val pendingIntent = PendingIntent.getActivity(activity, 0, intent, flags)

        runCatching {
            adapter.enableForegroundDispatch(activity, pendingIntent, null, null)
        }
            .onSuccess { Log.d(TAG, "NFC foreground dispatch registered ($reason)") }
            .onFailure {
                Log.w(
                    TAG,
                    "Unable to register NFC foreground dispatch ($reason)",
                    it,
                )
            }
    }

    private companion object {
        const val TAG = "NfcForegroundDispatch"
    }
}
