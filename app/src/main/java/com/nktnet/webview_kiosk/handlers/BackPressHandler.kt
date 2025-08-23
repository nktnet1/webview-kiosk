package com.nktnet.webview_kiosk.handlers

import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.utils.webview.WebViewNavigation

@Composable
fun BackPressHandler(
    webView: WebView,
    dispatcher: OnBackPressedDispatcher?,
) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    DisposableEffect(webView, dispatcher, userSettings.allowBackwardsNavigation) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!userSettings.allowBackwardsNavigation) return
                WebViewNavigation.goBack(webView, systemSettings)
            }
        }
        dispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }
}
