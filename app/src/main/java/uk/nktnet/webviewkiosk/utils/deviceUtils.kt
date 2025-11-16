package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import android.app.admin.DevicePolicyManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import androidx.core.content.pm.PackageInfoCompat
import androidx.webkit.WebViewCompat
import kotlinx.serialization.Serializable
import uk.nktnet.webviewkiosk.BuildConfig
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.DeviceRotationOption
import uk.nktnet.webviewkiosk.states.KeepScreenOnStateSingleton
import uk.nktnet.webviewkiosk.states.ThemeStateSingleton

fun setWindowBrightness(context: Context, value: Int) {
    val activity = context as? Activity ?: return
    val window = activity.window
    val layoutParams: WindowManager.LayoutParams = window.attributes
    if (value < 0) {
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    } else {
        layoutParams.screenBrightness = (value / 100f).coerceIn(0f, 1f)
    }
    window.attributes = layoutParams
}

fun setDeviceRotation(context: Context, rotation: DeviceRotationOption) {
    val activity = context as? AppCompatActivity ?: return
    activity.requestedOrientation = when (rotation) {
        DeviceRotationOption.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        DeviceRotationOption.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        DeviceRotationOption.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        DeviceRotationOption.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        DeviceRotationOption.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    }
}

fun updateDeviceSettings(context: Context) {
    val userSettings = UserSettings(context)
    KeepScreenOnStateSingleton.setKeepScreenOn(userSettings.keepScreenOn)
    ThemeStateSingleton.setTheme(userSettings.theme)
    setDeviceRotation(context, userSettings.rotation)
    setWindowBrightness(context, userSettings.brightness)
}

@Serializable
data class AppInfo(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val isDebug: Boolean,
    val supportedAbis: List<String>,
    val installer: String?,
    val isDeviceOwner: Boolean,
    val isLockTaskPermitted: Boolean,
    val instanceId: String
)

@Serializable
data class DeviceInfo(
    val androidRelease: String,
    val androidSdk: Int,
    val webViewVersion: String?,
    val screenWidth: Int,
    val screenHeight: Int,
    val screenDensity: Float,
    val isManagedProfile: Boolean?,
    val buildFingerprint: String
)

@Serializable
data class SystemInfo(
    val app: AppInfo,
    val device: DeviceInfo
)

fun getAppInfo(context: Context): AppInfo {
    val pm = context.packageManager
    val packageName = context.packageName
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val systemSettings = SystemSettings(context)

    val appName = context.applicationInfo.loadLabel(pm).toString()

    val versionName = try {
        pm.getPackageInfo(packageName, 0).versionName ?: "N/A"
    } catch (_: PackageManager.NameNotFoundException) {
        "N/A"
    }

    val versionCode = try {
        val info = pm.getPackageInfo(packageName, 0)
        PackageInfoCompat.getLongVersionCode(info)
    } catch (_: PackageManager.NameNotFoundException) {
        -1L
    }

    val targetSdk = try {
        pm.getPackageInfo(packageName, 0).applicationInfo?.targetSdkVersion ?: -1
    } catch (_: PackageManager.NameNotFoundException) {
        -1
    }

    val isDebug = try {
        val info = pm.getApplicationInfo(packageName, 0)
        (info.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    val supportedAbis = Build.SUPPORTED_ABIS.toList()

    val installer = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(packageName)
        }
    } catch (_: Exception) {
        null
    }

    val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
    val isLockTaskPermitted = dpm.isLockTaskPermitted(packageName)

    return AppInfo(
        name = appName,
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        minSdk = BuildConfig.MIN_SDK_VERSION,
        targetSdk = targetSdk,
        isDebug = isDebug,
        supportedAbis = supportedAbis,
        installer = installer,
        isDeviceOwner = isDeviceOwner,
        isLockTaskPermitted = isLockTaskPermitted,
        instanceId = systemSettings.appInstanceId
    )
}

fun getDeviceInfo(context: Context): DeviceInfo {
    val um = context.getSystemService(Context.USER_SERVICE) as UserManager

    val webViewVersion = try {
        WebViewCompat.getCurrentWebViewPackage(context)?.versionName
    } catch (_: Exception) {
        null
    }

    val metrics = context.resources.displayMetrics
    val screenWidth = metrics.widthPixels
    val screenHeight = metrics.heightPixels
    val screenDensity = metrics.density

    val isManagedProfile: Boolean? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        um.isManagedProfile
    } else {
        null
    }

    return DeviceInfo(
        androidRelease = Build.VERSION.RELEASE,
        androidSdk = Build.VERSION.SDK_INT,
        webViewVersion = webViewVersion,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        screenDensity = screenDensity,
        isManagedProfile = isManagedProfile,
        buildFingerprint = Build.FINGERPRINT
    )
}

fun getSystemInfo(context: Context): SystemInfo {
    return SystemInfo(
        app = getAppInfo(context),
        device = getDeviceInfo(context)
    )
}
