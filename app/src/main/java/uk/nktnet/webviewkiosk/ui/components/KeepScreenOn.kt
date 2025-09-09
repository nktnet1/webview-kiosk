package uk.nktnet.webviewkiosk.ui.components

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun KeepScreenOnOption(keepOn: Boolean) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(keepOn, lifecycleOwner) {
        val window = (view.context as? ComponentActivity)?.window

        fun setKeepScreenOn(enabled: Boolean) {
            if (enabled) {
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        setKeepScreenOn(keepOn)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> setKeepScreenOn(keepOn)
                Lifecycle.Event.ON_PAUSE -> setKeepScreenOn(false)
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            setKeepScreenOn(false)
        }
    }
}
