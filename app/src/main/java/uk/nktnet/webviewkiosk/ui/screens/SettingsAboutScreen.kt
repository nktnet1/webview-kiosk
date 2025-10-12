package uk.nktnet.webviewkiosk.ui.screens

import android.content.ClipData
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.navigation.NavController
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.BuildConfig
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
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
    val resources = LocalResources.current
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

    val webViewVersion = remember {
        try {
            WebViewCompat.getCurrentWebViewPackage(context)?.versionName ?: "Unknown"
        } catch (_: Exception) {
            "N/A"
        }
    }

    val screenInfo = remember {
        val metrics = resources.displayMetrics
        "${metrics.widthPixels} x ${metrics.heightPixels} px, density: ${metrics.density}"
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

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            onClick = {
                openAppDetailsSettings(context)
            }
        ) {
            Text(
                text = "Open App Info",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium
            )
        }

        Text(
            text = "App Details",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )
        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

        InfoItem(label = "Name", value = appName)
        InfoItem(label = "Package", value = packageName)
        InfoItem(label = "Version", value = "$versionCode ($versionName)")
        InfoItem(label = "Min SDK", value = BuildConfig.MIN_SDK_VERSION.toString())
        InfoItem(label = "Target SDK", value = targetSdkVersion)
        InfoItem(label = "Debug Build", value = debugFlag)
        InfoItem(label = "Supported ABIs", value = supportedABIs)
        InfoItem(label = "Installer", value = installerPackage)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Device Info",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

        InfoItem(label = "Android Version", value = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        InfoItem(label = "WebView Version", value = webViewVersion)
        InfoItem(label = "Screen / Display", value = screenInfo)
        InfoItem(label = "Build Fingerprint", value = Build.FINGERPRINT)

        Spacer(modifier = Modifier.padding(bottom = 8.dp))
    }
}
