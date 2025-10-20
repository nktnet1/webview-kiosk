package uk.nktnet.webviewkiosk.handlers

import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.BackButtonHoldActionOption
import uk.nktnet.webviewkiosk.states.BackButtonStateSingleton
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation

@Composable
fun BackPressHandler(
    webView: WebView,
    customLoadUrl: (newUrl: String) -> Unit,
    dispatcher: OnBackPressedDispatcher?,
) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    val scope = rememberCoroutineScope()
    var enableBack by remember { mutableStateOf(true) }

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

    DisposableEffect(webView, dispatcher, userSettings.allowBackwardsNavigation) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!userSettings.allowBackwardsNavigation || !enableBack) {
                    return
                }
                WebViewNavigation.goBack(customLoadUrl, systemSettings)
            }
        }
        dispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }
}
