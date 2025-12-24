package com.nktnet.webview_kiosk.ui.components.apps

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.nktnet.webview_kiosk.R

@Composable
fun AppSearchBar(
    searchQuery: TextFieldValue,
    onSearchChange: (String) -> Unit,
    onSortToggle: () -> Unit,
    filteredAppCount: Int,
    ascending: Boolean,
) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = searchQuery.text,
            onValueChange = onSearchChange,
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .weight(1f)
                .height(43.dp),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            MaterialTheme.shapes.small
                        )
                        .padding(start = 14.dp, end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.text.isEmpty()) {
                            Text(
                                text = if (filteredAppCount > 1) {
                                    "Search $filteredAppCount apps"
                                } else if (filteredAppCount == 1) {
                                    "Search 1 app."
                                } else {
                                    "No apps available."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontStyle = FontStyle.Italic
                                )
                            )
                        }
                        innerTextField()
                    }

                    IconButton(
                        enabled = searchQuery.text.isNotEmpty(),
                        onClick = { onSearchChange("") },
                        modifier = Modifier
                            .size(26.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_clear_24),
                            contentDescription = "Clear",
                            modifier = Modifier
                                .size(24.dp)
                                .padding(2.dp),
                            )
                    }
                }
            }
        )

        IconButton(
            onClick = onSortToggle,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .graphicsLayer(scaleX = 0.9f, scaleY = 0.9f)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    MaterialTheme.shapes.small
                )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_sort_24),
                contentDescription = "Sort Order",
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer(
                        scaleY = if (ascending) -1f else 1f,
                        scaleX = -1f
                    )
            )
        }
    }
}
