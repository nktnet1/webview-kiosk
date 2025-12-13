package com.nktnet.webview_kiosk.managers

import android.os.Build
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nktnet.webview_kiosk.states.BackButtonStateSingleton

class BackButtonManager(
    private val lifecycleScope: LifecycleCoroutineScope,
) {
    private var backLongPressJob: Job? = null
    private var isLegacyLongPressEmitted = false

    val onBackPressedCallback = object : OnBackPressedCallback(false) {
        private fun isButtonPress(backEvent: BackEventCompat): Boolean {
            return (
                (backEvent.touchX == 0.0f && backEvent.touchY == 0.0f)
                || (backEvent.touchX.isNaN() && backEvent.touchY.isNaN())
            )
        }

        override fun handleOnBackPressed() {
            // This is generic for all devices and models
            lifecycleScope.launch {
                BackButtonStateSingleton.emitShortPress()
            }
            if (shouldUsePredictiveBackLongPress()) {
                cancelBackgroundJob()
            }
        }

        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            if (shouldUsePredictiveBackLongPress() && isButtonPress(backEvent)) {
                backLongPressJob = lifecycleScope.launch {
                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                    BackButtonStateSingleton.emitLongPress()
                }
            }
            return super.handleOnBackStarted(backEvent)
        }

        override fun handleOnBackCancelled() {
            if (shouldUsePredictiveBackLongPress()) {
                cancelBackgroundJob()
            }
        }
    }

    private fun shouldUseCustomBackLongPress(): Boolean {
        return (
            Build.MANUFACTURER.equals("Huawei", ignoreCase = true)
            || Build.BRAND.equals("Huawei", ignoreCase = true)
        )
    }

    private fun shouldUsePredictiveBackLongPress(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }

    fun onKeyDown(keyCode: Int): Boolean {
        if (
            keyCode == KeyEvent.KEYCODE_BACK
            && shouldUseCustomBackLongPress()
            && !shouldUsePredictiveBackLongPress()
        ) {
            if (backLongPressJob == null) {
                backLongPressJob = lifecycleScope.launch {
                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                    BackButtonStateSingleton.emitLongPress()
                    isLegacyLongPressEmitted = true
                }
            }
            return true
        }
        return false
    }

    fun onKeyUp(keyCode: Int): Boolean {
        if (
            keyCode == KeyEvent.KEYCODE_BACK
            && shouldUseCustomBackLongPress()
            && !shouldUsePredictiveBackLongPress()
        ) {
            cancelBackgroundJob()
            if (isLegacyLongPressEmitted) {
                isLegacyLongPressEmitted = false
                return true
            }
        }
        return false
    }

    fun onKeyLongPress(keyCode: Int): Boolean {
        if (
            keyCode == KeyEvent.KEYCODE_BACK
            && !shouldUseCustomBackLongPress()
            && !shouldUsePredictiveBackLongPress()
        ) {
            lifecycleScope.launch {
                BackButtonStateSingleton.emitLongPress()
            }
            return true
        }
        return false
    }

    private fun cancelBackgroundJob() {
        backLongPressJob?.cancel()
        backLongPressJob = null
    }
}
