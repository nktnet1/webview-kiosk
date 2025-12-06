package uk.nktnet.webviewkiosk.ui.components.webview

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
import uk.nktnet.webviewkiosk.R
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction

@Composable
private fun RoundIconButton(
    iconRes: Int,
    contentDesc: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDesc,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun WebViewFindBar(
    webView: WebView,
    showFindInPage: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!showFindInPage) return

    var query by remember { mutableStateOf("") }
    var currentMatch by remember { mutableIntStateOf(0) }
    var totalMatches by remember { mutableIntStateOf(0) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(webView) {
        webView.setFindListener { activeOrdinal, matches, _ ->
            currentMatch = activeOrdinal + 1
            totalMatches = matches
        }
    }

    LaunchedEffect(Unit) {
        if (showFindInPage) {
            focusRequester.requestFocus()
        }
    }

    Row(
        modifier = modifier
            .wrapContentSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BasicTextField(
            value = query,
            onValueChange = {
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
                        .height(50.dp)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                "Find in page…",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                ),
                            )
                        }
                        innerTextField()
                    }

                    if (query.isNotEmpty()) {
                        val displayMatch = if (totalMatches == 0) 0 else currentMatch
                        Text(
                            "$displayMatch/$totalMatches",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (totalMatches == 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            ),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        )


        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RoundIconButton(
                iconRes = R.drawable.keyboard_arrow_up_24,
                contentDesc = "Next",
                onClick = { webView.findNext(false) }
            )
            RoundIconButton(
                iconRes = R.drawable.keyboard_arrow_down_24,
                contentDesc = "Previous",
                onClick = { webView.findNext(true) }
            )
            RoundIconButton(
                iconRes = R.drawable.baseline_clear_24,
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
