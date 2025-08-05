package com.nktnet.webview_kiosk.ui.view

import android.app.Activity
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.config.AddressBarMode
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.FloatingMenuButton
import com.nktnet.webview_kiosk.utils.createCustomWebview
import com.nktnet.webview_kiosk.utils.generateBlockedPageHtml
import com.nktnet.webview_kiosk.utils.rememberLockedState

@Composable
fun WebviewScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? Activity

    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    val (isBlocked, blockedPageHtml) = rememberBlockLogic(userSettings)
    val isPinned by rememberLockedState()

    var currentUrl by remember { mutableStateOf(systemSettings.lastUrl.ifEmpty { userSettings.homeUrl }) }
    var urlBarText by remember { mutableStateOf(TextFieldValue(currentUrl)) }

    val showAddressBar = when (userSettings.addressBarMode) {
        AddressBarMode.SHOWN -> true
        AddressBarMode.HIDDEN -> false
        AddressBarMode.HIDDEN_WHEN_LOCKED -> !isPinned
    }

    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val webView = createCustomWebview(
        context = context,
        initUrl = currentUrl,
        isBlocked = isBlocked,
        showBlockedPage = { url -> blockedPageHtml(url) },
        onPageFinished = { url ->
            currentUrl = url
            urlBarText = TextFieldValue(url)
            systemSettings.lastUrl = url
        }
    )

    HandleBackPress(webView, onBackPressedDispatcher)

    Column(Modifier.fillMaxSize()) {
        if (showAddressBar) {
            OutlinedTextField(
                value = urlBarText,
                onValueChange = { urlBarText = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                shape = RoundedCornerShape(percent = 50),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        val input = urlBarText.text.trim()
                        val finalUrl = if (input.startsWith("http")) input else "https://$input"
                        currentUrl = finalUrl
                        webView.loadUrl(finalUrl)
                    }
                ),
                textStyle = LocalTextStyle.current,
                trailingIcon = {
                    IconButton(onClick = {
                        val input = urlBarText.text.trim()
                        val finalUrl = if (input.startsWith("http")) input else "https://$input"
                        currentUrl = finalUrl
                        webView.loadUrl(finalUrl)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go")
                    }
                }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

            if (!isPinned) {
                ShowFloatingMenu(
                    navController = navController,
                    onHomeClick = { webView.loadUrl(userSettings.homeUrl) },
                    onLockClick = {
                        try {
                            activity?.startLockTask()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
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

@Composable
private fun rememberBlockLogic(
    userSettings: UserSettings
): Pair<(String) -> Boolean, (String) -> String> {
    val blacklistRegexes = remember(userSettings.websiteBlacklist) {
        userSettings.websiteBlacklist.lines()
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .mapNotNull { runCatching { Regex(it) }.getOrNull() }
    }

    val whitelistRegexes = remember(userSettings.websiteWhitelist) {
        userSettings.websiteWhitelist.lines()
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .mapNotNull { runCatching { Regex(it) }.getOrNull() }
    }

    val blockedMessage = userSettings.blockedMessage

    val isBlocked: (String) -> Boolean = { url ->
        if (whitelistRegexes.any { it.containsMatchIn(url) }) false
        else blacklistRegexes.any { it.containsMatchIn(url) }
    }

    return Pair(isBlocked) { url -> generateBlockedPageHtml(url, blockedMessage) }
}

@Composable
private fun BoxScope.ShowFloatingMenu(
    navController: NavController,
    onHomeClick: () -> Unit,
    onLockClick: () -> Unit
) {
    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
        FloatingMenuButton(
            onHomeClick = onHomeClick,
            onLockClick = onLockClick,
            navController = navController
        )
    }
}
