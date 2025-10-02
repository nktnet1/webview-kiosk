package uk.nktnet.webviewkiosk.handlers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import uk.nktnet.webviewkiosk.config.UserSettings
import kotlin.math.max
import kotlinx.coroutines.delay

@Composable
fun MultitapHandler(
    requiredTaps: Int = 10,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width.toFloat()
    val screenHeightPx = windowInfo.containerSize.height.toFloat()

    var tapsLeft by remember { mutableIntStateOf(requiredTaps) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val maxInterval = 300L

    val toastRef = remember { mutableStateOf<android.widget.Toast?>(null) }

    fun showToast(message: String) {
        toastRef.value?.cancel()
        toastRef.value = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).also { it.show() }
    }

    val userSettings = remember { UserSettings(context) }

    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(tapsLeft, lastTapTime) {
        if (tapsLeft in 1..5) {
            delay(maxInterval)
            if (System.currentTimeMillis() - lastTapTime >= maxInterval) {
                tapsLeft = requiredTaps
            }
        }
    }

    if (userSettings.allowGoHome) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInteropFilter { motionEvent ->
                    if (motionEvent.action == android.view.MotionEvent.ACTION_DOWN) {
                        val now = System.currentTimeMillis()
                        if (motionEvent.x < screenWidthPx / 2f && motionEvent.y < screenHeightPx / 2f) {
                            if (now - lastTapTime > maxInterval) {
                                tapsLeft = requiredTaps
                            }
                            tapsLeft = max(0, tapsLeft - 1)
                            lastTapTime = now
                            when {
                                tapsLeft <= 0 -> {
                                    tapsLeft = requiredTaps
                                    toastRef.value?.cancel()
                                    showConfirmDialog = true
                                }
                                tapsLeft <= 5 -> {
                                    showToast("Tap $tapsLeft more times to navigate home")
                                }
                            }
                        }
                    }
                    false
                }
        ) {
            if (tapsLeft in 1..5) {
                Box(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .fillMaxHeight(0.5f)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        .border(2.dp, MaterialTheme.colorScheme.primary)
                )
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text("Return Home?")
            },
            text = { Text("""
                Do you wish to return to the home page?
                - ${userSettings.homeUrl}              
                """.trimIndent())
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onSuccess()
                }) {
                    Text("Go Home")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Cancel")
                }
            },
            properties = DialogProperties(
                dismissOnClickOutside = false,
            )
        )
    }
}
