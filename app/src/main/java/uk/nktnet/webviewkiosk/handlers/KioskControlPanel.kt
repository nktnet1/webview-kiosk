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
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.BackButtonHoldActionOption
import uk.nktnet.webviewkiosk.config.option.FloatingToolbarModeOption
import uk.nktnet.webviewkiosk.config.option.KioskControlPanelRegionOption
import uk.nktnet.webviewkiosk.config.option.WebviewControlActionOption
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.states.BackButtonStateSingleton
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.states.WaitingForUnlockStateSingleton
import uk.nktnet.webviewkiosk.utils.canDisableKioskControlPanelRegion
import uk.nktnet.webviewkiosk.utils.handleUserKeyEvent
import uk.nktnet.webviewkiosk.utils.handleUserTouchEvent
import uk.nktnet.webviewkiosk.utils.tryLockTask
import uk.nktnet.webviewkiosk.utils.unlockWithAuthIfRequired
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation
import kotlin.math.max

@Composable
private fun ActionButton(
    action: WebviewControlActionOption,
    enabled: Boolean,
    onClick: () -> Unit,
    iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = action.label,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(action.label)
    }
}

@Composable
fun KioskControlPanel(
    navController: NavController,
    requiredTaps: Int,
    showFindInPage: () -> Unit,
    showHistoryDialog: () -> Unit,
    showBookmarkDialog: () -> Unit,
    showFilesDialog: () -> Unit,
    showAppsDialog: () -> Unit,
    webView: WebView,
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

    var showDialog by remember { mutableStateOf(false) }
    var isSticky by remember { mutableStateOf(systemSettings.isKioskControlPanelSticky) }

    val kioskControlPanelRegion = if (
        userSettings.kioskControlPanelRegion == KioskControlPanelRegionOption.DISABLED
        && !canDisableKioskControlPanelRegion(userSettings)
    ) {
        KioskControlPanelRegionOption.TOP_LEFT
    } else {
        userSettings.kioskControlPanelRegion
    }

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

    if (kioskControlPanelRegion != KioskControlPanelRegionOption.DISABLED) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInteropFilter { motionEvent ->
                    if (motionEvent.action == android.view.MotionEvent.ACTION_DOWN) {
                        val now = System.currentTimeMillis()

                        val inRegion = when (kioskControlPanelRegion) {
                            KioskControlPanelRegionOption.TOP_LEFT -> motionEvent.x < screenWidthPx / 2f && motionEvent.y < screenHeightPx / 2f
                            KioskControlPanelRegionOption.TOP_RIGHT -> motionEvent.x >= screenWidthPx / 2f && motionEvent.y < screenHeightPx / 2f
                            KioskControlPanelRegionOption.BOTTOM_LEFT -> motionEvent.x < screenWidthPx / 2f && motionEvent.y >= screenHeightPx / 2f
                            KioskControlPanelRegionOption.BOTTOM_RIGHT -> motionEvent.x >= screenWidthPx / 2f && motionEvent.y >= screenHeightPx / 2f
                            KioskControlPanelRegionOption.TOP -> motionEvent.y < screenHeightPx / 2f
                            KioskControlPanelRegionOption.BOTTOM -> motionEvent.y >= screenHeightPx / 2f
                            KioskControlPanelRegionOption.FULL -> true
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
                                    ToastManager.cancel()
                                    enableInteraction = false
                                    handleShowDialog()
                                    scope.launch {
                                        delay(600L)
                                        enableInteraction = true
                                    }
                                }
                                tapsLeft <= 5 -> {
                                    ToastManager.show(
                                        context,
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
                val (boxWidth, boxHeight, boxAlignment) = when (kioskControlPanelRegion) {
                    KioskControlPanelRegionOption.TOP_LEFT -> Triple(0.5f, 0.5f, Alignment.TopStart)
                    KioskControlPanelRegionOption.TOP_RIGHT -> Triple(0.5f, 0.5f, Alignment.TopEnd)
                    KioskControlPanelRegionOption.BOTTOM_LEFT -> Triple(0.5f, 0.5f, Alignment.BottomStart)
                    KioskControlPanelRegionOption.BOTTOM_RIGHT -> Triple(0.5f, 0.5f, Alignment.BottomEnd)
                    KioskControlPanelRegionOption.TOP -> Triple(1f, 0.5f, Alignment.TopCenter)
                    KioskControlPanelRegionOption.BOTTOM -> Triple(1f, 0.5f, Alignment.BottomCenter)
                    KioskControlPanelRegionOption.FULL -> Triple(1f, 1f, Alignment.Center)
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

    val menuItems: Map<WebviewControlActionOption, @Composable () -> Unit> = remember(
        isSticky,
        isLocked,
        systemSettings.historyIndex,
        systemSettings.historyStack.size,
    ) {
        val canGoForward = systemSettings.historyIndex < (systemSettings.historyStack.size - 1)
        val canGoBack = systemSettings.historyIndex > 0

        mapOf(
            WebviewControlActionOption.NAVIGATION to {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            WebViewNavigation.goBack(customLoadUrl, systemSettings)
                            showDialog = isSticky
                        },
                        enabled = enableInteraction && canGoBack,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_arrow_back_24),
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Back",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    Button(
                        onClick = {
                            WebViewNavigation.goForward(customLoadUrl, systemSettings)
                            showDialog = isSticky
                        },
                        enabled = enableInteraction && canGoForward,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Forward",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(R.drawable.baseline_arrow_forward_24),
                            contentDescription = "Forward",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            },
            WebviewControlActionOption.BACK to {
                ActionButton(
                    action = WebviewControlActionOption.BACK,
                    enabled = enableInteraction && canGoBack,
                    onClick = {
                        WebViewNavigation.goBack(customLoadUrl, systemSettings)
                        showDialog = isSticky
                    },
                    iconRes = R.drawable.baseline_arrow_back_24
                )
            },
            WebviewControlActionOption.FORWARD to {
                ActionButton(
                    action = WebviewControlActionOption.FORWARD,
                    enabled = enableInteraction && canGoForward,
                    onClick = {
                        WebViewNavigation.goForward(customLoadUrl, systemSettings)
                        showDialog = isSticky
                    },
                    iconRes = R.drawable.baseline_arrow_forward_24
                )
            },
            WebviewControlActionOption.HOME to {
                ActionButton(
                    action = WebviewControlActionOption.HOME,
                    enabled = enableInteraction,
                    onClick = {
                        WebViewNavigation.goHome(customLoadUrl, systemSettings, userSettings)
                        showDialog = isSticky
                    },
                    iconRes = R.drawable.baseline_home_24
                )
            },
            WebviewControlActionOption.REFRESH to {
                ActionButton(
                    action = WebviewControlActionOption.REFRESH,
                    enabled = enableInteraction,
                    onClick = {
                        WebViewNavigation.refresh(customLoadUrl, systemSettings, userSettings)
                        showDialog = isSticky
                    },
                    iconRes = R.drawable.baseline_refresh_24
                )
            },
            WebviewControlActionOption.HISTORY to {
                ActionButton(
                    action = WebviewControlActionOption.HISTORY,
                    enabled = enableInteraction,
                    onClick = {
                        showDialog = isSticky
                        showHistoryDialog()
                    },
                    iconRes = R.drawable.outline_history_24
                )
            },
            WebviewControlActionOption.BOOKMARK to {
                ActionButton(
                    action = WebviewControlActionOption.BOOKMARK,
                    enabled = enableInteraction,
                    onClick = {
                        showDialog = isSticky
                        showBookmarkDialog()
                    },
                    iconRes = R.drawable.outline_bookmark_24
                )
            },
            WebviewControlActionOption.FILES to {
                ActionButton(
                    action = WebviewControlActionOption.FILES,
                    enabled = enableInteraction,
                    onClick = {
                        showDialog = isSticky
                        showFilesDialog()
                    },
                    iconRes = R.drawable.outline_folder_24
                )
            },
            WebviewControlActionOption.FIND to {
                ActionButton(
                    action = WebviewControlActionOption.FIND,
                    enabled = enableInteraction,
                    onClick = {
                        showDialog = isSticky
                        showFindInPage()
                    },
                    iconRes = R.drawable.find_in_page_24
                )
            },
            WebviewControlActionOption.SCROLL_TOP to {
                ActionButton(
                    action = WebviewControlActionOption.SCROLL_TOP,
                    enabled = enableInteraction,
                    onClick = {
                        showDialog = isSticky
                        webView.pageUp(true)
                    },
                    iconRes = R.drawable.keyboard_double_arrow_up_24
                )
            },
            WebviewControlActionOption.SCROLL_BOT to {
                ActionButton(
                    action = WebviewControlActionOption.SCROLL_BOT,
                    enabled = enableInteraction,
                    onClick = {
                        showDialog = isSticky
                        webView.pageDown(true)
                    },
                    iconRes = R.drawable.keyboard_double_arrow_down_24
                )
            },
            WebviewControlActionOption.APPS to {
                ActionButton(
                    action = WebviewControlActionOption.APPS,
                    enabled = enableInteraction,
                    onClick = {
                        showAppsDialog()
                    },
                    iconRes = R.drawable.apps_24px
                )
            },
            WebviewControlActionOption.SETTINGS to {
                ActionButton(
                    action = WebviewControlActionOption.SETTINGS,
                    enabled = enableInteraction,
                    onClick = {
                        showDialog = false
                        navController.navigate(Screen.Settings.route)
                    },
                    iconRes = R.drawable.baseline_settings_24
                )
            },
            WebviewControlActionOption.LOCK to {
                ActionButton(
                    action = WebviewControlActionOption.LOCK,
                    enabled = enableInteraction,
                    onClick = {
                        showDialog = isSticky
                        tryLockTask(activity)
                    },
                    iconRes = R.drawable.baseline_lock_24
                )
            },
            WebviewControlActionOption.UNLOCK to {
                ActionButton(
                    action = WebviewControlActionOption.UNLOCK,
                    enabled = enableInteraction,
                    onClick = {
                        activity?.let {
                            unlockWithAuthIfRequired(activity)
                        }
                    },
                    iconRes = R.drawable.baseline_lock_open_24
                )
            },
        )
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
                modifier = Modifier
                    .handleUserTouchEvent()
                    .handleUserKeyEvent(context, showDialog)
                    .padding(16.dp)
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
                                val newSticky = !isSticky
                                ToastManager.show(context, "Sticky mode ${if (newSticky) "enabled." else "disabled."}")
                                isSticky = newSticky
                                systemSettings.isKioskControlPanelSticky = newSticky
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

                    val enabledActions = remember(userSettings, isLocked) {
                        val result = mutableListOf<WebviewControlActionOption>()
                        var hasSettings = false
                        var hasUnlock = false

                        userSettings.kioskControlPanelActions.forEach { action ->
                            val include = when (action) {
                                WebviewControlActionOption.NAVIGATION -> userSettings.allowBackwardsNavigation
                                WebviewControlActionOption.BACK -> userSettings.allowBackwardsNavigation
                                WebviewControlActionOption.FORWARD -> userSettings.allowBackwardsNavigation
                                WebviewControlActionOption.HOME -> userSettings.allowGoHome
                                WebviewControlActionOption.REFRESH -> userSettings.allowRefresh
                                WebviewControlActionOption.HISTORY -> userSettings.allowHistoryAccess
                                WebviewControlActionOption.BOOKMARK -> userSettings.allowBookmarkAccess
                                WebviewControlActionOption.FILES -> userSettings.allowLocalFiles
                                WebviewControlActionOption.SETTINGS -> !isLocked
                                WebviewControlActionOption.LOCK -> !isLocked
                                WebviewControlActionOption.UNLOCK -> isLocked
                                WebviewControlActionOption.APPS,
                                WebviewControlActionOption.FIND,
                                WebviewControlActionOption.SCROLL_TOP,
                                WebviewControlActionOption.SCROLL_BOT -> true
                            }

                            if (include) {
                                result.add(action)
                                if (action == WebviewControlActionOption.SETTINGS) {
                                    hasSettings = true
                                }
                                if (action == WebviewControlActionOption.UNLOCK) {
                                    hasUnlock = true
                                }
                            }
                        }
                        if (
                            !isLocked
                            && !hasSettings
                            && userSettings.floatingToolbarMode == FloatingToolbarModeOption.HIDDEN
                        ) {
                            result.add(WebviewControlActionOption.SETTINGS)
                        }
                        if (isLocked && !hasUnlock) {
                            result.add(WebviewControlActionOption.UNLOCK)
                        }
                        result
                    }

                    enabledActions.forEach { action ->
                        menuItems[action]?.invoke()
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
