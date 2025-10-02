package uk.nktnet.webviewkiosk.handlers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import kotlin.math.max

@Composable
fun InactivityTimeoutHandler(
    systemSettings: SystemSettings,
    userSettings: UserSettings,
    customLoadUrl: (newUrl: String) -> Unit
) {
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var countdown by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(lastInteraction) {
        while (true) {
            delay(200L)
            val timeout = max(userSettings.resetOnInactivitySeconds, Constants.MIN_INACTIVITY_TIMEOUT_SECONDS) * 1000L
            val countdownSeconds = Constants.INACTIVITY_COUNTDOWN_SECONDS
            val countdownStart = timeout - countdownSeconds * 1000L
            val elapsed = System.currentTimeMillis() - lastInteraction

            if (timeout > 0 && elapsed >= countdownStart && countdown == null) {
                countdown = countdownSeconds
                while (countdown!! > 0) {
                    delay(1000L)
                    countdown = countdown!! - 1
                    if (System.currentTimeMillis() - lastInteraction < countdownStart) {
                        countdown = null
                        break
                    }
                }
                if (countdown != null) {
                    systemSettings.clearHistory()
                    customLoadUrl(userSettings.homeUrl)
                    lastInteraction = System.currentTimeMillis()
                    countdown = null
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInteropFilter {
                lastInteraction = System.currentTimeMillis()
                countdown = null
                false
            }
    ) {
        countdown?.let { remaining ->
            Box(
                Modifier
                    .fillMaxWidth(0.5f)
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(24.dp)
                    .clickable {
                        countdown = null
                        lastInteraction = System.currentTimeMillis()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Resetting in", fontSize = 16.sp, color = Color.White)
                    Text("$remaining", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Tap to cancel", fontSize = 16.sp, color = Color.White)
                }
            }
        }
    }
}
