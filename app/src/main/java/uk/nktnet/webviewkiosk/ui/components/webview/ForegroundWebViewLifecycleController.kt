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

private class ForegroundScreenStateReceiver(
    private val onScreenStateChanged: (Boolean) -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> onScreenStateChanged(true)
            Intent.ACTION_SCREEN_ON -> onScreenStateChanged(false)
        }
    }
}

/**
 * Keep the foreground WebView's native pause state in sync with both the host
 * lifecycle and display sleep. Some automotive devices turn the display off without
 * stopping the Activity, but WebView.onPause() is what suspends geolocation in
 * Chromium.
 */
internal class ForegroundWebViewLifecycleController(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val windowView: View,
    private val pause: () -> Unit,
    private val resume: () -> Unit,
) : LifecycleEventObserver {
    private val power =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var attached = false
    private var started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    private var screenOff = !power.isInteractive
    // Unknown on attach because the WebView may already have been paused.
    private var webViewPaused: Boolean? = null
    private var focusObserver: ViewTreeObserver? = null

    private val focusListener =
        ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            // Recover when a head unit omits screen broadcasts but changes
            // power state and focus while suspending or restoring the display.
            if (!hasFocus && !power.isInteractive) {
                screenOff = true
                updatePauseState()
            } else if (hasFocus && power.isInteractive) {
                screenOff = false
                updatePauseState()
            }
        }

    private val receiver = ForegroundScreenStateReceiver { off ->
        if (attached) {
            screenOff = off
            updatePauseState()
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
        updatePauseState()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_PAUSE -> {
                // The screen can sleep while this Activity remains STARTED.
                if (!power.isInteractive) {
                    screenOff = true
                    updatePauseState()
                }
            }
            Lifecycle.Event.ON_RESUME -> {
                if (power.isInteractive) {
                    screenOff = false
                    updatePauseState()
                }
            }
            Lifecycle.Event.ON_START -> {
                started = true
                // Handles a wake where the vendor did not send SCREEN_ON.
                if (power.isInteractive) {
                    screenOff = false
                }
                updatePauseState()
            }
            Lifecycle.Event.ON_STOP -> {
                started = false
                updatePauseState()
            }
            Lifecycle.Event.ON_DESTROY -> detach()
            else -> Unit
        }
    }

    private fun updatePauseState() {
        if (!attached) {
            return
        }
        val shouldPause = !started || screenOff
        if (webViewPaused != shouldPause) {
            webViewPaused = shouldPause
            if (shouldPause) {
                pause()
            } else {
                resume()
            }
        }
    }

    fun detach() {
        if (!attached) {
            return
        }
        attached = false
        lifecycle.removeObserver(this)
        val observer =
            focusObserver?.takeIf { it.isAlive } ?: windowView.viewTreeObserver
        observer.removeOnWindowFocusChangeListener(focusListener)
        focusObserver = null
        context.unregisterReceiver(receiver)
        webViewPaused = null
    }
}
