package uk.nktnet.webviewkiosk.ui.components.webview

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.WebViewInset
import uk.nktnet.webviewkiosk.utils.LockStateViewModel
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation

@Composable
fun AddressBar(
    urlBarText: TextFieldValue,
    onUrlBarTextChange: (TextFieldValue) -> Unit,
    hasFocus: Boolean,
    onFocusChanged: (FocusState) -> Unit,
    addressBarSearch: (String) -> Unit,
    webView: WebView,
    customLoadUrl: (newUrl: String) -> Unit,
) {
    val context = LocalContext.current

    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }
    var urlTextState by remember { mutableStateOf(urlBarText.text) }

    var menuExpanded by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showLocalFilesDialog by remember { mutableStateOf(false) }

    val isLocked by viewModel<LockStateViewModel>().isLocked

    val addressBarInset = when (userSettings.webViewInset) {
        WebViewInset.StatusBars,
        WebViewInset.SystemBars,
        WebViewInset.SafeDrawing,
        WebViewInset.SafeGestures,
        WebViewInset.SafeContent -> WindowInsets()
        else -> if (!isLocked) {
            WindowInsets.statusBars
        } else {
            WindowInsets()
        }
    }

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
                .onFocusChanged(onFocusChanged),
            shape = RoundedCornerShape(percent = 50),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = {
                if (urlBarText.text.isNotBlank()) {
                    addressBarSearch(urlBarText.text)
                }
            }),
            trailingIcon = {
                IconButton(onClick = { addressBarSearch(urlTextState) }) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_search_24),
                        contentDescription = "Go"
                    )
                }
            }
        )

        val showMenu =
            userSettings.allowBackwardsNavigation
                || userSettings.allowRefresh
                || userSettings.allowGoHome
                || userSettings.allowHistoryAccess
                || userSettings.allowBookmarkAccess
                || userSettings.allowLocalFiles

        if (showMenu) {
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
            ) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .padding(0.dp)
                        .size(width = 24.dp, height = 80.dp)
                        .wrapContentSize(Alignment.Center)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.outline_more_vert_24),
                        contentDescription = "Menu",
                        modifier = Modifier.size(32.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (userSettings.allowBackwardsNavigation) {
                        DropdownMenuItem(
                            text = { Text("Back") },
                            enabled = systemSettings.historyIndex > 0,
                            onClick = {
                                WebViewNavigation.goBack(customLoadUrl, systemSettings)
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_arrow_back_24),
                                    contentDescription = "Back"
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Forward") },
                            enabled = systemSettings.historyIndex < (systemSettings.historyStack.size - 1),
                            onClick = {
                                WebViewNavigation.goForward(customLoadUrl, systemSettings)
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_arrow_forward_24),
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
                                    painter = painterResource(R.drawable.baseline_refresh_24),
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
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_home_24),
                                    contentDescription = "Home"
                                )
                            }
                        )
                    }
                    if (userSettings.allowHistoryAccess) {
                        DropdownMenuItem(
                            text = { Text("History") },
                            onClick = {
                                menuExpanded = false
                                showHistoryDialog = true
                            },
                            leadingIcon = { Icon(painter = painterResource(R.drawable.outline_history_24), contentDescription = "History") }
                        )
                    }
                    if (userSettings.allowBookmarkAccess) {
                        DropdownMenuItem(
                            text = { Text("Bookmark") },
                            onClick = {
                                menuExpanded = false
                                showBookmarksDialog = true
                            },
                            leadingIcon = { Icon(painter = painterResource(R.drawable.outline_bookmark_24), contentDescription = "Bookmarks") }
                        )
                    }
                    if (userSettings.allowLocalFiles) {
                        DropdownMenuItem(
                            text = { Text("Files") },
                            onClick = {
                                menuExpanded = false
                                showLocalFilesDialog = true
                            },
                            leadingIcon = { Icon(painter = painterResource(R.drawable.outline_upload_file_24), contentDescription = "Local Files") }
                        )
                    }
                }
            }
        }
    }

    if (showHistoryDialog) {
        HistoryDialog(customLoadUrl, onDismiss = { showHistoryDialog = false })
    }

    if (showBookmarksDialog) {
        BookmarksDialog(customLoadUrl, onDismiss = { showBookmarksDialog = false })
    }

    if (showLocalFilesDialog) {
        LocalFilesDialog(
            onDismiss = { showLocalFilesDialog = false },
            customLoadUrl = customLoadUrl
        )
    }

    LaunchedEffect(hasFocus) {
        if (hasFocus) {
            onUrlBarTextChange(urlBarText.copy(selection = TextRange(0, urlBarText.text.length)))
        }
    }
}
