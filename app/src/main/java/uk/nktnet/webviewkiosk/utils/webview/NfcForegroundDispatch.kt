package uk.nktnet.webviewkiosk.utils.webview

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    private var dispatchRegistered = false
    private var pendingWakeRetry = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wakeRetry = Runnable {
        if (resumed) {
            pendingWakeRetry = false
            register("delayed screen wake")
        }
    }

    fun onResume() {
        resumed = true
        register("activity resumed")
        // SCREEN_ON may have arrived while the activity was paused. Retain its
        // bounded retry until the foreground activity can register dispatch.
        if (pendingWakeRetry) {
            scheduleWakeRetry()
        }
    }

    fun onPause() {
        resumed = false
        mainHandler.removeCallbacks(wakeRetry)
        dispatchRegistered = false
        val adapter = adapterProvider() ?: return
        runCatching { adapter.disableForegroundDispatch(activity) }
            .onFailure {
                Log.w(TAG, "Failed to disable NFC foreground dispatch", it)
            }
    }

    fun onScreenOn() {
        pendingWakeRetry = true
        if (resumed) {
            register("screen on")
            // The NFC service may finish waking after SCREEN_ON. Make one more
            // attempt without polling, resetting the radio, or keeping the activity
            // alive.
            scheduleWakeRetry()
        }
    }

    private fun scheduleWakeRetry() {
        mainHandler.removeCallbacks(wakeRetry)
        mainHandler.postDelayed(wakeRetry, WAKE_RETRY_DELAY_MS)
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

        // Android registers an onPause listener on every enable call. Replace the
        // existing registration before recovering it, or repeated screen wakes
        // will accumulate listeners that disable dispatch on the next pause.
        if (dispatchRegistered) {
            runCatching { adapter.disableForegroundDispatch(activity) }
                .onFailure {
                    Log.w(
                        TAG,
                        "Failed to replace NFC foreground dispatch ($reason)",
                        it,
                    )
                }
            dispatchRegistered = false
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
            .onSuccess {
                dispatchRegistered = true
                Log.d(TAG, "NFC foreground dispatch registered ($reason)")
            }
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
        const val WAKE_RETRY_DELAY_MS = 1_500L
    }
}
