package com.example.webview_locker.ui.view

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import com.example.webview_locker.config.SystemSettingsKeys
import com.example.webview_locker.config.UserSettingsKeys
import com.example.webview_locker.ui.components.CustomWebView
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

    val webView = CustomWebView(context, initUrl, systemPrefs)

    HandleBackPress(webView, onBackPressedDispatcher)

    var menuExpanded by remember { mutableStateOf(false) }

    val isPinned by rememberPinnedState()

    val lighterBlue = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

        if (!isPinned) {
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                FloatingMenuButton(
                    onMenuClick = { menuExpanded = true },
                    isMenuExpanded = menuExpanded,
                    onDismissMenu = { menuExpanded = false },
                    onHomeClick = {
                        menuExpanded = false
                        webView.loadUrl(homeUrl)
                    },
                    onPinClick = {
                        menuExpanded = false
                        try {
                            activity?.startLockTask()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    onSettingsClick = {
                        menuExpanded = false
                        onOpenSettings()
                    },
                    tintColor = lighterBlue
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

