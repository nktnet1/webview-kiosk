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
    private val onScreenStateChanged: () -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF,
            Intent.ACTION_SCREEN_ON -> onScreenStateChanged()
        }
    }
}

/**
 * Sample host visibility and device interactivity for native GPS requests, including
 * screen events without Activity transitions.
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
    private var started = false
    // Unknown until attach samples lifecycle and current device interactivity.
    private var webViewPaused: Boolean? = null
    private var focusObserver: ViewTreeObserver? = null

    private val focusListener = ViewTreeObserver.OnWindowFocusChangeListener {
        // A focus change can expose a missing screen broadcast. Losing focus
        // to a dialog while interactive does not itself pause the WebView.
        updatePauseState()
    }

    private val receiver = ForegroundScreenStateReceiver { updatePauseState() }

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
        // The host may have changed state while this controller was detached.
        started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        attached = true
        focusObserver = windowView.viewTreeObserver
        focusObserver?.addOnWindowFocusChangeListener(focusListener)
        lifecycle.addObserver(this)
        updatePauseState()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_PAUSE,
            Lifecycle.Event.ON_RESUME -> updatePauseState()
            Lifecycle.Event.ON_START -> {
                started = true
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
        // Screen broadcasts can arrive after another power transition. Sample
        // current interactivity on every signal instead of retaining a stale flag.
        val shouldPause = !started || !power.isInteractive
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
