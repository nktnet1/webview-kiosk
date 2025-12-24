package com.nktnet.webview_kiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.utils.navigateToWebViewScreen

@Composable
fun AdminRestrictionsChangedScreen(
    navController: NavController,
) {
    val spacerHeight = LocalWindowInfo.current.containerSize.height * 0.05f
    var remaining by remember { mutableIntStateOf(10) }

    LaunchedEffect(Unit) {
        while (remaining > 1) {
            remaining -= 1
            delay(1000)
        }
        navigateToWebViewScreen(navController)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 4.dp)
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(spacerHeight.dp))
            Text(
                text = stringResource(R.string.admin_restrictions_changed_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = """
                    Your IT Administrator has applied new configurations.

                    Some settings may have been restricted.
                """.trimIndent(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { navigateToWebViewScreen(navController) },
                modifier = Modifier
                    .sizeIn(maxWidth = 200.dp)
                    .fillMaxWidth()
                    .height(60.dp),
            ) {
                Text("I understand.")
            }
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Returning automatically in ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontStyle = FontStyle.Italic,
                )
                Text(
                    text = "$remaining",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = if (remaining < 3) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                )
                Text(
                    text = " second${if (remaining == 1) "" else "s"}.",
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
