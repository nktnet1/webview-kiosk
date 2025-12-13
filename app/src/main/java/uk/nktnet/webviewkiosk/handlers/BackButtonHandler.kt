package com.nktnet.webview_kiosk.handlers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.option.BackButtonHoldActionOption
import com.nktnet.webview_kiosk.states.BackButtonStateSingleton
import com.nktnet.webview_kiosk.utils.webview.WebViewNavigation

@Composable
fun BackPressHandler(
    customLoadUrl: (newUrl: String) -> Unit,
) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    val scope = rememberCoroutineScope()
    var enableBack by remember { mutableStateOf(true) }

    LaunchedEffect(userSettings.allowBackwardsNavigation) {
        BackButtonStateSingleton.shortPressEvents.collect {
            if (userSettings.allowBackwardsNavigation && enableBack) {
                WebViewNavigation.goBack(customLoadUrl, systemSettings)
            }
        }
    }

    LaunchedEffect(userSettings.backButtonHoldAction) {
        if (userSettings.backButtonHoldAction != BackButtonHoldActionOption.DISABLED) {
            BackButtonStateSingleton.longPressEvents.collect {
                enableBack = false
                if (userSettings.backButtonHoldAction == BackButtonHoldActionOption.GO_HOME) {
                    WebViewNavigation.goHome(customLoadUrl, systemSettings, userSettings)
                }
                scope.launch {
                    delay(1000L)
                    enableBack = true
                }
            }
        }
    }
}
