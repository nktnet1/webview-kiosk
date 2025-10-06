package uk.nktnet.webviewkiosk.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel

@Composable
fun InfoItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
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
    val packageManager = context.packageManager
    val packageName = context.packageName

    val appName = remember {
        context.applicationInfo.loadLabel(packageManager).toString()
    }

    val versionName = remember {
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "N/A"
        } catch (_: PackageManager.NameNotFoundException) {
            "N/A"
        }
    }

    val versionCode = remember {
        try {
            val info = packageManager.getPackageInfo(packageName, 0)
            PackageInfoCompat.getLongVersionCode(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            "N/A"
        }
    }

    val targetSdkVersion = remember {
        try {
            packageManager.getPackageInfo(packageName, 0).applicationInfo?.targetSdkVersion.toString()
        } catch (_: PackageManager.NameNotFoundException) {
            "N/A"
        }
    }

    val debugFlag = remember {
        try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            if ((info.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) "Yes" else "No"
        } catch (_: PackageManager.NameNotFoundException) {
            "N/A"
        }
    }

    val supportedABIs = remember {
        Build.SUPPORTED_ABIS.joinToString(", ")
    }

    val installerPackage = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            } ?: "Unknown"
        } catch (_: Exception) {
            "N/A"
        }
    }

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

        InfoItem(label = "App", value = appName)
        InfoItem(label = "Package", value = packageName)
        InfoItem(label = "Version", value = "$versionCode ($versionName)")
        InfoItem(label = "Target SDK", value = targetSdkVersion)
        InfoItem(label = "Debug Build", value = debugFlag)
        InfoItem(label = "Supported ABIs", value = supportedABIs)
        InfoItem(label = "Installer", value = installerPackage)
    }
}
