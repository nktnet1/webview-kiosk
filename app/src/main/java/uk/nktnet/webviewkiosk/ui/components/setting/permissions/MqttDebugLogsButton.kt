package uk.nktnet.webviewkiosk.ui.components.setting.permissions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.config.Screen

@Composable
fun MqttDebugLogsButton (navController: NavController) {
    Button(
        onClick = {
            navController.navigate(Screen.SettingsMqttDebug.route)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Debug Logs")
    }
}
