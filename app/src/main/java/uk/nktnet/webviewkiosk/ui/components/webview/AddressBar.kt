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
import kotlinx.coroutines.delay
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.AddressBarActionOption
import uk.nktnet.webviewkiosk.config.option.WebViewInset
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.utils.handleUserKeyEvent
import uk.nktnet.webviewkiosk.utils.handleUserTouchEvent
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation

@Composable
private fun AddressBarMenuItem(
    action: AddressBarActionOption,
    enabled: Boolean = true,
    onClick: () -> Unit,
    iconRes: Int,
) {
    DropdownMenuItem(
        text = { Text(action.label) },
        enabled = enabled,
        onClick = onClick,
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = action.label
            )
        }
    )
}

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

    val statusBarInset = WindowInsets.statusBars
    val addressBarInset = remember (isLocked, statusBarInset) {
        when (userSettings.webViewInset) {
            WebViewInset.StatusBars,
            WebViewInset.SystemBars,
            WebViewInset.SafeDrawing,
            WebViewInset.SafeGestures,
            WebViewInset.SafeContent -> WindowInsets()
            else -> if (!isLocked) {
                statusBarInset
            } else {
                WindowInsets()
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(200)
        allowFocus = true
    }

    LaunchedEffect(hasFocus) {
        if (hasFocus) {
            onUrlBarTextChange(urlBarText.copy(selection = TextRange(0, urlBarText.text.length)))
        }
    }

    val menuItems: Map<AddressBarActionOption, @Composable () -> Unit> = remember {
        mapOf(
            AddressBarActionOption.BACK to {
                AddressBarMenuItem(
                    action = AddressBarActionOption.BACK,
                    enabled = systemSettings.historyIndex > 0,
                    onClick = {
                        WebViewNavigation.goBack(customLoadUrl, systemSettings)
                        menuExpanded = false
                    },
                    iconRes = R.drawable.baseline_arrow_back_24,
                )
            },
            AddressBarActionOption.FORWARD to {
                AddressBarMenuItem(
                    action = AddressBarActionOption.FORWARD,
                    enabled = systemSettings.historyIndex < (systemSettings.historyStack.size - 1),
                    onClick = {
                        WebViewNavigation.goForward(customLoadUrl, systemSettings)
                        menuExpanded = false
                    },
                    iconRes = R.drawable.baseline_arrow_forward_24,
                )
            },
            AddressBarActionOption.REFRESH to {
                AddressBarMenuItem(
                    action = AddressBarActionOption.REFRESH,
                    onClick = {
                        WebViewNavigation.refresh(customLoadUrl, systemSettings, userSettings)
                        menuExpanded = false
                    },
                    iconRes = R.drawable.baseline_refresh_24,
                )
            },
            AddressBarActionOption.HOME to {
                AddressBarMenuItem(
                    action = AddressBarActionOption.HOME,
                    onClick = {
                        WebViewNavigation.goHome(customLoadUrl, systemSettings, userSettings)
                        menuExpanded = false
                    },
                    iconRes = R.drawable.baseline_home_24,
                )
            },
            AddressBarActionOption.HISTORY to {
                AddressBarMenuItem(
                    action = AddressBarActionOption.HISTORY,
                    onClick = {
                        showHistoryDialog = true
                        menuExpanded = false
                    },
                    iconRes = R.drawable.outline_history_24,
                )
            },
            AddressBarActionOption.BOOKMARK to {
                AddressBarMenuItem(
                    action = AddressBarActionOption.BOOKMARK,
                    onClick = {
                        showBookmarksDialog = true
                        menuExpanded = false
                    },
                    iconRes = R.drawable.outline_bookmark_24,
                )
            },
            AddressBarActionOption.FILES to {
                AddressBarMenuItem(
                    action = AddressBarActionOption.FILES,
                    onClick = {
                        showLocalFilesDialog = true
                        menuExpanded = false
                    },
                    iconRes = R.drawable.outline_folder_24,
                )
            }
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .windowInsetsPadding(addressBarInset)
            .fillMaxWidth()
            .focusProperties { canFocus = allowFocus }
            .height(70.dp)
            .padding(horizontal = 8.dp)
    ) {
        OutlinedTextField(
            value = urlBarText,
            onValueChange = { onUrlBarTextChange(it) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onFocusChanged(onFocusChanged)
                .focusProperties { canFocus = allowFocus },
            shape = RoundedCornerShape(percent = 50),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = {
                if (urlBarText.text.isNotBlank()) {
                    addressBarSearch(urlBarText.text)
                }
            }),
            trailingIcon = {
                IconButton(
                    onClick = { addressBarSearch(urlBarText.text) }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_search_24),
                        contentDescription = "Go"
                    )
                }
            }
        )

        val enabledActions = remember {
            userSettings.addressBarActions.filter { action ->
                when (action) {
                    AddressBarActionOption.BACK -> userSettings.allowBackwardsNavigation
                    AddressBarActionOption.FORWARD -> userSettings.allowBackwardsNavigation
                    AddressBarActionOption.REFRESH -> userSettings.allowRefresh
                    AddressBarActionOption.HOME -> userSettings.allowGoHome
                    AddressBarActionOption.HISTORY -> userSettings.allowHistoryAccess
                    AddressBarActionOption.BOOKMARK -> userSettings.allowBookmarkAccess
                    AddressBarActionOption.FILES -> userSettings.allowLocalFiles
                }
            }
        }

        if (enabledActions.isNotEmpty()) {
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
                    modifier = Modifier
                        .handleUserTouchEvent()
                        .handleUserKeyEvent(context, menuExpanded)
                ) {
                    enabledActions.forEach { key ->
                        menuItems[key]?.invoke()
                    }
                }
            }
        }
    }

    HistoryDialog(
        showHistoryDialog,
        { showHistoryDialog = false },
        customLoadUrl
    )

    BookmarksDialog(
        showBookmarksDialog,
        { showBookmarksDialog = false },
        customLoadUrl
    )

    LocalFilesDialog(
        showLocalFilesDialog,
        { showLocalFilesDialog = false },
        customLoadUrl
    )
}
