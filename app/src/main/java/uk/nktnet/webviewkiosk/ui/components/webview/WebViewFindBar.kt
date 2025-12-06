package com.nktnet.webview_kiosk.ui.components.webview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.webkit.WebView
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.nktnet.webview_kiosk.R
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction

@Composable
private fun RoundIconButton(
    enabled: Boolean = true,
    iconRes: Int,
    contentDesc: String,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = iconTint,
        )
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDesc,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun WebViewFindBar(
    webView: WebView,
    isActiveFindInPage: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
) {
    if (!isActiveFindInPage) return

    var query by remember { mutableStateOf("") }
    var currentMatch by remember { mutableIntStateOf(0) }
    var totalMatches by remember { mutableIntStateOf(0) }
    var doneSearching by remember { mutableStateOf(false) }

    LaunchedEffect(webView) {
        webView.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            currentMatch = activeMatchOrdinal + 1
            totalMatches = numberOfMatches
            doneSearching = isDoneCounting
        }
    }

    LaunchedEffect(Unit) {
        if (isActiveFindInPage) {
            focusRequester.requestFocus()
        }
    }

    Row(
        modifier = modifier
            .wrapContentSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BasicTextField(
            value = query,
            onValueChange = {
                doneSearching = false
                query = it
                webView.findAllAsync(it)
            },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            ),
            modifier = Modifier
                .focusRequester(focusRequester)
                .weight(1f),
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { webView.findNext(true) }
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .wrapContentSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .height(45.dp)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                "Find in page",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                ),
                            )
                        }
                        innerTextField()
                    }

                    if (query.isNotEmpty()) {
                        val displayMatch = if (totalMatches == 0) {
                            0
                        } else {
                            currentMatch
                        }
                        Text(
                            "$displayMatch/$totalMatches",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (doneSearching && totalMatches == 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                    MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = if (doneSearching) 1f else 0.5f
                                    )
                                },
                            ),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RoundIconButton(
                enabled = totalMatches > 1,
                iconRes = R.drawable.keyboard_arrow_up_24,
                contentDesc = "Next",
                onClick = { webView.findNext(false) }
            )
            RoundIconButton(
                enabled = totalMatches > 1,
                iconRes = R.drawable.keyboard_arrow_down_24,
                contentDesc = "Previous",
                onClick = { webView.findNext(true) }
            )
            RoundIconButton(
                iconRes = R.drawable.baseline_clear_24,
                iconTint = MaterialTheme.colorScheme.error,
                contentDesc = "Close",
                onClick = {
                    onActiveChange(false)
                    webView.clearMatches()
                    currentMatch = 0
                    totalMatches = 0
                }
            )
        }
    }
}
