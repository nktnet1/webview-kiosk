package uk.nktnet.webviewkiosk.ui.components.setting

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R

@Composable
fun DebugLogsButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
    ) {
        Text(stringResource(R.string.settings_mqtt_debug_log_title))
    }
}
