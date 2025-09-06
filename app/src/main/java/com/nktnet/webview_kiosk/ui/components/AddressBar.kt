package com.nktnet.webview_kiosk.ui.components

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.WebViewInset
import com.nktnet.webview_kiosk.utils.rememberLockedState
import com.nktnet.webview_kiosk.utils.webview.WebViewNavigation

@Composable
fun AddressBar(
    urlBarText: TextFieldValue,
    onUrlBarTextChange: (TextFieldValue) -> Unit,
    hasFocus: Boolean,
    onFocusChanged: (FocusState) -> Unit,
    focusRequester: FocusRequester,
    addressBarSearch: (String) -> Unit,
    webView: WebView,
    customLoadUrl: (newUrl: String) -> Unit
) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }
    var urlTextState by remember { mutableStateOf(urlBarText.text) }

    val isLocked by rememberLockedState()
    val addressBarInset = when (userSettings.webViewInset) {
        WebViewInset.StatusBars,
        WebViewInset.SystemBars,
        WebViewInset.SafeDrawing,
        WebViewInset.SafeGestures,
        WebViewInset.SafeContent -> WindowInsets()
        else -> if (!isLocked) WindowInsets.statusBars else WindowInsets()
    }

    var menuExpanded by remember { mutableStateOf(false) }
    val showMenu =
        userSettings.allowBackwardsNavigation || userSettings.allowRefresh || userSettings.allowGoHome ||
                userSettings.allowHistoryAccess || userSettings.allowBookmarkAccess
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .windowInsetsPadding(addressBarInset)
            .fillMaxWidth()
            .height(70.dp)
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
                onGo = { if (urlBarText.text.isNotBlank()) addressBarSearch(urlBarText.text) }
            ),
            textStyle = LocalTextStyle.current,
            trailingIcon = {
                IconButton(onClick = { addressBarSearch(urlTextState) }) {
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
                            enabled = systemSettings.historyIndex > 0,
                            onClick = {
                                WebViewNavigation.goBack(customLoadUrl, systemSettings)
                                val newUrl =
                                    systemSettings.historyStack[systemSettings.historyIndex].url
                                onUrlBarTextChange(TextFieldValue(newUrl))
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Forward") },
                            enabled = systemSettings.historyIndex < (systemSettings.historyStack.size - 1),
                            onClick = {
                                WebViewNavigation.goForward(customLoadUrl, systemSettings)
                                val newUrl =
                                    systemSettings.historyStack[systemSettings.historyIndex].url
                                onUrlBarTextChange(TextFieldValue(newUrl))
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Forward"
                                )
                            }
                        )
                    }
                    if (userSettings.allowRefresh) {
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            onClick = { webView.reload(); menuExpanded = false },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Refresh"
                                )
                            }
                        )
                    }
                    if (userSettings.allowGoHome) {
                        DropdownMenuItem(
                            text = { Text("Home") },
                            onClick = {
                                WebViewNavigation.goHome(customLoadUrl, systemSettings, userSettings)
                                onUrlBarTextChange(TextFieldValue(userSettings.homeUrl))
                                menuExpanded = false
                            },
                            leadingIcon = { Icon(Icons.Filled.Home, contentDescription = "Home") }
                        )
                    }
                    if (userSettings.allowHistoryAccess) {
                        DropdownMenuItem(
                            text = { Text("History") },
                            onClick = {
                                menuExpanded = false
                                showHistoryDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.outline_history_24),
                                    contentDescription = "History"
                                )
                            }
                        )
                    }
                    if (userSettings.allowBookmarkAccess) {
                        DropdownMenuItem(
                            text = { Text("Bookmark") },
                            onClick = {
                                menuExpanded = false
                                showBookmarksDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.outline_bookmark_24),
                                    contentDescription = "Bookmarks"
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showHistoryDialog) {
        HistoryDialog(
            customLoadUrl,
            onDismiss = { showHistoryDialog = false }
        )
    }

    if (showBookmarksDialog) {
        BookmarksDialog(
            customLoadUrl,
            onDismiss = { showBookmarksDialog = false },
        )
    }

    LaunchedEffect(hasFocus) {
        if (hasFocus) onUrlBarTextChange(
            urlBarText.copy(
                selection = TextRange(0, urlBarText.text.length)
            )
        )
    }
}
