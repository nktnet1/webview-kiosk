package com.example.webview_locker.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.core.content.edit
import com.example.webview_locker.config.SystemSettingsKeys

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CustomWebView(
    context: Context,
    initUrl: String,
    systemPrefs: SharedPreferences
): WebView {
    return remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url != null) {
                        systemPrefs.edit { putString(SystemSettingsKeys.LAST_URL, url) }
                    }
                }
            }
            loadUrl(initUrl)
        }
    }
}
