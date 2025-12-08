package uk.nktnet.webviewkiosk.ui.components.apps

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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R


@Composable
fun AppSearchBar(
    searchQuery: TextFieldValue,
    onSearchChange: (String) -> Unit,
    onSortToggle: () -> Unit,
    appCount: Int,
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
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodySmall.fontSize
            ),
            modifier = Modifier
                .weight(1f)
                .height(43.dp),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 12.dp)
                ) {
                    if (searchQuery.text.isEmpty()) {
                        Text(
                            text = "Search $appCount apps",
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                fontStyle = FontStyle.Italic
                            )
                        )
                    }
                    innerTextField()
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
