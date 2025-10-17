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

@Composable
fun SettingLabel(
    navController: NavController,
    label: String,
    showBackIcon: Boolean = true,
    endIcon: @Composable (() -> Unit)? = {
        Icon(
            painter = painterResource(R.drawable.baseline_exit_to_app_24),
            contentDescription = "Go to WebView",
            modifier = Modifier.size(24.dp)
        )
    },
    onEndIconClick: (() -> Unit)? = {
        navigateToWebViewScreen(navController)
    }
) {
    val sideOffset = 20.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp)
            .height(55.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBackIcon) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(64.dp).offset(x = -sideOffset),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_arrow_back_24),
                        contentDescription = "Back",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.offset(x = -sideOffset)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (endIcon != null) {
            IconButton(
                onClick = { onEndIconClick?.invoke() },
                modifier = Modifier.size(64.dp).offset(x = sideOffset),
            ) {
                endIcon()
            }
        }
    }
}
