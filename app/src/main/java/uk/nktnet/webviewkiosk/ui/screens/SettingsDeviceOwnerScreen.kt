package uk.nktnet.webviewkiosk.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rosan.dhizuku.shared.DhizukuVariables
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.main.DeviceOwnerManager
import uk.nktnet.webviewkiosk.main.DeviceOwnerMode
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.dhizuku.DhizukuRequestPermissionOnLaunch
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureBlockActivityStartInTaskSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureGlobalActionsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureHomeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureKeyguardSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureNotificationsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureOverviewSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureSystemInfoSetting
import uk.nktnet.webviewkiosk.utils.normaliseInfoText
import uk.nktnet.webviewkiosk.utils.setupLockTaskPackage

fun openPackage(context: Context, packageName: String, showToast: (msg: String) -> Unit) {
    try {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            showToast("App not installed")
        }
    } catch (e: Exception) {
        showToast("Error: $e")
    }
}

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
    var isLockedTaskPermitted by remember {
        mutableStateOf(dpm.isLockTaskPermitted(context.packageName))
    }

    val deviceOwnerStatus by DeviceOwnerManager.status.collectAsState()

    var showDeviceOwnerRemovalDialog by remember { mutableStateOf(false) }

    var toastRef: Toast? = null
    val showToast: (String) -> Unit = { msg ->
        toastRef?.cancel()
        toastRef = Toast.makeText(
            context, msg, Toast.LENGTH_SHORT
        ).apply { show() }
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
                TextButton(
                    onClick = { showDeviceOwnerRemovalDialog = false }
                ) {
                    Text("No")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeContent)
            .padding(horizontal = 16.dp)
    ) {
        SettingLabel(navController = navController, label = "Device Owner")
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

            if (deviceOwnerStatus.mode == DeviceOwnerMode.DeviceOwner) {
                Button(
                    enabled = isDeviceOwner,
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

            if (hasOwnerPermission && !isLockedTaskPermitted) {
                Button(
                    onClick = {
                        setupLockTaskPackage(context)
                        isLockedTaskPermitted = dpm.isLockTaskPermitted(context.packageName)
                        showToast("Added ${Constants.APP_NAME} to lock task packages.")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                ) {
                    Text("Set Lock Task Permitted")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Dhizuku",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            DhizukuRequestPermissionOnLaunch()

            Spacer(modifier = Modifier.height(8.dp))

            if (deviceOwnerStatus.mode == DeviceOwnerMode.Dhizuku) {
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
                            showToast,
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
}
