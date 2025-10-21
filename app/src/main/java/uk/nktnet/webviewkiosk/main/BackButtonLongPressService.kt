package uk.nktnet.webviewkiosk.main

import android.annotation.SuppressLint
import android.view.KeyEvent
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.states.BackButtonStateSingleton

class BackButtonLongPressService(
    private val lifecycleScope: LifecycleCoroutineScope
) {
    private var backButtonJob: Job? = null
    private var isLongPressHandled = false

    @SuppressLint("GestureBackNavigation")
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (backButtonJob == null) {
                backButtonJob = lifecycleScope.launch {
                    delay(Constants.BACK_BUTTON_LONG_PRESS_THRESHOLD)
                    isLongPressHandled = true
                    BackButtonStateSingleton.emitLongPress()
                }
            }
        }
        return false
    }

    @SuppressLint("GestureBackNavigation")
    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            backButtonJob?.cancel()
            backButtonJob = null
            if (isLongPressHandled) {
                isLongPressHandled = false
                return true
            }
        }
        return false
    }
}