package uk.nktnet.webviewkiosk.ui.screens

import android.os.Build
import android.util.Log
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rosan.dhizuku.shared.DhizukuVariables
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.data.DeviceOwnerMode
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.common.SystemSafeAlertDialog as AlertDialog
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.dialog.DeviceAdminReceiverListDialog
import uk.nktnet.webviewkiosk.ui.components.setting.dialog.LockTaskPackagesDialog
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.dhizuku.DhizukuRequestPermissionOnLaunchSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureBlockActivityStartInTaskSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureGlobalActionsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureHomeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureKeyguardSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureNotificationsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureOverviewSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureSystemInfoSetting
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes
import uk.nktnet.webviewkiosk.utils.normaliseInfoText
import uk.nktnet.webviewkiosk.utils.openPackage
import uk.nktnet.webviewkiosk.utils.setupLockTaskPackage

@Composable
fun SettingsDeviceOwnerScreen(navController: NavController) {
    val context = LocalContext.current

    var capabilities by remember {
        mutableStateOf(DeviceOwnerManager.getCapabilities(context))
    }
    var hasOwnerPermission by remember {
        mutableStateOf(DeviceOwnerManager.hasOwnerPermission(context))
    }

    val deviceOwnerStatus by DeviceOwnerManager.status.collectAsState()

    var showDeviceOwnerRemovalDialog by remember { mutableStateOf(false) }
    var showAdminReceiverListDialog by remember { mutableStateOf(false) }
    var showLockTaskPackagesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DeviceOwnerManager.initWithDhizukuRetry(context)
        while (true) {
            DeviceOwnerManager.init(context)
            hasOwnerPermission = DeviceOwnerManager.hasOwnerPermission(context)
            capabilities = DeviceOwnerManager.getCapabilities(context)
            delay(1000.milliseconds)
        }
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 4.dp)
                .padding(horizontal = 16.dp)
    ) {
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_device_owner_title),
        )
        SettingDivider()

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            if (!hasOwnerPermission) {
                Text(
                    text =
                        stringResource(
                            R.string.ui_device_owner_not_configured,
                            stringResource(R.string.app_name),
                        ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text =
                    stringResource(
                        if (deviceOwnerStatus.mode == DeviceOwnerMode.Dhizuku) {
                            R.string.ui_shared_owner_status
                        } else {
                            R.string.ui_owner_status
                        },
                        stringResource(deviceOwnerStatus.owner.displayLabelRes),
                        stringResource(deviceOwnerStatus.userType.displayLabelRes),
                    ),
                modifier = Modifier.padding(vertical = 6.dp),
            )
            if (!capabilities.lockTaskPackages) {
                Text(
                    stringResource(R.string.ui_lock_task_policy_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (capabilities.removeOwner) {
                Button(
                    onClick = { showDeviceOwnerRemovalDialog = true },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                MaterialTheme.colorScheme.errorContainer,
                            contentColor =
                                MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                ) {
                    Text(stringResource(R.string.ui_deactivate_device_owner))
                }
            }
            if (capabilities.transferOwnership) {
                Button(
                    onClick = { showAdminReceiverListDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                ) {
                    Text(stringResource(R.string.ui_transfer_ownership))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.ui_lock_task_features),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            HorizontalDivider(
                Modifier,
                DividerDefaults.Thickness,
                DividerDefaults.color,
            )

            LockTaskFeatureHomeSetting(enabled = capabilities.lockTaskFeatures)
            LockTaskFeatureOverviewSetting(enabled = capabilities.lockTaskFeatures)
            LockTaskFeatureGlobalActionsSetting(
                enabled = capabilities.lockTaskFeatures
            )
            LockTaskFeatureNotificationsSetting(
                enabled = capabilities.lockTaskFeatures
            )
            LockTaskFeatureSystemInfoSetting(enabled = capabilities.lockTaskFeatures)
            LockTaskFeatureKeyguardSetting(enabled = capabilities.lockTaskFeatures)
            LockTaskFeatureBlockActivityStartInTaskSetting(
                enabled =
                    capabilities.lockTaskFeatures &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                enabled =
                    capabilities.lockTaskPackages &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
                onClick = {
                    showLockTaskPackagesDialog = true
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
            ) {
                Text(stringResource(R.string.ui_manage_lock_task_packages))
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.ui_dhizuku),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            HorizontalDivider(
                Modifier,
                DividerDefaults.Thickness,
                DividerDefaults.color,
            )

            DhizukuRequestPermissionOnLaunchSetting()

            if (deviceOwnerStatus.mode == DeviceOwnerMode.Dhizuku) {
                Spacer(modifier = Modifier.height(8.dp))

                if (!hasOwnerPermission) {
                    Button(
                        onClick = {
                            DeviceOwnerManager.requestDhizukuPermission(
                                context = context,
                                onGranted = {
                                    setupLockTaskPackage(context)
                                    capabilities =
                                        DeviceOwnerManager.getCapabilities(context)
                                    hasOwnerPermission =
                                        DeviceOwnerManager.hasOwnerPermission(
                                            context
                                        )
                                },
                            )
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor =
                                    MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    ) {
                        Text(stringResource(R.string.ui_request_dhizuku_permission))
                    }
                }

                Button(
                    onClick = {
                        openPackage(
                            context,
                            DhizukuVariables.OFFICIAL_PACKAGE_NAME,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                ) {
                    Text(stringResource(R.string.ui_open_dhizuku))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        DeviceAdminReceiverListDialog(
            showDialog = showAdminReceiverListDialog,
            onDismiss = { showAdminReceiverListDialog = false },
        )
    }

    if (showDeviceOwnerRemovalDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceOwnerRemovalDialog = false },
            title = { Text(stringResource(R.string.ui_deactivate_device_owner)) },
            text = {
                Text(
                    normaliseInfoText(
                        stringResource(
                            R.string.ui_confirm_remove_device_owner,
                            stringResource(R.string.app_name),
                        )
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeviceOwnerRemovalDialog = false
                        try {
                            DeviceOwnerManager.removeNativeOwner(context)
                        } catch (e: Exception) {
                            Log.e(
                                Constants.APP_SCHEME,
                                "Failed to remove management owner",
                                e,
                            )
                            ToastManager.show(
                                context,
                                context.getString(
                                    R.string.ui_error_value,
                                    e.message,
                                ),
                            )
                        }
                        hasOwnerPermission =
                            DeviceOwnerManager.hasOwnerPermission(context)
                        capabilities = DeviceOwnerManager.getCapabilities(context)
                    }
                ) {
                    Text(
                        stringResource(R.string.ui_deactivate),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeviceOwnerRemovalDialog = false
                    }
                ) {
                    Text(stringResource(R.string.ui_cancel))
                }
            },
        )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        LockTaskPackagesDialog(showLockTaskPackagesDialog) {
            showLockTaskPackagesDialog = false
        }
    }
}
