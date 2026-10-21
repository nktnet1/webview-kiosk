package uk.nktnet.webviewkiosk.ui.components.webview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** One wake belongs to the WebView that was in the foreground before sleep. */
internal class ScreenWakeRefreshController(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val windowView: View,
    private val canRefresh: () -> Boolean,
    private val refresh: () -> Unit,
) : LifecycleEventObserver {
    private val power =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var attached = false
    private var resumed = false
    private var screenOff = !power.isInteractive
    private var eligibleAtSleep = false
    private var pendingWake = false
    private var focusObserver: ViewTreeObserver? = null

    private val focusListener =
        ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                refreshIfReady()
            }
        }

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!attached) {
                    return
                }
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        // onPause may precede this broadcast. Retain eligibility
                        // only if that pause happened after the device slept.
                        eligibleAtSleep =
                            (resumed || eligibleAtSleep) && canRefresh()
                        screenOff = true
                        pendingWake = false
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        if (!screenOff) {
                            return
                        }
                        screenOff = false
                        pendingWake = eligibleAtSleep && canRefresh()
                        eligibleAtSleep = false
                        refreshIfReady()
                    }
                }
            }
        }

    fun attach() {
        if (attached || lifecycle.currentState == Lifecycle.State.DESTROYED) {
            return
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        attached = true
        focusObserver = windowView.viewTreeObserver
        focusObserver?.addOnWindowFocusChangeListener(focusListener)
        lifecycle.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                resumed = true
                refreshIfReady()
            }
            Lifecycle.Event.ON_PAUSE -> {
                if (!power.isInteractive) {
                    eligibleAtSleep = (resumed || eligibleAtSleep) && canRefresh()
                } else {
                    // An ordinary app switch or settings navigation must not
                    // retain a wake to replay on a later return.
                    eligibleAtSleep = false
                    pendingWake = false
                }
                resumed = false
            }
            Lifecycle.Event.ON_DESTROY -> detach()
            else -> Unit
        }
    }

    private fun refreshIfReady() {
        if (
            !attached ||
                !pendingWake ||
                screenOff ||
                !power.isInteractive ||
                !resumed ||
                !windowView.hasWindowFocus()
        ) {
            return
        }
        // Consume before calling into navigation, which can dispose this owner.
        pendingWake = false
        if (canRefresh()) {
            refresh()
        }
    }

    fun detach() {
        if (!attached) {
            return
        }
        attached = false
        pendingWake = false
        eligibleAtSleep = false
        resumed = false
        lifecycle.removeObserver(this)
        val observer =
            focusObserver?.takeIf { it.isAlive } ?: windowView.viewTreeObserver
        observer.removeOnWindowFocusChangeListener(focusListener)
        focusObserver = null
        context.unregisterReceiver(receiver)
    }
}
