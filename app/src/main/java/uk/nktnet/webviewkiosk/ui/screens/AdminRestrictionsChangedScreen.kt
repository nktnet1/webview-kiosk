package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.utils.navigateToWebViewScreen

@Composable
fun AdminRestrictionsChangedScreen(
    navController: NavController,
) {
    val spacerHeight = LocalWindowInfo.current.containerSize.height * 0.05f
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeContent)
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(spacerHeight.dp))
            Text(
                text = "New Configurations",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = """
                    Your IT Administrator has updated ${Constants.APP_NAME}'s configuration.
                    Some settings may have been restricted.
                """.trimIndent(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    navigateToWebViewScreen(navController)
                },
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Text("I understand.")
            }
        }
    }
}
