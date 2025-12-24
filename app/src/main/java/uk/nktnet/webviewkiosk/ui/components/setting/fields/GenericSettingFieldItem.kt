package com.nktnet.webview_kiosk.ui.components.setting.fields

import android.content.ClipData
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.nktnet.webview_kiosk.R

@Composable
fun <T> GenericSettingFieldItem(
    label: String,
    value: T,
    onClick: () -> Unit,
    onLongClick: ((value: T) -> Unit)? = null,
    restricted: Boolean,
    description: @Composable (T) -> Unit
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = {
                    if (onLongClick != null) {
                        onLongClick.invoke(value)
                    } else {
                        scope.launch {
                            val clipData = ClipData.newPlainText(label, value.toString())
                            clipboard.setClipEntry(clipData.toClipEntry())
                        }
                    }
                }
            )
            .padding(top = 8.dp, start = 2.dp, end = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth(0.9f)) {
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (restricted) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "[Restricted]",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                description(value)
            }
            Icon(
                painter = if (restricted) {
                    painterResource(R.drawable.baseline_remove_red_eye_24)
                } else {
                    painterResource(R.drawable.baseline_edit_24)
                },
                contentDescription = "Edit",
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )
    }
}
