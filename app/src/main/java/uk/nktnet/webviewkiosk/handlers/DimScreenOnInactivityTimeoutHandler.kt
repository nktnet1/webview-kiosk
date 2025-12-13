package com.nktnet.webview_kiosk.handlers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.states.UserInteractionStateSingleton
import com.nktnet.webview_kiosk.utils.setWindowBrightness
import kotlin.math.max

@Composable
fun DimScreenOnInactivityTimeoutHandler() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    val timeoutDuration = max(
        userSettings.dimScreenOnInactivitySeconds,
        Constants.MIN_INACTIVITY_TIMEOUT_SECONDS
    ) * 1000L

    val lastInteraction by UserInteractionStateSingleton.lastInteractionState

    LaunchedEffect(lastInteraction) {
        setWindowBrightness(context, userSettings.brightness)
        while (true) {
            delay(500L)
            val elapsed = System.currentTimeMillis() - lastInteraction
            if (elapsed >= timeoutDuration) {
                setWindowBrightness(context, 0)
                break
            }
        }
    }
}
