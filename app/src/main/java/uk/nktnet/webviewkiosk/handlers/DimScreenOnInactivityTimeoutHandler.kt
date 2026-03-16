package uk.nktnet.webviewkiosk.handlers

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.states.UserInteractionStateSingleton
import uk.nktnet.webviewkiosk.utils.setWindowBrightness
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
            val elapsed = try {
                System.currentTimeMillis() - lastInteraction
            } catch (e: IllegalStateException) {
                Log.w(
                    Constants.APP_SCHEME,
                    "Failed to check last interaction time",
                    e
                )
                continue
            }
            if (elapsed >= timeoutDuration) {
                setWindowBrightness(context, 0)
                break
            }
        }
    }
}
