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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import uk.nktnet.webviewkiosk.R
import androidx.compose.runtime.mutableIntStateOf
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
    findInPageActive: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!findInPageActive) return

    var query by remember { mutableStateOf("") }
    var activeMatch by remember { mutableIntStateOf(0) }
    var totalMatches by remember { mutableIntStateOf(0) }

    LaunchedEffect(webView) {
        webView.setFindListener { activeOrdinal, matches, _ ->
            activeMatch = activeOrdinal + 1
            totalMatches = matches
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
        TextField(
            value = query,
            singleLine = true,
            onValueChange = {
                query = it
                webView.findAllAsync(it)
            },
            placeholder = { Text("Find in page…") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { webView.findNext(true) }
            ),
            trailingIcon = {
                if (totalMatches > 0) {
                    Text(
                        "$activeMatch/$totalMatches",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RoundIconButton(
                iconRes = R.drawable.keyboard_arrow_up_24,
                contentDesc = "Next",
                onClick = { webView.findNext(true) }
            )
            RoundIconButton(
                iconRes = R.drawable.keyboard_arrow_down_24,
                contentDesc = "Previous",
                onClick = { webView.findNext(false) }
            )
            RoundIconButton(
                iconRes = R.drawable.baseline_clear_24,
                contentDesc = "Close",
                onClick = {
                    onActiveChange(false)
                    webView.clearMatches()
                    activeMatch = 0
                    totalMatches = 0
                }
            )
        }
    }
}
