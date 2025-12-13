package com.nktnet.webview_kiosk.handlers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.states.UserInteractionStateSingleton
import kotlin.math.max

const val RESET_TIMEOUT_INT = -1

@Composable
fun ResetOnInactivityTimeoutHandler(
    customLoadUrl: (newUrl: String) -> Unit
) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    val timeoutDuration = max(
        userSettings.resetOnInactivitySeconds,
        Constants.MIN_INACTIVITY_TIMEOUT_SECONDS
    ) * 1000L

    val countdownStartDuration = timeoutDuration - Constants.INACTIVITY_COUNTDOWN_SECONDS * 1000L
    var countdown by remember { mutableIntStateOf(RESET_TIMEOUT_INT) }

    val lastInteraction by UserInteractionStateSingleton.lastInteractionState

    val handleTimeoutReached = {
        systemSettings.clearHistory()
        customLoadUrl(userSettings.homeUrl)
        UserInteractionStateSingleton.onUserInteraction()
    }

    LaunchedEffect(lastInteraction) {
        countdown = RESET_TIMEOUT_INT
        while (true) {
            delay(500L)
            val elapsed = System.currentTimeMillis() - lastInteraction
            if (elapsed >= countdownStartDuration) {
                countdown = Constants.INACTIVITY_COUNTDOWN_SECONDS
                while (countdown > 0) {
                    delay(1000L)
                    countdown--
                }
                handleTimeoutReached()
                break
            }
        }
    }

    if (countdown > 0) {
        Box(
            Modifier.fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier=  Modifier
                    .fillMaxWidth(0.5f)
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Resetting in", fontSize = 16.sp, color = Color.White)
                    Text(
                        "$countdown",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text("Tap to cancel", fontSize = 16.sp, color = Color.White)
                }
            }
        }
    }
}
