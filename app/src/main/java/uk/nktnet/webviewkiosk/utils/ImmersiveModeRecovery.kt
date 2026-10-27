package uk.nktnet.webviewkiosk.utils

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Repairs persistent bar reveals without depending on a focus/resume transition.
 * Ordinary transient overlays remain Android's responsibility: Android 11 can report
 * those bars as hidden even while drawing them above the app.
 */
internal class ImmersiveModeRecovery(
    private val windowView: View,
    private val canRecover: () -> Boolean,
    private val recover: () -> Unit,
    private val readInsets: () -> WindowInsetsCompat? = {
        ViewCompat.getRootWindowInsets(windowView)
    },
) {
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var pending = false
    private var attemptedForReveal = false
    private var layoutObserver: ViewTreeObserver? = null
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        checkVisibility()
    }
    private val restoreOnResume = Runnable { restoreIfReady() }
    private val recoverVisibleBars = Runnable {
        pending = false
        if (isReady() && hasPersistentBars(readInsets())) {
            // Set this first: hiding can synchronously trigger another layout.
            // Do not spin if a windowing policy refuses our request.
            attemptedForReveal = true
            recover()
        }
    }

    fun resume() {
        if (resumed) {
            return
        }
        resumed = true
        layoutObserver = windowView.viewTreeObserver
        layoutObserver?.addOnGlobalLayoutListener(layoutListener)
        // Activity.onResume can precede both RESUMED and window focus. Post for
        // the former; onWindowFocusChanged handles the latter independently.
        handler.post(restoreOnResume)
    }

    fun pause() {
        resumed = false
        handler.removeCallbacks(restoreOnResume)
        cancelRecovery()
        attemptedForReveal = false
        val observer = layoutObserver
        if (observer != null) {
            val liveObserver =
                observer.takeIf { it.isAlive } ?: windowView.viewTreeObserver
            liveObserver.removeOnGlobalLayoutListener(layoutListener)
        }
        layoutObserver = null
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            restoreIfReady()
        } else {
            handler.removeCallbacks(restoreOnResume)
            cancelRecovery()
            attemptedForReveal = false
        }
    }

    fun onUserInteraction() {
        // Do not consume input or hide normal swipe overlays on every tap.
        checkVisibility()
    }

    private fun restoreIfReady() {
        cancelRecovery()
        attemptedForReveal = false
        if (
            isReady() &&
                readInsets()?.isVisible(WindowInsetsCompat.Type.ime()) != true
        ) {
            recover()
        }
    }

    private fun isReady(): Boolean =
        resumed && windowView.hasWindowFocus() && canRecover()

    private fun checkVisibility() {
        if (!isReady() || !hasPersistentBars(readInsets())) {
            cancelRecovery()
            attemptedForReveal = false
            return
        }
        if (!pending && !attemptedForReveal) {
            pending = true
            // One bounded, event-driven retry, not a periodic fullscreen timer.
            // Repeated layouts/taps must not postpone this deadline forever.
            handler.postDelayed(recoverVisibleBars, recoveryDelayMillis())
        }
    }

    private fun hasPersistentBars(insets: WindowInsetsCompat?): Boolean =
        insets != null &&
            !insets.isVisible(WindowInsetsCompat.Type.ime()) &&
            (insets.isVisible(WindowInsetsCompat.Type.statusBars()) ||
                insets.isVisible(WindowInsetsCompat.Type.navigationBars()))

    private fun recoveryDelayMillis(): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val accessibility =
                windowView.context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                    as? AccessibilityManager
            return maxOf(
                REHIDE_DELAY_MILLIS,
                accessibility
                    ?.getRecommendedTimeoutMillis(
                        REHIDE_DELAY_MILLIS.toInt(),
                        AccessibilityManager.FLAG_CONTENT_CONTROLS,
                    )
                    ?.toLong() ?: REHIDE_DELAY_MILLIS,
            )
        }
        return REHIDE_DELAY_MILLIS
    }

    private fun cancelRecovery() {
        handler.removeCallbacks(recoverVisibleBars)
        pending = false
    }

    companion object {
        internal const val REHIDE_DELAY_MILLIS = 3000L
    }
}
