package com.nktnet.webview_kiosk.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.nktnet.webview_kiosk.managers.ToastManager
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.utils.getSystemInfo
import com.nktnet.webview_kiosk.utils.humanReadableSize
import com.nktnet.webview_kiosk.utils.openAppDetailsSettings
import com.nktnet.webview_kiosk.R

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
                    ToastManager.show(
                        context,
                        "Tap and hold to copy value."
                    )
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
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
    ) {
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_about_title)
        )
        SettingDivider()

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { openAppDetailsSettings(context) }
            ) {
                Text(
                    text = stringResource(R.string.about_open_app_info),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.about_section_app),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HorizontalDivider(
                modifier = Modifier,
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )

            InfoItem(
                label = stringResource(R.string.about_app_name),
                value = systemInfo.app.name
            )
            InfoItem(
                label = stringResource(R.string.about_app_package),
                value = systemInfo.app.packageName
            )
            InfoItem(
                label = stringResource(R.string.about_app_version),
                value = "${systemInfo.app.versionCode} " +
                        "(${systemInfo.app.versionName})"
            )
            InfoItem(
                label = stringResource(R.string.about_app_min_sdk),
                value = systemInfo.app.minSdk.toString()
            )
            InfoItem(
                label = stringResource(R.string.about_app_target_sdk),
                value = systemInfo.app.targetSdk.toString()
            )
            InfoItem(
                label = stringResource(R.string.about_app_debug_build),
                value = systemInfo.app.isDebug.toString()
            )
            InfoItem(
                label = stringResource(R.string.about_app_installer),
                value = systemInfo.app.installer ?: "N/A"
            )
            InfoItem(
                label = stringResource(R.string.about_app_device_owner),
                value = systemInfo.app.isDeviceOwner.toString()
            )
            InfoItem(
                label = stringResource(R.string.about_app_lock_task_permitted),
                value = systemInfo.app.isLockTaskPermitted.toString()
            )
            InfoItem(
                label = stringResource(R.string.about_app_dhizuku_permission_granted),
                value = systemInfo.app.dhizukuPermissionGranted.toString()
            )
            InfoItem(
                label = stringResource(R.string.about_app_instance_id),
                value = systemInfo.app.instanceId
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.about_section_device),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HorizontalDivider(
                modifier = Modifier,
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )

            InfoItem(
                label = stringResource(R.string.about_device_android_version),
                value = "${systemInfo.device.releaseVersion} " +
                        "(SDK ${systemInfo.device.sdkVersion})"
            )
            InfoItem(
                label = stringResource(R.string.about_device_webview_version),
                value = systemInfo.device.webViewVersion ?: "N/A"
            )
            InfoItem(
                label = stringResource(R.string.about_device_screen_display),
                value = "${systemInfo.device.screenWidth} x ${systemInfo.device.screenHeight} px, " +
                        "density: ${systemInfo.device.screenDensity}"
            )
            InfoItem(
                label = stringResource(R.string.about_device_managed_profile),
                value = systemInfo.device.isManagedProfile?.toString() ?: "N/A"
            )
            InfoItem(
                label = stringResource(R.string.about_device_time_zone),
                value = systemInfo.device.timeZone
            )
            InfoItem(
                label = stringResource(R.string.about_device_locale),
                value = systemInfo.device.locale
            )
            InfoItem(
                label = stringResource(R.string.about_device_total_ram),
                value = humanReadableSize(context, systemInfo.device.totalMemory)
            )
            InfoItem(
                label = stringResource(R.string.about_device_total_storage),
                value = humanReadableSize(context, systemInfo.device.totalStorage)
            )
            InfoItem(
                label = stringResource(R.string.about_device_model),
                value = systemInfo.device.model
            )
            InfoItem(
                label = stringResource(R.string.about_device_manufacturer),
                value = systemInfo.device.manufacturer
            )
            InfoItem(
                label = stringResource(R.string.about_device_brand),
                value = systemInfo.device.brand
            )
            InfoItem(
                label = stringResource(R.string.about_device_device),
                value = systemInfo.device.device
            )
            InfoItem(
                label = stringResource(R.string.about_device_product),
                value = systemInfo.device.product
            )
            InfoItem(
                label = stringResource(R.string.about_device_hardware),
                value = systemInfo.device.hardware
            )
            InfoItem(
                label = stringResource(R.string.about_device_board),
                value = systemInfo.device.board
            )
            InfoItem(
                label = stringResource(R.string.about_device_bootloader),
                value = systemInfo.device.bootloader
            )
            InfoItem(
                label = stringResource(R.string.about_device_security_patch),
                value = systemInfo.device.securityPatch ?: "N/A"
            )
            InfoItem(
                label = stringResource(R.string.about_device_supported_abis),
                value = systemInfo.device.supportedAbis.joinToString(", ")
                    .ifEmpty { "N/A" }
            )
            InfoItem(
                label = stringResource(R.string.about_device_supported_32bit_abis),
                value = systemInfo.device.supported32BitAbis.joinToString(", ")
                    .ifEmpty { "N/A" }
            )
            InfoItem(
                label = stringResource(R.string.about_device_supported_64bit_abis),
                value = systemInfo.device.supported64BitAbis.joinToString(", ")
                    .ifEmpty { "N/A" }
            )
            InfoItem(
                label = stringResource(R.string.about_device_build_fingerprint),
                value = systemInfo.device.buildFingerprint
            )

            Spacer(modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}
