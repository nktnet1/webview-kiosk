package com.nktnet.webview_kiosk.ui.components

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.nktnet.webview_kiosk.config.UserSettings

@Composable
fun AddressBar(
    urlBarText: TextFieldValue,
    onUrlBarTextChange: (TextFieldValue) -> Unit,
    hasFocus: Boolean,
    onFocusChanged: (FocusState) -> Unit,
    focusRequester: FocusRequester,
    triggerLoad: (String) -> Unit,
    webView: WebView,
) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    var urlTextState by remember { mutableStateOf(urlBarText.text) }

    var menuExpanded by remember { mutableStateOf(false) }

    val showMenu = userSettings.allowBackwardsNavigation || userSettings.allowRefresh || userSettings.allowGoHome

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp)
    ) {
        OutlinedTextField(
            value = urlBarText,
            onValueChange = {
                onUrlBarTextChange(it)
                urlTextState = it.text
            },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged(onFocusChanged),
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
                    if (urlBarText.text.isNotBlank()) {
                        triggerLoad(urlBarText.text)
                    }
                }
            ),
            textStyle = LocalTextStyle.current,
            trailingIcon = {
                IconButton(onClick = { triggerLoad(urlTextState) }) {
                    Icon(Icons.Default.Search, contentDescription = "Go")
                }
            }
        )

        if (showMenu) {
            Box(modifier = Modifier.padding(start = 4.dp)) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .padding(0.dp)
                        .size(width = 24.dp, height = 80.dp)
                        .wrapContentSize(Alignment.Center)
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Menu",
                        modifier = Modifier.size(32.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (userSettings.allowBackwardsNavigation) {
                        DropdownMenuItem(
                            text = { Text("Back") },
                            enabled = webView.canGoBack(),
                            onClick = {
                                webView.goBack()
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Forward") },
                            enabled = webView.canGoForward(),
                            onClick = {
                                webView.goForward()
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                            }
                        )
                    }
                    if (userSettings.allowRefresh) {
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            onClick = {
                                webView.reload()
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                        )
                    }
                    if (userSettings.allowGoHome) {
                        DropdownMenuItem(
                            text = { Text("Home") },
                            onClick = {
                                webView.loadUrl(userSettings.homeUrl)
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Home, contentDescription = "Home")
                            }
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(hasFocus) {
        if (hasFocus) {
            onUrlBarTextChange(urlBarText.copy(selection = TextRange(0, urlBarText.text.length)))
        }
    }
}
