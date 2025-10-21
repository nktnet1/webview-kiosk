package uk.nktnet.webviewkiosk.main

import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.states.BackButtonStateSingleton

class BackButtonLongPressService(
    private val lifecycleScope: LifecycleCoroutineScope,
    fragmentManager: FragmentManager,
    onBackPressedDispatcher: OnBackPressedDispatcher,
) {
    private var backLongPressJob: Job? = null
    private var isLongPressHandled = false

    val onBackPressedCallback = object : UserInteractionOnBackPressedCallback(
        fragmentManager = fragmentManager,
        dispatcher = onBackPressedDispatcher,
    ) {
        override fun handleOnBackPressed() {
            Log.d("[handleOnBackPressed]", "[handleOnBackPressed]")
            if (shouldUsePredictiveBackLongPress()) {
                cancelBackgroundJob()
            }
            super.handleOnBackPressed()
        }

        private fun isButtonPress(backEvent: BackEventCompat): Boolean {
            Log.d("[isButtonPress]", "[isButtonPress]")
            return (
                (backEvent.touchX == 0.0f && backEvent.touchY == 0.0f)
                || (backEvent.touchX.isNaN() && backEvent.touchY.isNaN())
            )
        }

        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            Log.d("[handleOnBackStarted]", "[handleOnBackStarted]")
            if (shouldUsePredictiveBackLongPress() && isButtonPress(backEvent)) {
                backLongPressJob = lifecycleScope.launch {
                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                    BackButtonStateSingleton.emitLongPress()
                }
            }
        }

        override fun handleOnBackCancelled() {
            Log.d("[handleOnBackCancelled]", "[handleOnBackCancelled]")
            if (shouldUsePredictiveBackLongPress()) {
                cancelBackgroundJob()
            }
        }
    }

    private fun shouldUseCustomBackLongPress(): Boolean {
        return Build.MANUFACTURER.equals("Huawei", ignoreCase = true)
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
                    delay(Constants.BACK_BUTTON_LONG_PRESS_THRESHOLD)
                    isLongPressHandled = true
                    BackButtonStateSingleton.emitLongPress()
                }
            }
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
            if (isLongPressHandled) {
                isLongPressHandled = false
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
            lifecycleScope.launch { BackButtonStateSingleton.emitLongPress() }
            return true
        }
        return false
    }

    private fun cancelBackgroundJob() {
        backLongPressJob?.cancel()
        backLongPressJob = null
    }
}
