package uk.nktnet.webviewkiosk.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.utils.normaliseInfoText

@Composable
fun SettingsDeviceOwnerScreen(navController: NavController) {
    val context = LocalContext.current
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(
        context.packageName,
        WebviewKioskAdminReceiver::class.java.name
    )

    var isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)

    var showDeviceOwnerRemovalDialog by remember { mutableStateOf(false) }

    if (showDeviceOwnerRemovalDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceOwnerRemovalDialog = false },
            title = { Text("Deactivate Device Owner") },
            text = {
                Text(
                    normaliseInfoText("""
                        Are you sure you want to unset ${Constants.APP_NAME} as the
                        device owner?

                        This means Lock Task Mode will no longer be available, and
                        the kiosk lock feature will default to Screen Pinning.
                    """.trimIndent())
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeviceOwnerRemovalDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            try {
                                @Suppress("DEPRECATION")
                                dpm.clearProfileOwner(adminComponent)
                            } catch (e: Throwable) {
                                e.printStackTrace()
                            }
                        }
                        try {
                            @Suppress("DEPRECATION")
                            dpm.clearDeviceOwnerApp(context.packageName)
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                        isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
                    }
                ) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { showDeviceOwnerRemovalDialog = false }) { Text("No") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
            .verticalScroll(rememberScrollState())
    ) {
        SettingLabel(navController = navController, label = "Device Owner")

        SettingDivider()

        if (!isDeviceOwner) {
            Text(
                text = "${Constants.APP_NAME} is not set as the device owner.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Button(
            enabled = isDeviceOwner,
            onClick = {
                showDeviceOwnerRemovalDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) { Text("Deactivate Device Owner") }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
