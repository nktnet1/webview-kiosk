package uk.nktnet.webviewkiosk.ui.components.webview

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.AddressBarActionOption
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.states.WaitingForUnlockStateSingleton
import uk.nktnet.webviewkiosk.utils.handleUserKeyEvent
import uk.nktnet.webviewkiosk.utils.handleUserTouchEvent
import uk.nktnet.webviewkiosk.utils.tryLockTask
import uk.nktnet.webviewkiosk.utils.unlockWithAuthIfRequired
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
    navController: NavController,
    urlBarText: TextFieldValue,
    onUrlBarTextChange: (TextFieldValue) -> Unit,
    hasFocus: Boolean,
    onFocusChanged: (FocusState) -> Unit,
    showFindInPage: () -> Unit,
    addressBarSearch: (String) -> Unit,
    customLoadUrl: (newUrl: String) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val focusManager = LocalFocusManager.current

    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    var menuExpanded by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showLocalFilesDialog by remember { mutableStateOf(false) }
    var allowFocus by remember { mutableStateOf(false) }

    val isLocked by LockStateSingleton.isLocked

    val toastRef = remember { mutableStateOf<Toast?>(null) }
    fun showToast(message: String) {
        toastRef.value?.cancel()
        toastRef.value = Toast.makeText(
            context, message, Toast.LENGTH_SHORT
        ).also { it.show() }
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

    LaunchedEffect(Unit) {
        WaitingForUnlockStateSingleton.unlockSuccess.collect {
            if (menuExpanded) {
                menuExpanded = false
            }
        }
    }

    val menuItems: Map<AddressBarActionOption, @Composable () -> Unit> = remember(
        isLocked,
        systemSettings.historyIndex,
        systemSettings.historyStack.size,
    ) {
        val canGoForward = systemSettings.historyIndex < (systemSettings.historyStack.size - 1)
        val canGoBack = systemSettings.historyIndex > 0

        mapOf(
            AddressBarActionOption.NAVIGATION to {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        4.dp,
                        Alignment.CenterHorizontally
                    )
                ) {
                    IconButton(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        enabled = canGoBack,
                        shape = RectangleShape,
                        onClick = {
                            WebViewNavigation.goBack(customLoadUrl, systemSettings)
                            menuExpanded = false
                        }
                    ) {
                        Icon(
                            modifier = Modifier.padding(start = 8.dp),
                            painter = painterResource(R.drawable.baseline_arrow_back_24),
                            contentDescription = AddressBarActionOption.BACK.label
                        )
                    }

                    IconButton(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RectangleShape,
                        enabled = canGoForward,
                        onClick = {
                            WebViewNavigation.goForward(customLoadUrl, systemSettings)
                            menuExpanded = false
                        }
                    ) {
                        Icon(
                            modifier = Modifier.padding(end = 8.dp),
                            painter = painterResource(R.drawable.baseline_arrow_forward_24),
                            contentDescription = AddressBarActionOption.FORWARD.label
                        )
                    }
                }
            },
            AddressBarActionOption.BACK to {
                AddressBarMenuItem(
                    action = AddressBarActionOption.BACK,
                    enabled = canGoBack,
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
                    enabled = canGoForward,
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
            },
            AddressBarActionOption.FIND to {
                AddressBarMenuItem(
                    action = AddressBarActionOption.FIND,
                    onClick = {
                        showFindInPage()
                        menuExpanded = false
                    },
                    iconRes = R.drawable.find_in_page_24,
                )
            },
            AddressBarActionOption.SETTINGS to {
                AddressBarMenuItem(
                    action = AddressBarActionOption.SETTINGS,
                    onClick = {
                        navController.navigate(Screen.Settings.route)
                        menuExpanded = false
                    },
                    iconRes = R.drawable.baseline_settings_24,
                )
            },
            AddressBarActionOption.LOCK to {
                AddressBarMenuItem(
                    action = AddressBarActionOption.LOCK,
                    onClick = {
                        tryLockTask(activity, ::showToast)
                        menuExpanded = false
                    },
                    iconRes = R.drawable.baseline_lock_24,
                )
            },
            AddressBarActionOption.UNLOCK to {
                AddressBarMenuItem(
                    action = AddressBarActionOption.UNLOCK,
                    onClick = {
                        activity?.let {
                            unlockWithAuthIfRequired(activity, ::showToast)
                        }
                        menuExpanded = false
                    },
                    iconRes = R.drawable.baseline_lock_open_24,
                )
            },
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(
                userSettings.addressBarSize.paddingDp,
            )
            .fillMaxWidth()
            .focusProperties { canFocus = allowFocus }
    ) {
        BasicTextField(
            value = urlBarText,
            onValueChange = { onUrlBarTextChange(it) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = userSettings.addressBarSize.fontSizeSp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onFocusChanged(onFocusChanged)
                .focusProperties { canFocus = allowFocus },
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = {
                if (urlBarText.text.isNotBlank()) {
                    addressBarSearch(urlBarText.text)
                }
            }),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(userSettings.addressBarSize.heightDp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            RoundedCornerShape(50)
                        )
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 0.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (urlBarText.text.isEmpty()) {
                            Text(
                                text = "Search",
                                style = LocalTextStyle.current.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    fontSize = userSettings.addressBarSize.fontSizeSp,
                                    fontStyle = FontStyle.Italic,
                                )
                            )
                        }
                        innerTextField()
                    }

                    IconButton(
                        modifier = Modifier
                            .size(userSettings.addressBarSize.searchIconSizeDp),
                        onClick = { addressBarSearch(urlBarText.text) },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_search_24),
                            contentDescription = "Search",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(userSettings.addressBarSize.searchIconPaddingDp),
                        )
                    }
                }
            }
        )

        val enabledActions = remember(userSettings, isLocked) {
            userSettings.addressBarActions.filter { action ->
                when (action) {
                    AddressBarActionOption.NAVIGATION -> userSettings.allowBackwardsNavigation
                    AddressBarActionOption.BACK -> userSettings.allowBackwardsNavigation
                    AddressBarActionOption.FORWARD -> userSettings.allowBackwardsNavigation
                    AddressBarActionOption.REFRESH -> userSettings.allowRefresh
                    AddressBarActionOption.HOME -> userSettings.allowGoHome
                    AddressBarActionOption.HISTORY -> userSettings.allowHistoryAccess
                    AddressBarActionOption.BOOKMARK -> userSettings.allowBookmarkAccess
                    AddressBarActionOption.FILES -> userSettings.allowLocalFiles
                    AddressBarActionOption.SETTINGS -> !isLocked
                    AddressBarActionOption.LOCK -> !isLocked
                    AddressBarActionOption.UNLOCK -> isLocked
                    AddressBarActionOption.FIND -> true
                }
            }
        }

        if (enabledActions.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .offset(x = userSettings.addressBarSize.paddingDp / 4)
            ) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        menuExpanded = true
                    },
                    modifier = Modifier
                        .height(userSettings.addressBarSize.moreVertHeightDp)
                        .aspectRatio(2f / 3f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.outline_more_vert_24),
                        contentDescription = "Menu",
                        modifier = Modifier.fillMaxSize()
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
