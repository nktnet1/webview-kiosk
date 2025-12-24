package com.nktnet.webview_kiosk.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rosan.dhizuku.shared.DhizukuVariables
import kotlinx.coroutines.delay
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.WebviewKioskAdminReceiver
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.data.DeviceOwnerMode
import com.nktnet.webview_kiosk.managers.DeviceOwnerManager
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import com.nktnet.webview_kiosk.ui.components.setting.dialog.DeviceAdminReceiverListDialog
import com.nktnet.webview_kiosk.ui.components.setting.dialog.LockTaskPackagesDialog
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.dhizuku.DhizukuRequestPermissionOnLaunchSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureBlockActivityStartInTaskSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureGlobalActionsSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureHomeSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureKeyguardSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureNotificationsSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureOverviewSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureSystemInfoSetting
import com.nktnet.webview_kiosk.utils.normaliseInfoText
import com.nktnet.webview_kiosk.utils.openPackage
import com.nktnet.webview_kiosk.utils.setupLockTaskPackage

@Composable
fun SettingsDeviceOwnerScreen(navController: NavController) {
    val context = LocalContext.current

    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(
        context.packageName,
        WebviewKioskAdminReceiver::class.java.name
    )

    var isDeviceOwner by remember {
        mutableStateOf(dpm.isDeviceOwnerApp(context.packageName))
    }
    var hasOwnerPermission by remember {
        mutableStateOf(DeviceOwnerManager.hasOwnerPermission(context))
    }

    val deviceOwnerStatus by DeviceOwnerManager.status.collectAsState()

    var showDeviceOwnerRemovalDialog by remember { mutableStateOf(false) }
    var showAdminReceiverListDialog by remember { mutableStateOf(false) }
    var showLockTaskPackagesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isDeviceOwner) {
        DeviceOwnerManager.init(context)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
            hasOwnerPermission = DeviceOwnerManager.hasOwnerPermission(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
    ) {
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_device_owner_title)
        )
        SettingDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            if (!hasOwnerPermission) {
                Text(
                    text = """
                        ${Constants.APP_NAME} is not set as the device owner.
                        The settings below will not take effect.
                    """.trimIndent(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (
                deviceOwnerStatus.mode == DeviceOwnerMode.DeviceOwner
                && isDeviceOwner
            ) {
                Button(
                    onClick = { showDeviceOwnerRemovalDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                ) {
                    Text("Deactivate Device Owner")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Button(
                        onClick = { showAdminReceiverListDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                    ) {
                        Text("Transfer Ownership")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Lock Task Features",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            LockTaskFeatureHomeSetting()
            LockTaskFeatureOverviewSetting()
            LockTaskFeatureGlobalActionsSetting()
            LockTaskFeatureNotificationsSetting()
            LockTaskFeatureSystemInfoSetting()
            LockTaskFeatureKeyguardSetting()
            LockTaskFeatureBlockActivityStartInTaskSetting()

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                enabled = hasOwnerPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
                onClick = {
                    showLockTaskPackagesDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp),
            ) {
                Text("Manage Lock Task Packages")
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Dhizuku",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            DhizukuRequestPermissionOnLaunchSetting()

            if (deviceOwnerStatus.mode == DeviceOwnerMode.Dhizuku) {
                Spacer(modifier = Modifier.height(8.dp))

                if (!hasOwnerPermission) {
                    Button(
                        onClick = {
                            DeviceOwnerManager.requestDhizukuPermission(
                                onGranted = {
                                    setupLockTaskPackage(context)
                                    hasOwnerPermission = DeviceOwnerManager.hasOwnerPermission(context)
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                    ) {
                        Text("Request Dhizuku Permission")
                    }
                }

                Button(
                    onClick = {
                        openPackage(
                            context,
                            DhizukuVariables.OFFICIAL_PACKAGE_NAME,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                ) {
                    Text("Open Dhizuku")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        DeviceAdminReceiverListDialog(
            showDialog = showAdminReceiverListDialog,
            onDismiss = { showAdminReceiverListDialog = false }
        )
    }

    if (showDeviceOwnerRemovalDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceOwnerRemovalDialog = false },
            title = { Text("Deactivate Device Owner") },
            text = {
                Text(
                    normaliseInfoText("""
                        Are you sure you want to unset ${Constants.APP_NAME} as the
                        device owner?

                        This means Lock Task Mode will no longer be available, meaning
                        the kiosk lock feature will utilise Screen Pinning instead.
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
                        DeviceOwnerManager.init(context)
                        isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
                    }
                ) {
                    Text(
                        "Deactivate",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeviceOwnerRemovalDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        LockTaskPackagesDialog(
            showLockTaskPackagesDialog,
        ) {
            showLockTaskPackagesDialog = false
        }
    }
}
