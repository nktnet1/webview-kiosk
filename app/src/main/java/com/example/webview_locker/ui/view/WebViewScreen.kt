package com.example.webview_locker.ui.view

import android.app.Activity
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.webview_locker.config.SystemSettings
import com.example.webview_locker.config.UserSettings
import com.example.webview_locker.ui.components.customWebView
import com.example.webview_locker.ui.components.FloatingMenuButton
import com.example.webview_locker.utils.rememberLockedState

@Composable
fun WebView(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    val homeUrl = userSettings.homeUrl
    val initUrl by remember { mutableStateOf(systemSettings.lastUrl.ifEmpty { homeUrl }) }

    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val webView = customWebView(context, initUrl)

    HandleBackPress(webView, onBackPressedDispatcher)

    val isPinned by rememberLockedState()

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

        if (!isPinned) {
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                FloatingMenuButton(
                    onHomeClick = { webView.loadUrl(homeUrl) },
                    onLockClick = {
                        try {
                            activity?.startLockTask()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    onSettingsClick = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun HandleBackPress(
    webView: WebView,
    dispatcher: OnBackPressedDispatcher?
) {
    DisposableEffect(webView) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                }
            }
        }
        dispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }
}
