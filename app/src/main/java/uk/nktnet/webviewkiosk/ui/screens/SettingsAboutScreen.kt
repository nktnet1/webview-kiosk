package uk.nktnet.webviewkiosk.ui.screens

import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.bringSearchSettingIntoView
import uk.nktnet.webviewkiosk.ui.resources.managementModeLabelRes
import uk.nktnet.webviewkiosk.ui.resources.managementOwnerLabelRes
import uk.nktnet.webviewkiosk.ui.resources.managementUserTypeLabelRes
import uk.nktnet.webviewkiosk.utils.getSystemInfo
import uk.nktnet.webviewkiosk.utils.humanReadableSize
import uk.nktnet.webviewkiosk.utils.openAppDetailsSettings

@Composable
fun InfoItem(@StringRes labelRes: Int, value: String) {
    val label = stringResource(labelRes)
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .bringSearchSettingIntoView(
                    settingsItemTargetId(
                        context,
                        SettingsSearchResultKind.INFORMATION,
                        labelRes,
                    )
                )
                .padding(vertical = 12.dp)
                .combinedClickable(
                    onClick = {
                        ToastManager.show(
                            context,
                            context.getString(
                                R.string.ui_tap_and_hold_to_copy_value
                            ),
                        )
                    },
                    onLongClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipData.newPlainText(label, value).toClipEntry()
                            )
                        }
                    },
                )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SettingsAboutScreen(navController: NavController) {
    val context = LocalContext.current
    val systemInfo = remember { getSystemInfo(context) }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 4.dp)
                .padding(horizontal = 16.dp)
    ) {
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_about_title),
        )
        SettingDivider()

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxSize()
        ) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { openAppDetailsSettings(context) },
            ) {
                Text(
                    text = stringResource(R.string.about_open_app_info),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AboutSectionHeading(R.string.about_section_app)

            InfoItem(
                labelRes = R.string.about_app_name,
                value = systemInfo.app.name,
            )
            InfoItem(
                labelRes = R.string.about_app_package,
                value = systemInfo.app.packageName,
            )
            InfoItem(
                labelRes = R.string.about_app_version,
                value =
                    "${systemInfo.app.versionCode} " +
                        "(${systemInfo.app.versionName})",
            )
            InfoItem(
                labelRes = R.string.about_app_min_sdk,
                value = systemInfo.app.minSdk.toString(),
            )
            InfoItem(
                labelRes = R.string.about_app_target_sdk,
                value = systemInfo.app.targetSdk.toString(),
            )
            InfoItem(
                labelRes = R.string.about_app_debug_build,
                value = systemInfo.app.isDebug.toString(),
            )
            InfoItem(
                labelRes = R.string.about_app_installer,
                value = systemInfo.app.installer ?: stringResource(R.string.ui_n_a),
            )
            InfoItem(
                labelRes = R.string.about_app_instance_id,
                value = systemInfo.app.instanceId,
            )
            InfoItem(
                labelRes = R.string.about_source_code_url,
                value = systemInfo.app.sourceCodeUrl,
            )

            Spacer(modifier = Modifier.height(20.dp))

            AboutSectionHeading(R.string.about_section_management)

            InfoItem(
                labelRes = R.string.about_app_device_owner,
                value = systemInfo.app.isDeviceOwner.toString(),
            )
            InfoItem(
                labelRes = R.string.about_app_profile_owner,
                value = systemInfo.app.isProfileOwner.toString(),
            )
            InfoItem(
                labelRes = R.string.about_app_management_mode,
                value =
                    stringResource(
                        managementModeLabelRes(systemInfo.app.managementMode)
                    ),
            )
            InfoItem(
                labelRes = R.string.about_app_management_owner,
                value =
                    stringResource(
                        managementOwnerLabelRes(systemInfo.app.managementOwner)
                    ),
            )
            InfoItem(
                labelRes = R.string.about_app_management_user_type,
                value =
                    stringResource(
                        managementUserTypeLabelRes(systemInfo.app.managementUserType)
                    ),
            )
            InfoItem(
                labelRes = R.string.about_device_managed_profile,
                value =
                    systemInfo.device.isManagedProfile?.toString()
                        ?: stringResource(R.string.ui_n_a),
            )
            InfoItem(
                labelRes = R.string.about_app_lock_task_permitted,
                value = systemInfo.app.isLockTaskPermitted.toString(),
            )
            InfoItem(
                labelRes = R.string.about_app_dhizuku_permission_granted,
                value = systemInfo.app.dhizukuPermissionGranted.toString(),
            )
            InfoItem(
                labelRes = R.string.about_app_manage_lock_task_packages,
                value = systemInfo.app.canManageLockTaskPackages.toString(),
            )
            InfoItem(
                labelRes = R.string.about_app_manage_lock_task_features,
                value = systemInfo.app.canManageLockTaskFeatures.toString(),
            )
            InfoItem(
                labelRes = R.string.about_app_lock_device,
                value = systemInfo.app.canLockDevice.toString(),
            )

            Spacer(modifier = Modifier.height(20.dp))

            AboutSectionHeading(R.string.about_section_device)

            InfoItem(
                labelRes = R.string.about_device_android_version,
                value =
                    stringResource(
                        R.string.ui_value_sdk_value,
                        systemInfo.device.releaseVersion,
                        systemInfo.device.sdkVersion,
                    ),
            )
            InfoItem(
                labelRes = R.string.about_device_webview_version,
                value =
                    systemInfo.device.webViewVersion
                        ?: stringResource(R.string.ui_n_a),
            )
            InfoItem(
                labelRes = R.string.about_device_screen_display,
                value =
                    stringResource(
                        R.string.ui_value_x_value_px_density_value,
                        systemInfo.device.screenWidth,
                        systemInfo.device.screenHeight,
                        systemInfo.device.screenDensity,
                    ),
            )
            InfoItem(
                labelRes = R.string.about_device_time_zone,
                value = systemInfo.device.timeZone,
            )
            InfoItem(
                labelRes = R.string.about_device_locale,
                value = systemInfo.device.locale,
            )
            InfoItem(
                labelRes = R.string.about_device_total_ram,
                value = humanReadableSize(context, systemInfo.device.totalMemory),
            )
            InfoItem(
                labelRes = R.string.about_device_total_storage,
                value = humanReadableSize(context, systemInfo.device.totalStorage),
            )
            InfoItem(
                labelRes = R.string.about_device_model,
                value = systemInfo.device.model,
            )
            InfoItem(
                labelRes = R.string.about_device_manufacturer,
                value = systemInfo.device.manufacturer,
            )
            InfoItem(
                labelRes = R.string.about_device_brand,
                value = systemInfo.device.brand,
            )
            InfoItem(
                labelRes = R.string.about_device_device,
                value = systemInfo.device.device,
            )
            InfoItem(
                labelRes = R.string.about_device_product,
                value = systemInfo.device.product,
            )
            InfoItem(
                labelRes = R.string.about_device_hardware,
                value = systemInfo.device.hardware,
            )
            InfoItem(
                labelRes = R.string.about_device_board,
                value = systemInfo.device.board,
            )
            InfoItem(
                labelRes = R.string.about_device_bootloader,
                value = systemInfo.device.bootloader,
            )
            InfoItem(
                labelRes = R.string.about_device_security_patch,
                value =
                    systemInfo.device.securityPatch
                        ?: stringResource(R.string.ui_n_a),
            )
            InfoItem(
                labelRes = R.string.about_device_supported_abis,
                value =
                    systemInfo.device.supportedAbis.joinToString(", ").ifEmpty {
                        "N/A"
                    },
            )
            InfoItem(
                labelRes = R.string.about_device_supported_32bit_abis,
                value =
                    systemInfo.device.supported32BitAbis.joinToString(", ").ifEmpty {
                        "N/A"
                    },
            )
            InfoItem(
                labelRes = R.string.about_device_supported_64bit_abis,
                value =
                    systemInfo.device.supported64BitAbis.joinToString(", ").ifEmpty {
                        "N/A"
                    },
            )
            InfoItem(
                labelRes = R.string.about_device_build_fingerprint,
                value = systemInfo.device.buildFingerprint,
            )

            Spacer(modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun AboutSectionHeading(@StringRes label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
    )
    HorizontalDivider(
        modifier = Modifier,
        thickness = DividerDefaults.Thickness,
        color = DividerDefaults.color,
    )
}
