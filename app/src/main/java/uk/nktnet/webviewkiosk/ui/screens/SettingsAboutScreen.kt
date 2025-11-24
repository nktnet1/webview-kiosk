package uk.nktnet.webviewkiosk.ui.screens

import android.content.ClipData
import android.widget.Toast
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
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.utils.getSystemInfo
import uk.nktnet.webviewkiosk.utils.humanReadableSize
import uk.nktnet.webviewkiosk.utils.openAppDetailsSettings

@Composable
fun InfoItem(label: String, value: String, showToast: (msg: String) -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .combinedClickable(
                onClick = {
                    showToast("Click and hold to copy value.")
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
    var toastRef: Toast? = null
    val showToast: (String) -> Unit = { msg ->
        toastRef?.cancel()
        toastRef = Toast.makeText(
            context, msg, Toast.LENGTH_SHORT
        ).apply { show() }
    }

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

            InfoItem(label = "Name", value = systemInfo.app.name, showToast)
            InfoItem(label = "Package", value = systemInfo.app.packageName, showToast)
            InfoItem(
                label = "Version",
                value = "${systemInfo.app.versionCode} (${systemInfo.app.versionName})",
                showToast,
            )
            InfoItem(label = "Min SDK", value = systemInfo.app.minSdk.toString(), showToast)
            InfoItem(label = "Target SDK", value = systemInfo.app.targetSdk.toString(), showToast)
            InfoItem(label = "Debug Build", value = systemInfo.app.isDebug.toString(), showToast)
            InfoItem(label = "Installer", value = systemInfo.app.installer ?: "N/A", showToast)
            InfoItem(label = "Device Owner", value = systemInfo.app.isDeviceOwner.toString(), showToast)
            InfoItem(
                label = "Lock Task Permitted",
                value = systemInfo.app.isLockTaskPermitted.toString(),
                showToast,
            )
            InfoItem(label = "Instance ID", value = systemInfo.app.instanceId, showToast)

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
                showToast,
            )
            InfoItem(
                label = "WebView Version",
                value = systemInfo.device.webViewVersion ?: "N/A",
                showToast,
            )
            InfoItem(
                label = "Screen / Display",
                value = "${systemInfo.device.screenWidth} x ${systemInfo.device.screenHeight} px, density: ${systemInfo.device.screenDensity}",
                showToast,
            )
            InfoItem(
                label = "Managed Profile",
                value = systemInfo.device.isManagedProfile?.toString() ?: "N/A",
                showToast
            )
            InfoItem(label = "Time Zone", value = systemInfo.device.timeZone, showToast)
            InfoItem(label = "Locale", value = systemInfo.device.locale, showToast)
            InfoItem(label = "Total RAM", value = humanReadableSize(context, systemInfo.device.totalMemory), showToast)
            InfoItem(label = "Total Storage", value = humanReadableSize(context, systemInfo.device.totalStorage), showToast)
            InfoItem(label = "Model", value = systemInfo.device.model, showToast)
            InfoItem(label = "Manufacturer", value = systemInfo.device.manufacturer, showToast)
            InfoItem(label = "Brand", value = systemInfo.device.brand, showToast)
            InfoItem(label = "Device", value = systemInfo.device.device, showToast)
            InfoItem(label = "Product", value = systemInfo.device.product, showToast)
            InfoItem(label = "Hardware", value = systemInfo.device.hardware, showToast)
            InfoItem(label = "Board", value = systemInfo.device.board, showToast)
            InfoItem(label = "Bootloader", value = systemInfo.device.bootloader, showToast)
            InfoItem(label = "Security Patch", value = systemInfo.device.securityPatch ?: "N/A", showToast)
            InfoItem(
                label = "Supported ABIs",
                value = systemInfo.device.supportedAbis.joinToString(", ")
                    .ifEmpty { "N/A" },
                showToast,
            )
            InfoItem(
                label = "Supported 32-bit ABIs",
                value = systemInfo.device.supported32BitAbis.joinToString(", ")
                    .ifEmpty { "N/A" },
                showToast,
            )
            InfoItem(
                label = "Supported 64-bit ABIs",
                value = systemInfo.device.supported64BitAbis.joinToString(", ")
                    .ifEmpty { "N/A" },
                showToast,
            )
            InfoItem(label = "Build Fingerprint", value = systemInfo.device.buildFingerprint, showToast)

            Spacer(modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}
