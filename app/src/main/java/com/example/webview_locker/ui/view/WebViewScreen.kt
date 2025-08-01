package com.example.webview_locker.ui.view

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
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
import com.example.webview_locker.utils.rememberPinnedState
import kotlin.math.roundToInt

@Composable
fun WebView(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    val userPrefs = context.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
    val systemPrefs = context.getSharedPreferences(SystemSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)

    val homeUrl = userPrefs.getString(UserSettingsKeys.HOME_URL, "https://duckduckgo.com")!!
    val initUrl by remember { mutableStateOf(systemPrefs.getString(SystemSettingsKeys.LAST_URL, homeUrl) ?: homeUrl) }

    val savedOffsetX = systemPrefs.getFloat(SystemSettingsKeys.MENU_OFFSET_X, 0f)
    val savedOffsetY = systemPrefs.getFloat(SystemSettingsKeys.MENU_OFFSET_Y, 0f)

    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val webView = rememberWebView(context, initUrl, systemPrefs)

    HandleBackPress(webView, onBackPressedDispatcher)

    var menuExpanded by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(savedOffsetX) }
    var offsetY by remember { mutableFloatStateOf(savedOffsetY) }

    val isPinned by rememberPinnedState()

    val lighterBlue = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

        if (!isPinned) {
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                FloatingMenuButton(
                    offsetX = offsetX,
                    offsetY = offsetY,
                    onOffsetChange = { x, y ->
                        offsetX = x
                        offsetY = y
                        systemPrefs.edit {
                            putFloat(SystemSettingsKeys.MENU_OFFSET_X, offsetX)
                            putFloat(SystemSettingsKeys.MENU_OFFSET_Y, offsetY)
                        }
                    },
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun rememberWebView(
    context: Context,
    initUrl: String,
    systemPrefs: android.content.SharedPreferences
): android.webkit.WebView {
    return remember {
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

@Composable
private fun FloatingMenuButton(
    offsetX: Float,
    offsetY: Float,
    onOffsetChange: (Float, Float) -> Unit,
    onMenuClick: () -> Unit,
    isMenuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onHomeClick: () -> Unit,
    onPinClick: () -> Unit,
    onSettingsClick: () -> Unit,
    tintColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .padding(24.dp)
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onOffsetChange(offsetX + dragAmount.x, offsetY + dragAmount.y)
                    }
                )
            }
    ) {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = "Menu",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        DropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = onDismissMenu
        ) {
            DropdownMenuItem(
                text = { Text("Home", color = tintColor) },
                onClick = onHomeClick,
                leadingIcon = {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = tintColor
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Pin app", color = tintColor) },
                onClick = onPinClick,
                leadingIcon = {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = tintColor
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Settings", color = tintColor) },
                onClick = onSettingsClick,
                leadingIcon = {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = tintColor
                    )
                }
            )
        }
    }
}
