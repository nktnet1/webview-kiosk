package uk.nktnet.webviewkiosk.handlers

import android.webkit.WebView
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import uk.nktnet.webviewkiosk.config.UserSettings
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.auth.BiometricPromptManager
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.option.KioskControlPanelOption
import uk.nktnet.webviewkiosk.states.BackButtonStateSingleton
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.utils.tryLockTask
import uk.nktnet.webviewkiosk.utils.tryUnlockTask
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation

@Composable
fun KioskControlPanel(
    requiredTaps: Int,
    webView: WebView,
    customLoadUrl: (newUrl: String) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }
    val isLocked by LockStateSingleton.isLocked
    val biometricResult by BiometricPromptManager.promptResults.collectAsState(initial = BiometricPromptManager.BiometricResult.Loading)

    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width.toFloat()
    val screenHeightPx = windowInfo.containerSize.height.toFloat()

    var tapsLeft by remember { mutableIntStateOf(requiredTaps) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val maxInterval = 300L

    var enableDismiss by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val toastRef = remember { mutableStateOf<android.widget.Toast?>(null) }
    fun showToast(message: String) {
        toastRef.value?.cancel()
        toastRef.value = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).also { it.show() }
    }

    var showDialog by remember { mutableStateOf(false) }
    var isSticky by remember { mutableStateOf(systemSettings.isKioskControlPanelSticky) }

    LaunchedEffect(tapsLeft, lastTapTime) {
        if (tapsLeft in 1..5) {
            delay(maxInterval)
            if (System.currentTimeMillis() - lastTapTime >= maxInterval) {
                tapsLeft = requiredTaps
            }
        }
    }

    val handleShowDialog = {
        showDialog = true
        enableDismiss = false
        scope.launch {
            delay(1000L)
            enableDismiss = true
        }
    }

    LaunchedEffect(Unit) {
        BackButtonStateSingleton.longPressEvents.collect {
            handleShowDialog()
        }
    }

    if (userSettings.allowKioskControlPanel != KioskControlPanelOption.DISABLED) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInteropFilter { motionEvent ->
                    if (motionEvent.action == android.view.MotionEvent.ACTION_DOWN) {
                        val now = System.currentTimeMillis()

                        val inRegion = when (userSettings.allowKioskControlPanel) {
                            KioskControlPanelOption.TOP_LEFT -> motionEvent.x < screenWidthPx / 2f && motionEvent.y < screenHeightPx / 2f
                            KioskControlPanelOption.TOP_RIGHT -> motionEvent.x >= screenWidthPx / 2f && motionEvent.y < screenHeightPx / 2f
                            KioskControlPanelOption.BOTTOM_LEFT -> motionEvent.x < screenWidthPx / 2f && motionEvent.y >= screenHeightPx / 2f
                            KioskControlPanelOption.BOTTOM_RIGHT -> motionEvent.x >= screenWidthPx / 2f && motionEvent.y >= screenHeightPx / 2f
                            KioskControlPanelOption.TOP -> motionEvent.y < screenHeightPx / 2f
                            KioskControlPanelOption.BOTTOM -> motionEvent.y >= screenHeightPx / 2f
                            KioskControlPanelOption.FULL -> true
                            KioskControlPanelOption.DISABLED -> false
                        }

                        if (inRegion) {
                            if (now - lastTapTime > maxInterval) {
                                tapsLeft = requiredTaps
                            }
                            tapsLeft = max(0, tapsLeft - 1)
                            lastTapTime = now
                            when {
                                tapsLeft <= 0 -> {
                                    tapsLeft = requiredTaps
                                    toastRef.value?.cancel()
                                    handleShowDialog()
                                }
                                tapsLeft <= 5 -> {
                                    showToast(
                                        "Tap $tapsLeft more times to open the Kiosk Control Panel"
                                    )
                                }
                            }
                        }
                    }
                    false
                }
        ) {
            if (tapsLeft in 1..5) {
                val (boxWidth, boxHeight, boxAlignment) = when (userSettings.allowKioskControlPanel) {
                    KioskControlPanelOption.TOP_LEFT -> Triple(0.5f, 0.5f, Alignment.TopStart)
                    KioskControlPanelOption.TOP_RIGHT -> Triple(0.5f, 0.5f, Alignment.TopEnd)
                    KioskControlPanelOption.BOTTOM_LEFT -> Triple(0.5f, 0.5f, Alignment.BottomStart)
                    KioskControlPanelOption.BOTTOM_RIGHT -> Triple(0.5f, 0.5f, Alignment.BottomEnd)
                    KioskControlPanelOption.TOP -> Triple(1f, 0.5f, Alignment.TopCenter)
                    KioskControlPanelOption.BOTTOM -> Triple(1f, 0.5f, Alignment.BottomCenter)
                    KioskControlPanelOption.FULL -> Triple(1f, 1f, Alignment.Center)
                    KioskControlPanelOption.DISABLED -> Triple(0f, 0f, Alignment.TopStart)
                }
                Box(
                    Modifier
                        .fillMaxWidth(boxWidth)
                        .fillMaxHeight(boxHeight)
                        .align(boxAlignment)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        .border(2.dp, MaterialTheme.colorScheme.primary)
                )
            }
        }
    }

    if (showDialog) {
        Dialog(
            onDismissRequest = { showDialog = false },
            properties = DialogProperties(
                dismissOnClickOutside = enableDismiss,
                dismissOnBackPress = enableDismiss,
            )
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row (
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Kiosk Control Panel",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(
                            modifier = Modifier.offset(y = (-2).dp),
                            onClick = {
                                systemSettings.isKioskControlPanelSticky = !isSticky
                                isSticky = !isSticky
                                showToast("Sticky kiosk panel ${if (isSticky) "enabled." else "disabled."}")
                            },
                        ) {
                            Icon(
                                painter = if (isSticky) painterResource(R.drawable.custom_pin) else painterResource(R.drawable.custom_unpin),
                                contentDescription = if (isSticky) "Sticky" else "Non-Sticky",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    if (userSettings.allowBackwardsNavigation) {
                        Button(
                            onClick = {
                                WebViewNavigation.goBack(customLoadUrl, systemSettings)
                                showDialog = isSticky
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_arrow_back_24),
                                contentDescription = "Back",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Back")
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                WebViewNavigation.goForward(customLoadUrl, systemSettings)
                                showDialog = isSticky
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_arrow_forward_24),
                                contentDescription = "Forward",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Forward")
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                    }

                    if (userSettings.allowGoHome) {
                        Button(
                            onClick = {
                                WebViewNavigation.goHome(customLoadUrl, systemSettings, userSettings)
                                showDialog = isSticky
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_home_24),
                                contentDescription = "Home",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Home")
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                    }

                    if (userSettings.allowRefresh) {
                        Button(
                            onClick = {
                                webView.reload()
                                showDialog = isSticky
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_refresh_24),
                                contentDescription = "Refresh",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh")
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                    }

                    if (isLocked) {
                        var waitingForUnlock by remember { mutableStateOf(false) }
                        LaunchedEffect(biometricResult, waitingForUnlock) {
                            if (waitingForUnlock) {
                                if (
                                    biometricResult == BiometricPromptManager.BiometricResult.AuthenticationSuccess
                                    || biometricResult == BiometricPromptManager.BiometricResult.AuthenticationNotSet
                                ) {
                                    val res = tryUnlockTask(activity, ::showToast)
                                    if (res) {
                                        showDialog = isSticky
                                    }
                                    waitingForUnlock = false
                                }
                            }
                        }

                        Button(
                            onClick = {
                                waitingForUnlock = true
                                BiometricPromptManager.showBiometricPrompt(
                                    title = "Authentication Required",
                                    description = "Please authenticate to access settings"
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_lock_open_24),
                                contentDescription = "Unlock",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock")
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                    } else {
                        Button(
                            onClick = {
                                tryLockTask(activity, ::showToast)
                                showDialog = isSticky
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_lock_24),
                                contentDescription = "Lock",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lock")
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        TextButton(
                            onClick = { showDialog = false },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                        ) {
                            Text(
                                text = "Close",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
