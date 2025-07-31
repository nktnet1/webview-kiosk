package com.example.webview_locker.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import com.example.webview_locker.config.SystemSettingsKeys
import com.example.webview_locker.config.UserSettingsKeys
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebView(onOpenSettings: () -> Unit) {
    val context = LocalContext.current

    val userPrefs = context.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
    val systemPrefs = context.getSharedPreferences(SystemSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)

    val homeUrl = userPrefs.getString(UserSettingsKeys.HOME_URL, "https://duckduckgo.com")!!

    val initUrl by remember { mutableStateOf(systemPrefs.getString(SystemSettingsKeys.LAST_URL, homeUrl) ?: homeUrl) }
    val savedOffsetX = systemPrefs.getFloat(SystemSettingsKeys.MENU_OFFSET_X, 0f)
    val savedOffsetY = systemPrefs.getFloat(SystemSettingsKeys.MENU_OFFSET_Y, 0f)

    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val webView = remember {
        android.webkit.WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    if (url != null) {
                        systemPrefs.edit { putString(SystemSettingsKeys.LAST_URL, url) }
                    }
                }
            }
            loadUrl(initUrl)
        }
    }

    DisposableEffect(webView) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                }
            }
        }
        onBackPressedDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }

    var menuExpanded by remember { mutableStateOf(false) }

    var offsetX by remember { mutableFloatStateOf(savedOffsetX) }
    var offsetY by remember { mutableFloatStateOf(savedOffsetY) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .padding(24.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(primaryColor)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        },
                        onDragEnd = {
                            systemPrefs.edit {
                                putFloat(SystemSettingsKeys.MENU_OFFSET_X, offsetX)
                                putFloat(SystemSettingsKeys.MENU_OFFSET_Y, offsetY)
                            }
                        }
                    )
                }
                .align(Alignment.BottomEnd)
        ) {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "Menu",
                    tint = onPrimaryColor,
                    modifier = Modifier.size(36.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier
                    .background(primaryColor)
                    .width(IntrinsicSize.Min)
            ) {
                DropdownMenuItem(
                    text = { Text("Home", color = onPrimaryColor) },
                    onClick = {
                        menuExpanded = false
                        webView.loadUrl(homeUrl)
                    },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = onPrimaryColor) },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                DropdownMenuItem(
                    text = { Text("Pin app", color = onPrimaryColor) },
                    onClick = {
                        menuExpanded = false
                        // TODO: Implement pinning
                    },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = onPrimaryColor) },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                DropdownMenuItem(
                    text = { Text("Settings", color = onPrimaryColor) },
                    onClick = {
                        menuExpanded = false
                        onOpenSettings()
                    },
                    leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = onPrimaryColor) },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
