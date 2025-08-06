package com.nktnet.webview_kiosk.ui.components

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

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
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp, start = 10.dp, end = 10.dp)
    ) {
        OutlinedTextField(
            value = urlBarText,
            onValueChange = onUrlBarTextChange,
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
                onGo = { triggerLoad(urlBarText.text) }
            ),
            textStyle = LocalTextStyle.current,
            trailingIcon = {
                IconButton(onClick = { triggerLoad(urlBarText.text) }) {
                    Icon(Icons.Default.Search, contentDescription = "Go")
                }
            }
        )

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
        }
    }

    LaunchedEffect(hasFocus) {
        if (hasFocus) {
            onUrlBarTextChange(urlBarText.copy(selection = TextRange(0, urlBarText.text.length)))
        }
    }
}