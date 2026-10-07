package uk.nktnet.webviewkiosk.handlers

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.states.UserInteractionStateSingleton
import uk.nktnet.webviewkiosk.utils.setWindowBrightness
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun DimScreenOnInactivityTimeoutHandler(canHandleEvents: () -> Boolean) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val currentCanHandleEvents by rememberUpdatedState(canHandleEvents)

    val timeoutDuration = max(
        userSettings.dimScreenOnInactivitySeconds,
        Constants.MIN_INACTIVITY_TIMEOUT_SECONDS
    ) * 1000L

    val lastInteraction by UserInteractionStateSingleton.lastInteractionState.collectAsState()

    LaunchedEffect(lastInteraction) {
        if (currentCanHandleEvents()) {
            setWindowBrightness(context, userSettings.brightness)
        }
        while (true) {
            delay(500.milliseconds)
            val elapsed = try {
                System.currentTimeMillis() - lastInteraction
            } catch (e: IllegalStateException) {
                Log.w(
                    Constants.APP_SCHEME,
                    "Failed to check last interaction time for dimming screen",
                    e
                )
                continue
            }
            if (elapsed >= timeoutDuration) {
                if (currentCanHandleEvents()) {
                    setWindowBrightness(context, 0)
                }
                break
            }
        }
    }
}
