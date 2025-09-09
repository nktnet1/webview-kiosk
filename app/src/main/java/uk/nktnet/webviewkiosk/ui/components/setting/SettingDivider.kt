package uk.nktnet.webviewkiosk.ui.components.setting

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider

@Composable
fun SettingDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(top = 8.dp, bottom = 16.dp),
        thickness = 2.dp,
        color = MaterialTheme.colorScheme.onSurface
    )
}
