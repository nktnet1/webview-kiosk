package com.nktnet.webview_kiosk.ui.components.setting

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.utils.navigateToWebViewScreen

const val ICON_OFFSET = 8

@Composable
fun SettingLabel(
    navController: NavController,
    label: String,
    showBackIcon: Boolean = true,
    endIcon: @Composable (() -> Unit)? = {
        IconButton(
            onClick = {
                navigateToWebViewScreen(navController)
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.baseline_exit_to_app_24),
                contentDescription = "Go to WebView",
            )
        }
    },
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBackIcon) {
                IconButton(
                    onClick = {
                        if (!navController.popBackStack()) {
                            navigateToWebViewScreen(navController)
                        }
                    },
                    modifier = Modifier
                        .padding(end = 1.dp)
                        .offset(-ICON_OFFSET.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_arrow_back_24),
                        contentDescription = "Back",
                    )
                }
            }

            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (endIcon != null) {
            Box(
                modifier = Modifier.offset(ICON_OFFSET.dp)
            ) {
                endIcon()
            }
        }
    }
}
