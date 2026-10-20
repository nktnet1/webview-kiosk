package uk.nktnet.webviewkiosk.ui.components.webview

import android.content.ClipData
import android.webkit.WebView
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.WebviewControlActionOption
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.states.WaitingForUnlockStateSingleton
import uk.nktnet.webviewkiosk.utils.handleUserKeyEvent
import uk.nktnet.webviewkiosk.utils.handleUserTouchEvent
import uk.nktnet.webviewkiosk.utils.requestFocusWhenPlaced
import uk.nktnet.webviewkiosk.utils.tryLockTask
import uk.nktnet.webviewkiosk.utils.unlockWithAuthIfRequired
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation

// A URL can be a data: URI or contain an arbitrarily large query/fragment. Never
// hand those strings to Compose's single-line text intrinsics during normal
// WebView drawing. Retain the original string for navigation and clipboard use.
internal const val MAX_ADDRESS_BAR_EDITABLE_LENGTH = 2_048
private const val MAX_ADDRESS_BAR_PREVIEW_LENGTH = 256

internal fun boundedAddressBarPreview(value: String): String {
    if (value.length <= MAX_ADDRESS_BAR_PREVIEW_LENGTH) {
        return value
    }
    val end =
        if (Character.isHighSurrogate(value[MAX_ADDRESS_BAR_PREVIEW_LENGTH - 1])) {
            MAX_ADDRESS_BAR_PREVIEW_LENGTH - 1
        } else {
            MAX_ADDRESS_BAR_PREVIEW_LENGTH
        }
    return value.substring(0, end) + "\u2026"
}

@Composable
private fun AddressBarCopyButton(iconSize: Dp, onCopy: () -> Unit) {
    IconButton(onClick = onCopy, modifier = Modifier.size(iconSize)) {
        Icon(
            painter = painterResource(R.drawable.baseline_content_copy_24),
            contentDescription = "Copy full URL",
            modifier = Modifier.fillMaxSize().padding(6.dp),
        )
    }
}

@Composable
private fun AddressBarSearchButton(
    onSearch: () -> Unit,
    iconSize: Dp,
    iconPadding: Dp,
) {
    IconButton(modifier = Modifier.size(iconSize), onClick = onSearch) {
        Icon(
            painter = painterResource(R.drawable.baseline_search_24),
            contentDescription = "Search",
            modifier = Modifier.fillMaxSize().padding(iconPadding),
        )
    }
}

@Composable
private fun AddressBarMenuItem(
    action: WebviewControlActionOption,
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
                contentDescription = action.label,
            )
        },
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
    showHistoryDialog: () -> Unit,
    showBookmarkDialog: () -> Unit,
    showFilesDialog: () -> Unit,
    showAppsDialog: () -> Unit,
    webView: WebView,
    customLoadUrl: (newUrl: String) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var replacingOversizedUrl by remember { mutableStateOf(false) }
    var replacementHadFocus by remember { mutableStateOf(false) }
    var pastedOversizedUrl by remember { mutableStateOf(false) }
    val oversizedUrl = urlBarText.text.length > MAX_ADDRESS_BAR_EDITABLE_LENGTH
    // Avoid comparing very large URL keys on every recomposition.
    val urlPreview = boundedAddressBarPreview(urlBarText.text)
    val copyFullUrl: () -> Unit = {
        val currentUrl = urlBarText.text
        scope.launch {
            clipboard.setClipEntry(
                ClipData.newPlainText("Page URL", currentUrl).toClipEntry()
            )
        }
    }

    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    var menuExpanded by remember { mutableStateOf(false) }

    var allowFocus by remember { mutableStateOf(false) }

    val isLocked by LockStateSingleton.isLocked

    LaunchedEffect(Unit) {
        delay(200.milliseconds)
        allowFocus = true
    }

    LaunchedEffect(hasFocus) {
        if (hasFocus && !oversizedUrl && urlBarText.selection.collapsed) {
            onUrlBarTextChange(
                urlBarText.copy(
                    selection =
                        TextRange(
                            start = 0,
                            end = urlBarText.text.length,
                        )
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        WaitingForUnlockStateSingleton.unlockSuccess.collect {
            if (menuExpanded) {
                menuExpanded = false
            }
        }
    }

    val menuItems: Map<WebviewControlActionOption, @Composable () -> Unit> =
        remember(
            isLocked,
            systemSettings.historyIndex,
            systemSettings.historyStack.size,
        ) {
            val lastIndex = systemSettings.historyStack.lastIndex
            val canGoForward = systemSettings.historyIndex in 0 until lastIndex
            val canGoBack = systemSettings.historyIndex in 1..lastIndex

            mapOf(
                WebviewControlActionOption.NAVIGATION to
                    {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    4.dp,
                                    Alignment.CenterHorizontally,
                                ),
                        ) {
                            IconButton(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                enabled = canGoBack,
                                shape = RectangleShape,
                                onClick = {
                                    WebViewNavigation.goBack(
                                        customLoadUrl,
                                        systemSettings,
                                    )
                                    menuExpanded = false
                                },
                            ) {
                                Icon(
                                    modifier = Modifier.padding(start = 8.dp),
                                    painter =
                                        painterResource(
                                            R.drawable.baseline_arrow_back_24
                                        ),
                                    contentDescription =
                                        WebviewControlActionOption.BACK.label,
                                )
                            }

                            IconButton(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = RectangleShape,
                                enabled = canGoForward,
                                onClick = {
                                    WebViewNavigation.goForward(
                                        customLoadUrl,
                                        systemSettings,
                                    )
                                    menuExpanded = false
                                },
                            ) {
                                Icon(
                                    modifier = Modifier.padding(end = 8.dp),
                                    painter =
                                        painterResource(
                                            R.drawable.baseline_arrow_forward_24
                                        ),
                                    contentDescription =
                                        WebviewControlActionOption.FORWARD.label,
                                )
                            }
                        }
                    },
                WebviewControlActionOption.BACK to
                    {
                        AddressBarMenuItem(
                            action = WebviewControlActionOption.BACK,
                            enabled = canGoBack,
                            onClick = {
                                WebViewNavigation.goBack(
                                    customLoadUrl,
                                    systemSettings,
                                )
                                menuExpanded = false
                            },
                            iconRes = R.drawable.baseline_arrow_back_24,
                        )
                    },
                WebviewControlActionOption.FORWARD to
                    {
                        AddressBarMenuItem(
                            action = WebviewControlActionOption.FORWARD,
                            enabled = canGoForward,
                            onClick = {
                                WebViewNavigation.goForward(
                                    customLoadUrl,
                                    systemSettings,
                                )
                                menuExpanded = false
                            },
                            iconRes = R.drawable.baseline_arrow_forward_24,
                        )
                    },
                WebviewControlActionOption.REFRESH to
                    {
                        AddressBarMenuItem(
                            action = WebviewControlActionOption.REFRESH,
                            onClick = {
                                WebViewNavigation.refresh(
                                    customLoadUrl,
                                    systemSettings,
                                    userSettings,
                                )
                                menuExpanded = false
                            },
                            iconRes = R.drawable.baseline_refresh_24,
                        )
                    },
                WebviewControlActionOption.HOME to
                    {
                        AddressBarMenuItem(
                            action = WebviewControlActionOption.HOME,
                            onClick = {
                                WebViewNavigation.goHome(
                                    customLoadUrl,
                                    systemSettings,
                                    userSettings,
                                )
                                menuExpanded = false
                            },
                            iconRes = R.drawable.baseline_home_24,
                        )
                    },
                WebviewControlActionOption.HISTORY to
                    {
                        AddressBarMenuItem(
                            action = WebviewControlActionOption.HISTORY,
                            onClick = {
                                showHistoryDialog()
                                menuExpanded = false
                            },
                            iconRes = R.drawable.outline_history_24,
                        )
                    },
                WebviewControlActionOption.BOOKMARK to
                    {
                        AddressBarMenuItem(
                            action = WebviewControlActionOption.BOOKMARK,
                            onClick = {
                                showBookmarkDialog()
                                menuExpanded = false
                            },
                            iconRes = R.drawable.outline_bookmark_24,
                        )
                    },
                WebviewControlActionOption.FILES to
                    {
                        AddressBarMenuItem(
                            action = WebviewControlActionOption.FILES,
                            onClick = {
                                showFilesDialog()
                                menuExpanded = false
                            },
                            iconRes = R.drawable.outline_folder_24,
                        )
                    },
                WebviewControlActionOption.FIND to
                    {
                        AddressBarMenuItem(
                            action = WebviewControlActionOption.FIND,
                            onClick = {
                                showFindInPage()
                                menuExpanded = false
                            },
                            iconRes = R.drawable.find_in_page_24,
                        )
                    },
                WebviewControlActionOption.APPS to
                    {
                        AddressBarMenuItem(
                            action = WebviewControlActionOption.APPS,
                            onClick = {
                                showAppsDialog()
                                menuExpanded = false
                            },
                            iconRes = R.drawable.apps_24px,
                        )
                    },
                WebviewControlActionOption.SCROLL_TOP to
                    {
                        AddressBarMenuItem(
                            action = WebviewControlActionOption.SCROLL_TOP,
                            onClick = {
                                webView.pageUp(true)
                                menuExpanded = false
                            },
                            iconRes = R.drawable.keyboard_double_arrow_up_24,
                        )
                    },
                WebviewControlActionOption.SCROLL_BOT to
                    {
                        AddressBarMenuItem(
                            action = WebviewControlActionOption.SCROLL_BOT,
                            onClick = {
                                webView.pageDown(true)
                                menuExpanded = false
                            },
                            iconRes = R.drawable.keyboard_double_arrow_down_24,
                        )
                    },
                WebviewControlActionOption.SETTINGS to
                    {
                        AddressBarMenuItem(
                            action = WebviewControlActionOption.SETTINGS,
                            onClick = {
                                navController.navigate(Screen.Settings.route)
                                menuExpanded = false
                            },
                            iconRes = R.drawable.baseline_settings_24,
                        )
                    },
                WebviewControlActionOption.LOCK to
                    {
                        AddressBarMenuItem(
                            action = WebviewControlActionOption.LOCK,
                            onClick = {
                                tryLockTask(activity)
                                menuExpanded = false
                            },
                            iconRes = R.drawable.baseline_lock_24,
                        )
                    },
                WebviewControlActionOption.UNLOCK to
                    {
                        AddressBarMenuItem(
                            action = WebviewControlActionOption.UNLOCK,
                            onClick = {
                                activity?.let {
                                    unlockWithAuthIfRequired(activity)
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
        modifier =
            Modifier.padding(userSettings.addressBarSize.paddingDp)
                .fillMaxWidth()
                .focusProperties { canFocus = allowFocus },
    ) {
        if (oversizedUrl && !replacingOversizedUrl) {
            Row(
                modifier =
                    Modifier.weight(1f)
                        .height(userSettings.addressBarSize.heightDp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            RoundedCornerShape(50),
                        )
                        .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.weight(1f).fillMaxHeight().clickable(
                            onClickLabel = "Enter a replacement URL"
                        ) {
                            pastedOversizedUrl = false
                            replacingOversizedUrl = true
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = urlPreview,
                        style =
                            LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = userSettings.addressBarSize.fontSizeSp,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AddressBarCopyButton(
                    iconSize = userSettings.addressBarSize.searchIconSizeDp,
                    onCopy = copyFullUrl,
                )
                AddressBarSearchButton(
                    onSearch = { addressBarSearch(urlBarText.text) },
                    iconSize = userSettings.addressBarSize.searchIconSizeDp,
                    iconPadding = userSettings.addressBarSize.searchIconPaddingDp,
                )
            }
        } else {
            BasicTextField(
                // For oversized URLs, offer a replacement field rather than
                // passing the entire URL to CoreTextField's intrinsic measurer.
                // Pasted long addresses still live in urlBarText for Search.
                value =
                    when {
                        oversizedUrl -> TextFieldValue("")
                        else -> urlBarText
                    },
                onValueChange = { value ->
                    onUrlBarTextChange(value)
                    if (value.text.length > MAX_ADDRESS_BAR_EDITABLE_LENGTH) {
                        replacingOversizedUrl = true
                        pastedOversizedUrl = true
                    }
                },
                singleLine = true,
                textStyle =
                    LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = userSettings.addressBarSize.fontSizeSp,
                    ),
                modifier =
                    Modifier.fillMaxWidth()
                        .weight(1f)
                        .requestFocusWhenPlaced(
                            focusRequester,
                            enabled = replacingOversizedUrl && allowFocus,
                            requestKey = replacingOversizedUrl,
                        )
                        .onFocusChanged {
                            if (replacingOversizedUrl) {
                                if (it.isFocused) {
                                    replacementHadFocus = true
                                } else if (replacementHadFocus) {
                                    replacingOversizedUrl = false
                                    replacementHadFocus = false
                                    pastedOversizedUrl = false
                                }
                            }
                            onFocusChanged(it)
                        }
                        .focusProperties { canFocus = allowFocus },
                keyboardOptions =
                    KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            if (urlBarText.text.isNotBlank()) {
                                addressBarSearch(urlBarText.text)
                            }
                        }
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(userSettings.addressBarSize.heightDp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = 0.5f
                                    ),
                                    RoundedCornerShape(50),
                                )
                                .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (oversizedUrl || urlBarText.text.isEmpty()) {
                                Text(
                                    text =
                                        when {
                                            oversizedUrl && pastedOversizedUrl ->
                                                "Long URL ready — press Search"
                                            oversizedUrl -> "Enter a replacement URL"
                                            else -> "Search"
                                        },
                                    style =
                                        LocalTextStyle.current.copy(
                                            color =
                                                MaterialTheme.colorScheme.onSurface
                                                    .copy(alpha = 0.3f),
                                            fontSize =
                                                userSettings.addressBarSize
                                                    .fontSizeSp,
                                            fontStyle = FontStyle.Italic,
                                        ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                        if (oversizedUrl) {
                            AddressBarCopyButton(
                                iconSize =
                                    userSettings.addressBarSize.searchIconSizeDp,
                                onCopy = copyFullUrl,
                            )
                        }
                        AddressBarSearchButton(
                            onSearch = { addressBarSearch(urlBarText.text) },
                            iconSize = userSettings.addressBarSize.searchIconSizeDp,
                            iconPadding =
                                userSettings.addressBarSize.searchIconPaddingDp,
                        )
                    }
                },
            )
        }

        val enabledActions =
            remember(userSettings, isLocked) {
                userSettings.addressBarActions.filter { action ->
                    when (action) {
                        WebviewControlActionOption.NAVIGATION ->
                            userSettings.allowBackwardsNavigation
                        WebviewControlActionOption.BACK ->
                            userSettings.allowBackwardsNavigation
                        WebviewControlActionOption.FORWARD ->
                            userSettings.allowBackwardsNavigation
                        WebviewControlActionOption.REFRESH ->
                            userSettings.allowRefresh
                        WebviewControlActionOption.HOME -> userSettings.allowGoHome
                        WebviewControlActionOption.HISTORY ->
                            userSettings.allowHistoryAccess
                        WebviewControlActionOption.BOOKMARK ->
                            userSettings.allowBookmarkAccess
                        WebviewControlActionOption.FILES ->
                            userSettings.allowLocalFiles
                        WebviewControlActionOption.SETTINGS -> !isLocked
                        WebviewControlActionOption.LOCK -> !isLocked
                        WebviewControlActionOption.UNLOCK -> isLocked
                        WebviewControlActionOption.APPS,
                        WebviewControlActionOption.FIND,
                        WebviewControlActionOption.SCROLL_TOP,
                        WebviewControlActionOption.SCROLL_BOT -> true
                    }
                }
            }

        if (enabledActions.isNotEmpty()) {
            Box(
                modifier =
                    Modifier.padding(2.dp)
                        .offset(x = userSettings.addressBarSize.paddingDp / 4)
            ) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        menuExpanded = true
                    },
                    modifier =
                        Modifier.height(userSettings.addressBarSize.moreVertHeightDp)
                            .aspectRatio(2f / 3f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.outline_more_vert_24),
                        contentDescription = "Menu",
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier =
                        Modifier.handleUserTouchEvent()
                            .handleUserKeyEvent(context, menuExpanded),
                ) {
                    enabledActions.forEach { key ->
                        menuItems[key]?.invoke()
                    }
                }
            }
        }
    }
}
