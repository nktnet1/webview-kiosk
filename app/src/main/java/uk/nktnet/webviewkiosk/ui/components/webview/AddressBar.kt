package uk.nktnet.webviewkiosk.ui.components.webview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.WebViewInset
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.utils.webview.Suggest
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation

@Composable
fun AddressBar(
    urlBarText: TextFieldValue,
    onUrlBarTextChange: (TextFieldValue) -> Unit,
    hasFocus: Boolean,
    onFocusChanged: (FocusState) -> Unit,
    addressBarSearch: (String) -> Unit,
    customLoadUrl: (newUrl: String) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    var menuExpanded by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showLocalFilesDialog by remember { mutableStateOf(false) }
    var allowFocus by remember { mutableStateOf(false) }

    val isLocked by LockStateSingleton.isLocked

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

    var suggestions by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        allowFocus = true
    }

    LaunchedEffect(urlBarText.text) {
        if (urlBarText.text.isNotBlank()) {
            delay(300)
            suggestions = try {
                withContext(Dispatchers.IO) {
                    Suggest.duckduckgo(urlBarText.text)
                }
            } catch (_: Exception) {
                emptyList()
            }
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
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = urlBarText,
                onValueChange = {
                    onUrlBarTextChange(it)
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        onFocusChanged(it)
                    }
                    .focusProperties { canFocus = allowFocus },
                shape = RoundedCornerShape(percent = 50),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    if (urlBarText.text.isNotBlank()) {
                        addressBarSearch(urlBarText.text)
                    }
                }),
                trailingIcon = {
                    IconButton(onClick = {
                        addressBarSearch(urlBarText.text)
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_search_24),
                            contentDescription = "Go"
                        )
                    }
                }
            )

            DropdownMenu(
                expanded = hasFocus && urlBarText.text.isNotBlank() && suggestions.isNotEmpty(),
                onDismissRequest = { suggestions = emptyList() },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .fillMaxWidth(0.9f),
                properties = PopupProperties(
                    focusable = false
                ),
            ) {
                suggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = {
                            onUrlBarTextChange(TextFieldValue(suggestion))
                            addressBarSearch(suggestion)
                        },
                        modifier = Modifier
                    )
                }
            }
        }

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
                    onClick = {
                        focusManager.clearFocus()
                        menuExpanded = true
                    },
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
                            onClick = {
                                WebViewNavigation.refresh(customLoadUrl, systemSettings, userSettings)
                                menuExpanded = false
                            },
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
                            leadingIcon = { Icon(painter = painterResource(R.drawable.outline_folder_24), contentDescription = "Local Files") }
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
