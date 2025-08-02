package com.example.webview_locker.ui.view

import android.app.Activity
import android.content.Context
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.webview_locker.config.SystemSettingsKeys
import com.example.webview_locker.config.UserSettingsKeys
import com.example.webview_locker.ui.components.customWebView
import com.example.webview_locker.ui.components.FloatingMenuButton
import com.example.webview_locker.utils.rememberPinnedState

@Composable
fun WebView(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    val userPrefs = context.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
    val systemPrefs = context.getSharedPreferences(SystemSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)

    val homeUrl = userPrefs.getString(UserSettingsKeys.HOME_URL, "https://duckduckgo.com")!!
    val initUrl by remember { mutableStateOf(systemPrefs.getString(SystemSettingsKeys.LAST_URL, homeUrl) ?: homeUrl) }

    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val webView = customWebView(context, initUrl, systemPrefs)

    HandleBackPress(webView, onBackPressedDispatcher)

    val isPinned by rememberPinnedState()

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

        if (!isPinned) {
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                FloatingMenuButton(
                    onHomeClick = { webView.loadUrl(homeUrl) },
                    onPinClick = {
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
    webView: android.webkit.WebView,
    dispatcher: androidx.activity.OnBackPressedDispatcher?
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

