package uk.nktnet.webviewkiosk.handlers

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.option.BackButtonHoldActionOption
import uk.nktnet.webviewkiosk.config.option.KioskControlPanelRegionOption
import uk.nktnet.webviewkiosk.states.BackButtonStateSingleton
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.states.WaitingForUnlockStateSingleton
import uk.nktnet.webviewkiosk.ui.components.webview.BookmarksDialog
import uk.nktnet.webviewkiosk.ui.components.webview.HistoryDialog
import uk.nktnet.webviewkiosk.ui.components.webview.LocalFilesDialog
import uk.nktnet.webviewkiosk.utils.tryLockTask
import uk.nktnet.webviewkiosk.utils.unlockWithAuthIfRequired
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation

@Composable
fun KioskControlPanel(
    requiredTaps: Int,
    customLoadUrl: (newUrl: String) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }
    val isLocked by LockStateSingleton.isLocked

    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width.toFloat()
    val screenHeightPx = windowInfo.containerSize.height.toFloat()

    var tapsLeft by remember { mutableIntStateOf(requiredTaps) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val maxInterval = 300L

    val scope = rememberCoroutineScope()
    var enableDismiss by remember { mutableStateOf(false) }
    var enableInteraction by remember { mutableStateOf(true) }

    val toastRef = remember { mutableStateOf<Toast?>(null) }
    fun showToast(message: String) {
        toastRef.value?.cancel()
        toastRef.value = Toast.makeText(context, message, Toast.LENGTH_SHORT).also { it.show() }
    }

    var showDialog by remember { mutableStateOf(false) }
    var isSticky by remember { mutableStateOf(systemSettings.isKioskControlPanelSticky) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showLocalFilesDialog by remember { mutableStateOf(false) }

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
        if (userSettings.backButtonHoldAction == BackButtonHoldActionOption.OPEN_KIOSK_CONTROL_PANEL) {
            BackButtonStateSingleton.longPressEvents.collect {
                handleShowDialog()
            }
        }
    }

    LaunchedEffect(Unit) {
        WaitingForUnlockStateSingleton.unlockSuccess.collect {
            if (showDialog) {
                showDialog = isSticky
            }
        }
    }

    if (showHistoryDialog) {
        HistoryDialog(customLoadUrl, onDismiss = { showHistoryDialog = false })
    }

    if (showBookmarksDialog) {
        BookmarksDialog(customLoadUrl, onDismiss = { showBookmarksDialog = false })
    }

    if (showLocalFilesDialog) {
        LocalFilesDialog(
            onDismiss = { showLocalFilesDialog = false },
            customLoadUrl = customLoadUrl
        )
    }

    if (userSettings.kioskControlPanelRegion != KioskControlPanelRegionOption.DISABLED) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInteropFilter { motionEvent ->
                    if (motionEvent.action == android.view.MotionEvent.ACTION_DOWN) {
                        val now = System.currentTimeMillis()

                        val inRegion = when (userSettings.kioskControlPanelRegion) {
                            KioskControlPanelRegionOption.TOP_LEFT -> motionEvent.x < screenWidthPx / 2f && motionEvent.y < screenHeightPx / 2f
                            KioskControlPanelRegionOption.TOP_RIGHT -> motionEvent.x >= screenWidthPx / 2f && motionEvent.y < screenHeightPx / 2f
                            KioskControlPanelRegionOption.BOTTOM_LEFT -> motionEvent.x < screenWidthPx / 2f && motionEvent.y >= screenHeightPx / 2f
                            KioskControlPanelRegionOption.BOTTOM_RIGHT -> motionEvent.x >= screenWidthPx / 2f && motionEvent.y >= screenHeightPx / 2f
                            KioskControlPanelRegionOption.TOP -> motionEvent.y < screenHeightPx / 2f
                            KioskControlPanelRegionOption.BOTTOM -> motionEvent.y >= screenHeightPx / 2f
                            KioskControlPanelRegionOption.FULL -> true
                            KioskControlPanelRegionOption.DISABLED -> false
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
                                    enableInteraction = false
                                    handleShowDialog()
                                    scope.launch {
                                        delay(600L)
                                        enableInteraction = true
                                    }
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
                val (boxWidth, boxHeight, boxAlignment) = when (userSettings.kioskControlPanelRegion) {
                    KioskControlPanelRegionOption.TOP_LEFT -> Triple(0.5f, 0.5f, Alignment.TopStart)
                    KioskControlPanelRegionOption.TOP_RIGHT -> Triple(0.5f, 0.5f, Alignment.TopEnd)
                    KioskControlPanelRegionOption.BOTTOM_LEFT -> Triple(0.5f, 0.5f, Alignment.BottomStart)
                    KioskControlPanelRegionOption.BOTTOM_RIGHT -> Triple(0.5f, 0.5f, Alignment.BottomEnd)
                    KioskControlPanelRegionOption.TOP -> Triple(1f, 0.5f, Alignment.TopCenter)
                    KioskControlPanelRegionOption.BOTTOM -> Triple(1f, 0.5f, Alignment.BottomCenter)
                    KioskControlPanelRegionOption.FULL -> Triple(1f, 1f, Alignment.Center)
                    KioskControlPanelRegionOption.DISABLED -> Triple(0f, 0f, Alignment.TopStart)
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
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(scrollState)
                ) {
                    Row (
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Kiosk Control Panel",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (enableInteraction) 1f else 0.5f
                            )
                        )
                        IconButton(
                            enabled = enableInteraction,
                            modifier = Modifier.offset(y = (-2).dp),
                            onClick = {
                                systemSettings.isKioskControlPanelSticky = !isSticky
                                isSticky = !isSticky
                                showToast("Sticky mode ${if (isSticky) "enabled." else "disabled."}")
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                enabled = enableInteraction,
                                onClick = {
                                    WebViewNavigation.goBack(customLoadUrl, systemSettings)
                                    showDialog = isSticky
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_arrow_back_24),
                                    contentDescription = "Back",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Back",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            Button(
                                enabled = enableInteraction,
                                onClick = {
                                    WebViewNavigation.goForward(customLoadUrl, systemSettings)
                                    showDialog = isSticky
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_arrow_forward_24),
                                    contentDescription = "Forward",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Forward",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                    }

                    if (userSettings.allowGoHome) {
                        Button(
                            enabled = enableInteraction,
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
                            enabled = enableInteraction,
                            onClick = {
                                WebViewNavigation.refresh(customLoadUrl, systemSettings, userSettings)
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

                    if (userSettings.allowHistoryAccess) {
                        Button(
                            enabled = enableInteraction,
                            onClick = {
                                showDialog = isSticky
                                showHistoryDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_history_24),
                                contentDescription = "History",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("History")
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                    }

                    if (userSettings.allowBookmarkAccess) {
                        Button(
                            enabled = enableInteraction,
                            onClick = {
                                showDialog = isSticky
                                showBookmarksDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_bookmark_24),
                                contentDescription = "Bookmark",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bookmark")
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                    }

                    if (userSettings.allowLocalFiles) {
                        Button(
                            enabled = enableInteraction,
                            onClick = {
                                showDialog = isSticky
                                showLocalFilesDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_folder_24),
                                contentDescription = "Files",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Files")
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                    }

                    if (isLocked) {
                        Button(
                            enabled = enableInteraction,
                            onClick = {
                                activity?.let {
                                    unlockWithAuthIfRequired(activity, ::showToast)
                                }
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
                            enabled = enableInteraction,
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
                            enabled = enableInteraction,
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
