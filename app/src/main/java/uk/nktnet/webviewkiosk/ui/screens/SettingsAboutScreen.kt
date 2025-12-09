package uk.nktnet.webviewkiosk.ui.screens

import android.content.ClipData
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.utils.getSystemInfo
import uk.nktnet.webviewkiosk.utils.humanReadableSize
import uk.nktnet.webviewkiosk.utils.openAppDetailsSettings

@Composable
fun InfoItem(label: String, value: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .combinedClickable(
                onClick = {
                    ToastManager.show(context, "Tap and hold to copy value.")
                },
                onLongClick = {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipData.newPlainText(label, value).toClipEntry()
                        )
                    }
                }
            )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsAboutScreen(navController: NavController) {
    val context = LocalContext.current
    val systemInfo = remember { getSystemInfo(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeContent)
            .padding(horizontal = 16.dp)
    ) {
        SettingLabel(navController = navController, label = "About")
        SettingDivider()

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                onClick = { openAppDetailsSettings(context) }
            ) {
                Text(
                    text = "Open App Info",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Text(
                text = "App",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            InfoItem(label = "Name", value = systemInfo.app.name)
            InfoItem(label = "Package", value = systemInfo.app.packageName)
            InfoItem(
                label = "Version",
                value = "${systemInfo.app.versionCode} (${systemInfo.app.versionName})",
            )
            InfoItem(label = "Min SDK", value = systemInfo.app.minSdk.toString())
            InfoItem(label = "Target SDK", value = systemInfo.app.targetSdk.toString())
            InfoItem(label = "Debug Build", value = systemInfo.app.isDebug.toString())
            InfoItem(label = "Installer", value = systemInfo.app.installer ?: "N/A")
            InfoItem(label = "Device Owner", value = systemInfo.app.isDeviceOwner.toString())
            InfoItem(
                label = "Lock Task Permitted",
                value = systemInfo.app.isLockTaskPermitted.toString(),
            )
            InfoItem(
                label = "Dhizuku Permission Granted",
                value = systemInfo.app.dhizukuPermissionGranted.toString(),
            )
            InfoItem(label = "Instance ID", value = systemInfo.app.instanceId)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Device",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            InfoItem(
                label = "Android Version",
                value = "${systemInfo.device.releaseVersion} (SDK ${systemInfo.device.sdkVersion})",
            )
            InfoItem(
                label = "WebView Version",
                value = systemInfo.device.webViewVersion ?: "N/A",
            )
            InfoItem(
                label = "Screen / Display",
                value = "${systemInfo.device.screenWidth} x ${systemInfo.device.screenHeight} px, density: ${systemInfo.device.screenDensity}",
            )
            InfoItem(
                label = "Managed Profile",
                value = systemInfo.device.isManagedProfile?.toString() ?: "N/A",
            )
            InfoItem(label = "Time Zone", value = systemInfo.device.timeZone)
            InfoItem(label = "Locale", value = systemInfo.device.locale)
            InfoItem(label = "Total RAM", value = humanReadableSize(context, systemInfo.device.totalMemory))
            InfoItem(label = "Total Storage", value = humanReadableSize(context, systemInfo.device.totalStorage))
            InfoItem(label = "Model", value = systemInfo.device.model)
            InfoItem(label = "Manufacturer", value = systemInfo.device.manufacturer)
            InfoItem(label = "Brand", value = systemInfo.device.brand)
            InfoItem(label = "Device", value = systemInfo.device.device)
            InfoItem(label = "Product", value = systemInfo.device.product)
            InfoItem(label = "Hardware", value = systemInfo.device.hardware)
            InfoItem(label = "Board", value = systemInfo.device.board)
            InfoItem(label = "Bootloader", value = systemInfo.device.bootloader)
            InfoItem(label = "Security Patch", value = systemInfo.device.securityPatch ?: "N/A")
            InfoItem(
                label = "Supported ABIs",
                value = systemInfo.device.supportedAbis.joinToString(", ")
                    .ifEmpty { "N/A" },
            )
            InfoItem(
                label = "Supported 32-bit ABIs",
                value = systemInfo.device.supported32BitAbis.joinToString(", ")
                    .ifEmpty { "N/A" },
            )
            InfoItem(
                label = "Supported 64-bit ABIs",
                value = systemInfo.device.supported64BitAbis.joinToString(", ")
                    .ifEmpty { "N/A" },
            )
            InfoItem(label = "Build Fingerprint", value = systemInfo.device.buildFingerprint)

            Spacer(modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}
