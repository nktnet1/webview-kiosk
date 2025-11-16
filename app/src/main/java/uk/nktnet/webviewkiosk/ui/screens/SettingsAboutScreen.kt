package uk.nktnet.webviewkiosk.ui.screens

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import uk.nktnet.webviewkiosk.utils.openAppDetailsSettings

@Composable
fun InfoItem(label: String, value: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable {
                scope.launch {
                    clipboard.setClipEntry(
                        ClipData.newPlainText(label, value).toClipEntry()
                    )
                }
            }
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
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        SettingLabel(navController = navController, label = "About")

        SettingDivider()

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
            value = "${systemInfo.app.versionCode} (${systemInfo.app.versionName})"
        )
        InfoItem(label = "Min SDK", value = systemInfo.app.minSdk.toString())
        InfoItem(label = "Target SDK", value = systemInfo.app.targetSdk.toString())
        InfoItem(label = "Debug Build", value = systemInfo.app.isDebug.toString())
        InfoItem(
            label = "Supported ABIs",
            value = systemInfo.app.supportedAbis.joinToString(", ")
        )
        InfoItem(label = "Installer", value = systemInfo.app.installer ?: "N/A")
        InfoItem(label = "Device Owner", value = systemInfo.app.isDeviceOwner.toString())
        InfoItem(
            label = "Lock Task Permitted",
            value = systemInfo.app.isLockTaskPermitted.toString()
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
            value = "${systemInfo.device.androidRelease} (SDK ${systemInfo.device.androidSdk})"
        )
        InfoItem(
            label = "WebView Version",
            value = systemInfo.device.webViewVersion ?: "N/A"
        )
        InfoItem(
            label = "Screen / Display",
            value = "${systemInfo.device.screenWidth} x ${systemInfo.device.screenHeight} px, density: ${systemInfo.device.screenDensity}"
        )
        InfoItem(
            label = "Managed Profile",
            value = systemInfo.device.isManagedProfile?.toString() ?: "N/A"
        )
        InfoItem(label = "Build Fingerprint", value = systemInfo.device.buildFingerprint)

        Spacer(modifier = Modifier.padding(bottom = 8.dp))
    }
}
