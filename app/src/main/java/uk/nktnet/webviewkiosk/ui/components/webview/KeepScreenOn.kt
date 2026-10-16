package uk.nktnet.webviewkiosk.ui.components.webview

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import uk.nktnet.webviewkiosk.states.KeepScreenOnStateSingleton

@Composable
fun KeepScreenOnOption() {
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keepOn = KeepScreenOnStateSingleton.isKeepScreenOn.value

    DisposableEffect(keepOn, lifecycleOwner, activity) {
        val controller =
            KeepScreenOnWindowController(
                activity?.window,
                lifecycleOwner.lifecycle,
                keepOn,
            )
        controller.attach()
        onDispose { controller.detach() }
    }
}

/**
 * Window flags must follow the visible activity, not just the current Compose
 * setting.
 */
internal class KeepScreenOnWindowController(
    private val window: Window?,
    private val lifecycle: Lifecycle,
    private val keepOn: Boolean,
) : LifecycleEventObserver {
    private val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

    fun attach() {
        lifecycle.addObserver(this)
        syncWindowFlag()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        syncWindowFlag()
    }

    private fun syncWindowFlag() {
        if (keepOn && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            window?.addFlags(flag)
        } else {
            window?.clearFlags(flag)
        }
    }

    fun detach() {
        lifecycle.removeObserver(this)
        window?.clearFlags(flag)
    }
}
