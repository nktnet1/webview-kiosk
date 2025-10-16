package uk.nktnet.webviewkiosk.ui.components.setting.fields

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R

@Composable
fun <T> GenericSettingFieldItem(
    label: String,
    value: T,
    onClick: () -> Unit,
    restricted: Boolean,
    description: @Composable (T) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
